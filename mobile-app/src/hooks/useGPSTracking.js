import { useState, useEffect, useRef } from 'react';
import * as Location from 'expo-location';
import AsyncStorage from '@react-native-async-storage/async-storage';

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
// Minimum ms between React state updates for GPS-derived values.
// The GPS subscription still fires at full rate into refs for accurate distance
// calculation; this only throttles re-renders on the active ride screen.
const GPS_STATE_THROTTLE_MS = 2000;

// Below this threshold (km/h), GPS speed is treated as 0.
// GPS chipsets report phantom speeds of 3-15 km/h from signal drift while
// stationary; a 3 km/h (~0.8 m/s) cutoff filters drift without hiding
// genuine creeping motion (e.g. parking lot manoeuvres start around 5 km/h).
const SPEED_DEAD_ZONE_KMH = 3;

// If speed changes by more than this amount (km/h), bypass the render throttle
// so the UI reacts immediately to hard braking or stops.
const SPEED_CHANGE_THRESHOLD_KMH = 5;

// If no GPS fix arrives within this interval (ms), assume the vehicle is
// stationary and reset speed to 0. This covers the case where
// `distanceInterval` prevents callbacks while the device is not moving.
const STALE_SPEED_TIMEOUT_MS = 3000;

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
  const lastGpsStateUpdateRef = useRef(0);
  const lastReportedSpeedRef = useRef(0);
  const staleSpeedTimerRef = useRef(null);
  const lastGpsFixRef = useRef(0);

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
      if (staleSpeedTimerRef.current) {
        clearInterval(staleSpeedTimerRef.current);
        staleSpeedTimerRef.current = null;
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
          timeInterval: 1000,    // Update every 1 second
          distanceInterval: 10,  // Update every 10 meters (was 5 — halves update rate on highways)
        },
        (loc) => {
          const timestamp = Date.now();
          const { latitude, longitude, speed: gpsSpeed } = loc.coords;

          // Dead-zone filter: GPS chipsets report phantom speeds from signal
          // drift while stationary.  Treat anything below the threshold as 0.
          const rawKmh = (gpsSpeed && gpsSpeed > 0) ? gpsSpeed * 3.6 : 0;
          const speedKmh = rawKmh < SPEED_DEAD_ZONE_KMH ? 0 : rawKmh;

          // Record when we last received a GPS fix
          lastGpsFixRef.current = timestamp;

          // Always update refs (used for accurate distance and by the active screen)
          const newCoordinate = { latitude, longitude };
          routeRef.current.push(newCoordinate);

          // Calculate distance on every fix regardless of state throttle
          if (routeRef.current.length > 1) {
            const prevCoord = routeRef.current[routeRef.current.length - 2];
            const segmentDistance = calculateDistance(prevCoord, newCoordinate);
            distanceRef.current += segmentDistance;
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

          // Throttle state updates to avoid excessive re-renders.
          // Bypass the throttle when speed changed significantly (e.g. hard
          // braking or coming to a stop) so the UI reacts immediately.
          const now = Date.now();
          const speedDelta = Math.abs(speedKmh - lastReportedSpeedRef.current);
          const withinThrottle = now - lastGpsStateUpdateRef.current < GPS_STATE_THROTTLE_MS;
          if (withinThrottle && speedDelta < SPEED_CHANGE_THRESHOLD_KMH) return;
          lastGpsStateUpdateRef.current = now;
          lastReportedSpeedRef.current = speedKmh;

          setLocation(loc.coords);
          setSpeed(speedKmh);
          // For UI, only keep last 200 points for the map trail
          setRouteCoordinates(routeRef.current.slice(-200));
          setDistance(distanceRef.current);
          setSpeedData(speedDataRef.current.slice(-100));
        }
      );

      // Staleness timer: when GPS callbacks stop firing (e.g. stationary with
      // a high distanceInterval), reset speed to 0 so the display doesn't
      // stick on the last recorded value.
      staleSpeedTimerRef.current = setInterval(() => {
        if (
          lastGpsFixRef.current > 0 &&
          Date.now() - lastGpsFixRef.current > STALE_SPEED_TIMEOUT_MS &&
          lastReportedSpeedRef.current !== 0
        ) {
          lastReportedSpeedRef.current = 0;
          setSpeed(0);
        }
      }, 1000);

      setIsTracking(true);
      return true;
    } catch (error) {
      console.error('Error starting GPS tracking:', error);
      return false;
    }
  };

  const stopTracking = async () => {
    if (subscriptionRef.current) {
      subscriptionRef.current.remove();
      subscriptionRef.current = null;
    }
    if (staleSpeedTimerRef.current) {
      clearInterval(staleSpeedTimerRef.current);
      staleSpeedTimerRef.current = null;
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
