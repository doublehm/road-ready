import { renderHook, act } from '@testing-library/react-native';
import useGPSTracking from '../useGPSTracking';
import * as Location from 'expo-location';

// Mock expo-location
jest.mock('expo-location', () => ({
  requestForegroundPermissionsAsync: jest.fn().mockResolvedValue({ status: 'granted' }),
  getCurrentPositionAsync: jest.fn().mockResolvedValue({
    coords: { latitude: 49.2827, longitude: -123.1207, speed: 0 }
  }),
  watchPositionAsync: jest.fn().mockResolvedValue({
    remove: jest.fn()
  }),
  Accuracy: { High: 4 }
}));

describe('useGPSTracking', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('initializes with default values', () => {
    const { result } = renderHook(() => useGPSTracking());
    
    expect(result.current.isTracking).toBe(false);
    expect(result.current.speed).toBe(0);
    expect(result.current.distance).toBe(0);
    expect(result.current.routeCoordinates).toEqual([]);
  });

  it('starts tracking correctly', async () => {
    const { result } = renderHook(() => useGPSTracking());
    
    let success;
    await act(async () => {
      success = await result.current.startTracking();
    });
    
    expect(Location.requestForegroundPermissionsAsync).toHaveBeenCalled();
    expect(Location.watchPositionAsync).toHaveBeenCalled();
    expect(success).toBe(true);
    expect(result.current.isTracking).toBe(true);
  });

  it('stops tracking and returns data', async () => {
    const { result } = renderHook(() => useGPSTracking());
    
    await act(async () => {
      await result.current.startTracking();
    });
    
    let data;
    act(() => {
      data = result.current.stopTracking();
    });
    
    expect(result.current.isTracking).toBe(false);
    expect(data).toHaveProperty('routeCoordinates');
    expect(data).toHaveProperty('distance');
  });
});
