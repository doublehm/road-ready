import { useState, useEffect, useRef } from 'react';
import * as Location from 'expo-location';

/**
 * Calculate distance between two coordinates using Haversine formula
 *
 * @param {Object} coord1 - {latitude, longitude}
 * @param {Object} coord2 - {latitude, longitude}
 * @returns {number} Distance in kilometers
 */
function calculateDistance(coord1, coord2) {
  const R = 6371; // Earth's radius in km
  const dLat = (coord2.latitude - coord1.latitude) * (Math.PI / 180);
  const dLon = (coord2.longitude - coord1.longitude) * (Math.PI / 180);

  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(coord1.latitude * (Math.PI / 180)) *
      Math.cos(coord2.latitude * (Math.PI / 180)) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);

  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

/**
 * Custom hook for GPS tracking with speed and distance calculation
 *
 * @returns {Object} GPS data and control functions
 */
export default function useGPSTracking() {
  const [isTracking, setIsTracking] = useState(false);
  const [location, setLocation] = useState(null);
  const [speed, setSpeed] = useState(0); // km/h
  const [routeCoordinates, setRouteCoordinates] = useState([]);
  const [distance, setDistance] = useState(0); // km
  const [speedData, setSpeedData] = useState([]); // Array of {timestamp, speed, latitude, longitude}

  const subscriptionRef = useRef(null);
  const routeRef = useRef([]);
  const distanceRef = useRef(0);
  const speedDataRef = useRef([]);

  useEffect(() => {
    // Request location permissions on mount
    (async () => {
      try {
        const { status } = await Location.requestForegroundPermissionsAsync();
        if (status !== 'granted') {
          console.error('Location permission not granted');
          return;
        }

        const currentLocation = await Location.getCurrentPositionAsync({
          accuracy: Location.Accuracy.High,
        });
        setLocation(currentLocation.coords);
      } catch (error) {
        console.error('Error requesting location permission:', error);
      }
    })();

    return () => {
      // Cleanup on unmount
      if (subscriptionRef.current) {
        subscriptionRef.current.remove();
        subscriptionRef.current = null;
      }
    };
  }, []);

  const startTracking = async () => {
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        console.error('Location permission not granted');
        return false;
      }

      // Clear previous data
      routeRef.current = [];
      distanceRef.current = 0;
      speedDataRef.current = [];
      setRouteCoordinates([]);
      setDistance(0);
      setSpeedData([]);

      // Start watching position
      subscriptionRef.current = await Location.watchPositionAsync(
        {
          accuracy: Location.Accuracy.High,
          timeInterval: 1000, // Update every 1 second
          distanceInterval: 5, // Update every 5 meters
        },
        (loc) => {
          const timestamp = Date.now();
          const { latitude, longitude, speed: gpsSpeed } = loc.coords;

          // Update current location
          setLocation(loc.coords);

          // Calculate speed (m/s to km/h)
          const speedKmh = gpsSpeed ? gpsSpeed * 3.6 : 0;
          setSpeed(speedKmh);

          // Add to route
          const newCoordinate = { latitude, longitude };
          routeRef.current.push(newCoordinate);
          setRouteCoordinates([...routeRef.current]);

          // Calculate distance if we have a previous point
          if (routeRef.current.length > 1) {
            const prevCoord = routeRef.current[routeRef.current.length - 2];
            const segmentDistance = calculateDistance(prevCoord, newCoordinate);
            distanceRef.current += segmentDistance;
            setDistance(distanceRef.current);
          }

          // Store speed data point
          const speedPoint = {
            timestamp,
            speed: speedKmh,
            latitude,
            longitude,
            heading: loc.coords.heading || 0,
          };
          speedDataRef.current.push(speedPoint);

          // Update speed data periodically (every 5 samples)
          if (speedDataRef.current.length % 5 === 0) {
            setSpeedData([...speedDataRef.current]);
          }
        }
      );

      setIsTracking(true);
      return true;
    } catch (error) {
      console.error('Error starting GPS tracking:', error);
      return false;
    }
  };

  const stopTracking = () => {
    if (subscriptionRef.current) {
      subscriptionRef.current.remove();
      subscriptionRef.current = null;
    }

    setIsTracking(false);

    // Final data update
    setRouteCoordinates([...routeRef.current]);
    setDistance(distanceRef.current);
    setSpeedData([...speedDataRef.current]);

    return {
      routeCoordinates: routeRef.current,
      distance: distanceRef.current,
      speedData: speedDataRef.current,
    };
  };

  const clearData = () => {
    routeRef.current = [];
    distanceRef.current = 0;
    speedDataRef.current = [];
    setRouteCoordinates([]);
    setDistance(0);
    setSpeedData([]);
    setSpeed(0);
  };

  return {
    isTracking,
    location,
    speed,
    routeCoordinates,
    distance,
    speedData,
    startTracking,
    stopTracking,
    clearData,
  };
}
