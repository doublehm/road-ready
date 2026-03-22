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
// Max sensor samples held in memory during a ride (~60 s at 10 Hz).
// Older samples are already flushed to the server via the live-stream; we only
// need a recent window for the final submission fallback.
const DATA_WINDOW = 600;
// Minimum ms between React state updates for acceleration/rotation.
// Sensors still sample at full rate into refs; this only limits re-renders.
const STATE_THROTTLE_MS = 150;

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
  const lastAccelStateUpdateRef = useRef(0);
  const lastGyroStateUpdateRef = useRef(0);
  // Ring buffer for 3-sample moving average to pre-filter accelerometer noise
  const accelRingRef = useRef([]);

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
      accelRingRef.current = [];
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
        const rawUserAccel = {
          x: (accelData.x - gravityRef.current.x) * 9.81,
          y: (accelData.y - gravityRef.current.y) * 9.81,
          z: (accelData.z - gravityRef.current.z) * 9.81,
        };

        // 3-sample moving average to pre-filter noise spikes (potholes, vibration)
        const ring = accelRingRef.current;
        ring.push(rawUserAccel);
        if (ring.length > 3) ring.shift();

        const smoothed = {
          x: ring.reduce((s, p) => s + p.x, 0) / ring.length,
          y: ring.reduce((s, p) => s + p.y, 0) / ring.length,
          z: ring.reduce((s, p) => s + p.z, 0) / ring.length,
        };

        lastUserAccelRef.current = smoothed;
        
        // Throttle state update to avoid excessive re-renders at high sample rates
        const now = Date.now();
        if (now - lastAccelStateUpdateRef.current >= STATE_THROTTLE_MS) {
          setAcceleration(lastUserAccelRef.current);
          lastAccelStateUpdateRef.current = now;
        }
        syncData();
      });

      // Subscribe to Gyroscope
      gyroSubscriptionRef.current = Gyroscope.addListener((gyroData) => {
        lastGyroRef.current = {
          x: gyroData.x,
          y: gyroData.y,
          z: gyroData.z,
        };
        // Throttle state update
        const now = Date.now();
        if (now - lastGyroStateUpdateRef.current >= STATE_THROTTLE_MS) {
          setRotation(lastGyroRef.current);
          lastGyroStateUpdateRef.current = now;
        }
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

    // Keep a rolling window to bound memory during long rides
    if (dataRef.current.length > DATA_WINDOW) {
      dataRef.current = dataRef.current.slice(-DATA_WINDOW);
    }

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
