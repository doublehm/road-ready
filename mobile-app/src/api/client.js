import axios from 'axios';
import * as SecureStore from 'expo-secure-store';
import AsyncStorage from '@react-native-async-storage/async-storage';
import NetInfo from '@react-native-community/netinfo';

export const SERVER_URL = 'http://10.32.100.57:8000';
export const BASE_URL = `${SERVER_URL}/api/v1`;

const client = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
});

// Cache implementation for offline support
const CACHE_PREFIX = 'api_cache_';
const EMERGENCY_CLEANUP_KEY = 'emergency_cleanup_v1';

// Run emergency cleanup once to free up space from previous excessive caching
(async () => {
  try {
    const hasCleaned = await AsyncStorage.getItem(EMERGENCY_CLEANUP_KEY);
    if (!hasCleaned) {
      const keys = await AsyncStorage.getAllKeys();
      const cacheKeys = keys.filter(k => k.startsWith(CACHE_PREFIX));
      if (cacheKeys.length > 0) {
        await AsyncStorage.multiRemove(cacheKeys);
      }
      await AsyncStorage.setItem(EMERGENCY_CLEANUP_KEY, 'true');
      console.log('Emergency cache cleanup performed');
    }
  } catch (e) {
    console.warn('Emergency cleanup failed:', e);
  }
})();

client.interceptors.request.use(async (config) => {
  const token = await SecureStore.getItemAsync('userToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Wrap GET requests with caching
const originalGet = client.get;

export const clearCache = async () => {
  try {
    const keys = await AsyncStorage.getAllKeys();
    const cacheKeys = keys.filter(k => k.startsWith(CACHE_PREFIX));
    if (cacheKeys.length > 0) {
      await AsyncStorage.multiRemove(cacheKeys);
    }
    console.log('Cache cleared successfully');
  } catch (e) {
    console.error('Failed to clear cache:', e);
  }
};

client.get = async (url, config) => {
  const netInfo = await NetInfo.fetch();
  const cacheKey = CACHE_PREFIX + url + JSON.stringify(config || {});

  if (!netInfo.isConnected) {
    try {
      const cachedData = await AsyncStorage.getItem(cacheKey);
      if (cachedData) {
        console.log('Serving from cache:', url);
        return { data: JSON.parse(cachedData), fromCache: true };
      }
    } catch (e) {
      console.warn('Cache read failed:', e);
    }
  }

  try {
    const response = await originalGet.call(client, url, config);
    
    // Only cache successful GET responses that are small enough (< 20KB)
    // and avoid caching heavy telemetry-heavy detail endpoints or trend data
    const isHeavyEndpoint = url.includes('/diagnostic-ride/') || url.includes('/progress-trends');
    const responseSize = JSON.stringify(response.data).length;
    
    if (!isHeavyEndpoint && responseSize < 20000) {
      try {
        await AsyncStorage.setItem(cacheKey, JSON.stringify(response.data));
      } catch (storageError) {
        console.warn('Cache write failed (possibly disk full):', storageError);
        // If disk is full, clear the oldest cache entries
        if (storageError.message.includes('full')) {
          const keys = await AsyncStorage.getAllKeys();
          const cacheKeys = keys.filter(k => k.startsWith(CACHE_PREFIX));
          if (cacheKeys.length > 0) {
            await AsyncStorage.multiRemove(cacheKeys.slice(0, 10));
          }
        }
      }
    }
    return response;
  } catch (error) {
    if (!netInfo.isConnected || error.code === 'ECONNABORTED') {
      const cachedData = await AsyncStorage.getItem(cacheKey);
      if (cachedData) {
        return { data: JSON.parse(cachedData), fromCache: true };
      }
    }
    throw error;
  }
};

export default client;
