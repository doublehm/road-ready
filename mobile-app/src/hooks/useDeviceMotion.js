import { useState, useEffect, useRef } from 'react';
import { DeviceMotion } from 'expo-sensors';

/**
 * Custom hook for collecting device motion data (accelerometer + gyroscope)
 *
 * @param {number} sampleRate - Samples per second (default: 10 Hz)
 * @returns {Object} Motion data and control functions
 */
export default function useDeviceMotion(sampleRate = 10) {
  const [isTracking, setIsTracking] = useState(false);
  const [acceleration, setAcceleration] = useState({ x: 0, y: 0, z: 0 });
  const [rotation, setRotation] = useState({ x: 0, y: 0, z: 0 });
  const [data, setData] = useState([]);

  const subscriptionRef = useRef(null);
  const dataRef = useRef([]);

  useEffect(() => {
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
      // Request permissions
      const { status } = await DeviceMotion.requestPermissionsAsync();

      if (status !== 'granted') {
        console.error('Device motion permission not granted');
        return false;
      }

      // Set update interval (in milliseconds)
      const updateInterval = 1000 / sampleRate; // Convert Hz to ms
      DeviceMotion.setUpdateInterval(updateInterval);

      // Clear previous data
      dataRef.current = [];
      setData([]);

      // Subscribe to device motion updates
      subscriptionRef.current = DeviceMotion.addListener((motionData) => {
        const timestamp = Date.now();

        // Extract acceleration (m/s²)
        const accel = {
          x: motionData.acceleration?.x || 0,
          y: motionData.acceleration?.y || 0,
          z: motionData.acceleration?.z || 0,
        };

        // Extract rotation rate (rad/s)
        const rot = {
          x: motionData.rotationRate?.alpha || 0, // Pitch
          y: motionData.rotationRate?.beta || 0,  // Roll
          z: motionData.rotationRate?.gamma || 0, // Yaw
        };

        // Update current values
        setAcceleration(accel);
        setRotation(rot);

        // Store data point
        const dataPoint = {
          timestamp,
          acceleration: accel,
          rotation: rot,
        };

        dataRef.current.push(dataPoint);

        // Update state periodically (every 10 samples to avoid too many re-renders)
        if (dataRef.current.length % 10 === 0) {
          setData([...dataRef.current]);
        }
      });

      setIsTracking(true);
      return true;
    } catch (error) {
      console.error('Error starting device motion tracking:', error);
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
    setData([...dataRef.current]);

    return dataRef.current;
  };

  const clearData = () => {
    dataRef.current = [];
    setData([]);
  };

  return {
    isTracking,
    acceleration,
    rotation,
    data,
    startTracking,
    stopTracking,
    clearData,
  };
}
