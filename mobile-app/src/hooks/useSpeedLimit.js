import { useState, useEffect, useRef, useCallback } from 'react';
import client from '../api/client';

/**
 * Calculate distance between two coordinates using Haversine formula
 */
function calculateDistance(coord1, coord2) {
  const R = 6371000; // Earth's radius in meters
  const dLat = (coord2.latitude - coord1.latitude) * (Math.PI / 180);
  const dLon = (coord2.longitude - coord1.longitude) * (Math.PI / 180);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(coord1.latitude * (Math.PI / 180)) *
      Math.cos(coord2.latitude * (Math.PI / 180)) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c; // meters
}

const QUERY_DISTANCE_THRESHOLD = 100; // meters
const QUERY_TIME_THRESHOLD = 30000; // 30 seconds
const DRAMATIC_CHANGE_THRESHOLD = 30; // km/h - changes larger than this need confirmation
const SMOOTHING_HISTORY_SIZE = 3; // number of recent readings to consider

/**
 * Custom hook for querying speed limits based on GPS location.
 * Throttled: only queries when moved 100m or 30s elapsed.
 * Includes smoothing to prevent GPS drift from causing wild speed limit jumps.
 * Maintains a history of speed limit data for submission.
 *
 * @param {Object|null} location - Current GPS location {latitude, longitude}
 * @param {boolean} isActive - Whether to actively query (true during ride)
 * @returns {Object} Speed limit data and control functions
 */
export default function useSpeedLimit(location, isActive = false) {
  const [currentSpeedLimit, setCurrentSpeedLimit] = useState(null);
  const [roadName, setRoadName] = useState(null);
  const [roadType, setRoadType] = useState(null);
  const [zoneType, setZoneType] = useState('regular');
  const [isLoading, setIsLoading] = useState(false);

  const speedLimitDataRef = useRef([]);
  const lastQueryRef = useRef({ latitude: null, longitude: null, time: 0 });
  const cacheRef = useRef(new Map());
  // Smoothing: track recent raw readings and pending unconfirmed changes
  const recentLimitsRef = useRef([]); // last N raw speed limit readings
  const confirmedLimitRef = useRef(null); // last confirmed (applied) speed limit

  const roundCoord = (val) => Math.round(val * 1000) / 1000;

  const querySpeedLimit = useCallback(async (lat, lon) => {
    const cacheKey = `${roundCoord(lat)},${roundCoord(lon)}`;

    // Check client-side cache
    if (cacheRef.current.has(cacheKey)) {
      const cached = cacheRef.current.get(cacheKey);
      applyResult(cached, lat, lon);
      return;
    }

    setIsLoading(true);
    try {
      const response = await client.get('/diagnostic-rides/speed-limit', {
        params: { lat, lon },
      });

      const result = response.data;
      cacheRef.current.set(cacheKey, result);
      applyResult(result, lat, lon);
    } catch (error) {
      console.error('Speed limit query failed:', error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const applyResult = useCallback((result, lat, lon) => {
    const newLimit = result.speed_limit_kmh;
    const confirmed = confirmedLimitRef.current;

    // Add to recent readings history
    recentLimitsRef.current.push(newLimit);
    if (recentLimitsRef.current.length > SMOOTHING_HISTORY_SIZE) {
      recentLimitsRef.current.shift();
    }

    let limitToApply = newLimit;

    // Smoothing: if the new limit is a dramatic change from the confirmed limit,
    // only accept it if multiple recent readings agree (GPS drift protection)
    if (confirmed !== null && Math.abs(newLimit - confirmed) > DRAMATIC_CHANGE_THRESHOLD) {
      const recent = recentLimitsRef.current;
      // Count how many recent readings are close to the new limit (within 15 km/h)
      const agreeing = recent.filter(l => Math.abs(l - newLimit) <= 15).length;
      // Need at least 2 consecutive similar readings to confirm a dramatic change
      if (agreeing < 2) {
        // Reject the dramatic change - keep the confirmed limit
        limitToApply = confirmed;
      }
    }

    confirmedLimitRef.current = limitToApply;
    setCurrentSpeedLimit(limitToApply);
    setRoadName(result.road_name);
    setRoadType(result.road_type);
    setZoneType(result.zone_type || 'regular');

    // Store in history (with the smoothed limit, not the raw one)
    speedLimitDataRef.current.push({
      timestamp: Date.now(),
      latitude: lat,
      longitude: lon,
      speed_limit: limitToApply,
      road_type: result.road_type,
      road_name: result.road_name,
      zone_type: result.zone_type || 'regular',
      source: result.source,
    });
  }, []);

  useEffect(() => {
    if (!isActive || !location || !location.latitude || !location.longitude) {
      return;
    }

    const now = Date.now();
    const lastQuery = lastQueryRef.current;

    // Check if we should query: moved 100m or 30s elapsed
    let shouldQuery = false;

    if (!lastQuery.latitude || !lastQuery.longitude) {
      shouldQuery = true;
    } else {
      const distanceMoved = calculateDistance(
        { latitude: lastQuery.latitude, longitude: lastQuery.longitude },
        { latitude: location.latitude, longitude: location.longitude }
      );
      const timeElapsed = now - lastQuery.time;

      if (distanceMoved >= QUERY_DISTANCE_THRESHOLD || timeElapsed >= QUERY_TIME_THRESHOLD) {
        shouldQuery = true;
      }
    }

    if (shouldQuery) {
      lastQueryRef.current = {
        latitude: location.latitude,
        longitude: location.longitude,
        time: now,
      };
      querySpeedLimit(location.latitude, location.longitude);
    }
  }, [location, isActive, querySpeedLimit]);

  const getSpeedLimitData = useCallback(() => {
    return speedLimitDataRef.current;
  }, []);

  const clearData = useCallback(() => {
    speedLimitDataRef.current = [];
    cacheRef.current.clear();
    recentLimitsRef.current = [];
    confirmedLimitRef.current = null;
    setCurrentSpeedLimit(null);
    setRoadName(null);
    setRoadType(null);
    setZoneType('regular');
  }, []);

  return {
    currentSpeedLimit,
    roadName,
    roadType,
    zoneType,
    isLoading,
    getSpeedLimitData,
    clearData,
  };
}
