import { useState, useEffect, useRef } from 'react';
import { Accelerometer, Gyroscope } from 'expo-sensors';

/**
 * Custom hook for collecting device motion data (accelerometer + gyroscope)
 * 
 * This version uses Accelerometer and Gyroscope directly to avoid a known 
 * crash in DeviceMotion on some Android devices/versions (Display context error).
 *
 * @param {number} sampleRate - Samples per second (default: 10 Hz)
 * @returns {Object} Motion data and control functions
 */
export default function useDeviceMotion(sampleRate = 10) {
  const [isTracking, setIsTracking] = useState(false);
  const [acceleration, setAcceleration] = useState({ x: 0, y: 0, z: 0 });
  const [rotation, setRotation] = useState({ x: 0, y: 0, z: 0 });
  const [data, setData] = useState([]);

  const accelSubscriptionRef = useRef(null);
  const gyroSubscriptionRef = useRef(null);
  const dataRef = useRef([]);
  const gravityRef = useRef({ x: 0, y: 0, z: 1 }); // Start with 1g on Z axis
  const lastUserAccelRef = useRef({ x: 0, y: 0, z: 0 });
  const lastGyroRef = useRef({ x: 0, y: 0, z: 0 });

  useEffect(() => {
    return () => {
      // Cleanup on unmount
      stopTracking();
    };
  }, []);

  const startTracking = async () => {
    try {
      // Request permissions
      const { status: accelStatus } = await Accelerometer.requestPermissionsAsync();
      const { status: gyroStatus } = await Gyroscope.requestPermissionsAsync();

      if (accelStatus !== 'granted' || gyroStatus !== 'granted') {
        console.error('Sensor permissions not granted');
        return false;
      }

      // Set update interval (in milliseconds)
      const updateInterval = 1000 / sampleRate; // Convert Hz to ms
      Accelerometer.setUpdateInterval(updateInterval);
      Gyroscope.setUpdateInterval(updateInterval);

      // Clear previous data
      dataRef.current = [];
      setData([]);

      // Subscribe to Accelerometer
      accelSubscriptionRef.current = Accelerometer.addListener((accelData) => {
        // Accelerometer provides values in Gs (1g ≈ 9.81 m/s²)
        // We apply a basic high-pass filter to approximate user acceleration (removing gravity).
        
        const alpha = 0.8; // Low-pass filter constant to isolate gravity
        
        gravityRef.current = {
          x: alpha * gravityRef.current.x + (1 - alpha) * accelData.x,
          y: alpha * gravityRef.current.y + (1 - alpha) * accelData.y,
          z: alpha * gravityRef.current.z + (1 - alpha) * accelData.z,
        };

        // User acceleration = total acceleration - gravity
        lastUserAccelRef.current = {
          x: (accelData.x - gravityRef.current.x) * 9.81,
          y: (accelData.y - gravityRef.current.y) * 9.81,
          z: (accelData.z - gravityRef.current.z) * 9.81,
        };
        
        setAcceleration(lastUserAccelRef.current);
        syncData();
      });

      // Subscribe to Gyroscope
      gyroSubscriptionRef.current = Gyroscope.addListener((gyroData) => {
        lastGyroRef.current = {
          x: gyroData.x,
          y: gyroData.y,
          z: gyroData.z,
        };
        setRotation(lastGyroRef.current);
      });

      setIsTracking(true);
      return true;
    } catch (error) {
      console.error('Error starting sensor tracking:', error);
      return false;
    }
  };

  const syncData = () => {
    const timestamp = Date.now();
    const dataPoint = {
      timestamp,
      acceleration: { ...lastUserAccelRef.current },
      rotation: { ...lastGyroRef.current },
    };

    dataRef.current.push(dataPoint);

    // Update state periodically (every 10 samples)
    if (dataRef.current.length % 10 === 0) {
      setData(dataRef.current.slice(-100));
    }
  };

  const stopTracking = () => {
    if (accelSubscriptionRef.current) {
      accelSubscriptionRef.current.remove();
      accelSubscriptionRef.current = null;
    }
    if (gyroSubscriptionRef.current) {
      gyroSubscriptionRef.current.remove();
      gyroSubscriptionRef.current = null;
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
