import React, { useState, useEffect, useContext, useRef, useCallback, useMemo } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Alert,
  Modal,
  TextInput,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  AppState,
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Card, Surface, IconButton, FAB, Portal, Dialog, Button, Badge } from 'react-native-paper';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import OSMMap from '../components/OSMMap';
import Ionicons from '@expo/vector-icons/Ionicons';
import useGPSTracking from '../hooks/useGPSTracking';
import useDeviceMotion from '../hooks/useDeviceMotion';
import useSpeedLimit from '../hooks/useSpeedLimit';
import { AuthContext } from '../context/AuthContext';
import client from '../api/client';
import FeedbackPanel from '../components/FeedbackPanel';
import { DEVICE_EVENT_TO_CODE } from '../data/faults';

const DiagnosticRideActiveScreen = ({ route, navigation }) => {
  const { rideType, parentName, instructorId, bookingId, studentId } = route.params;
  const { userToken } = useContext(AuthContext);
  const insets = useSafeAreaInsets();

  const [startTime, setStartTime] = useState(null);
  const [duration, setDuration] = useState(0); // seconds
  const [isUploading, setIsUploading] = useState(false);
  const [latestEvents, setLatestEvents] = useState([]);
  const [alertMessage, setAlertMessage] = useState(null);
  const [noteModalVisible, setNoteModalVisible] = useState(false);
  const [noteText, setNoteText] = useState('');
  const coachNotesRef = useRef([]);
  const [feedbackPanelVisible, setFeedbackPanelVisible] = useState(false);
  const feedbackCountsRef = useRef({}); // {code: {code, label, category, count, timestamps}}
  const [feedbackBadgeCount, setFeedbackBadgeCount] = useState(0);
  const [reportModalVisible, setReportModalVisible] = useState(false);
  const [allEvents, setAllEvents] = useState([]);
  const [rideId, setRideId] = useState(null);
  const [isWsConnected, setIsWsConnected] = useState(false);
  const ws = useRef(null);
  const messageBuffer = useRef([]);

  const sendMessage = useCallback((msg) => {
    if (ws.current && ws.current.readyState === WebSocket.OPEN) {
      // Flush buffer first
      while (messageBuffer.current.length > 0) {
        const bufferedMsg = messageBuffer.current.shift();
        ws.current.send(JSON.stringify(bufferedMsg));
      }
      ws.current.send(JSON.stringify(msg));
    } else {
      // Buffer the message
      messageBuffer.current.push(msg);
      if (messageBuffer.current.length > 500) {
        messageBuffer.current.shift();
      }
    }
  }, []);

  // Hooks for sensor data collection
  const gpsTracking = useGPSTracking();
  const deviceMotion = useDeviceMotion(10); // 10 Hz sampling
  const speedLimit = useSpeedLimit(gpsTracking.location, !!startTime);

  // Stable refs for the latest sensor values.
  // Intervals must read from these refs rather than closing over state — state deps
  // in useEffect cause the interval to be torn down and recreated on every sensor
  // tick, so a 2-second interval would never fire if a 1-second dep keeps resetting it.
  const latestMotionDataRef = useRef([]);
  const latestSpeedDataRef = useRef([]);
  const latestSpeedLimitRef = useRef(null);
  const latestZoneTypeRef = useRef('regular');
  const latestLocationRef = useRef(null);
  const latestSpeedRef = useRef(0);
  const latestAccelerationRef = useRef({ x: 0, y: 0, z: 0 });
  const latestRotationRef = useRef({ x: 0, y: 0, z: 0 });
  const latestDurationRef = useRef(0);
  // triggerAlert is redefined every render; route calls through a ref so interval
  // closures always invoke the freshest version.
  const triggerAlertRef = useRef(null);
  // Tracks the last time each event type triggered an alert (ms timestamp).
  // Prevents the same event type from flooding the UI every 2 seconds.
  const lastAlertByTypeRef = useRef({});
  // Max events to keep in memory — prevents unbounded growth that causes freeze/crash
  const MAX_EVENTS = 300;

  // Speed comparison color
  const getSpeedColor = () => {
    if (!speedLimit.currentSpeedLimit || !gpsTracking.speed) return '#1a1a1a';
    const excess = gpsTracking.speed - speedLimit.currentSpeedLimit;
    if (excess > 10) return '#dc3545'; // red - significantly over
    if (excess > 0) return '#ffc107'; // yellow - slightly over
    return '#28a745'; // green - within limit
  };

  // Feedback criteria handler
  const handleFeedbackUpdate = useCallback((code, delta, metadata) => {
    const current = feedbackCountsRef.current[code] || {
      code: metadata?.code || code,
      label: metadata?.label || code,
      category: metadata?.category || '?',
      count: 0,
      timestamps: [],
    };

    const newCount = Math.max(0, current.count + delta);

    if (newCount === 0) {
      delete feedbackCountsRef.current[code];
    } else {
      feedbackCountsRef.current[code] = {
        ...current,
        count: newCount,
        timestamps: delta > 0 && metadata?.timestamp
          ? [...current.timestamps, { ts: metadata.timestamp, elapsed: metadata.elapsed_seconds }]
          : current.timestamps,
      };
    }

    const total = Object.values(feedbackCountsRef.current)
      .reduce((sum, entry) => sum + entry.count, 0);
    setFeedbackBadgeCount(total);
  }, []);

  // Real-time feedback loop — runs every 2 seconds while the ride is active.
  // IMPORTANT: only stable values (startTime, isUploading) are in the dep array.
  // Sensor state arrays (deviceMotion.data, gpsTracking.speedData) change every
  // ~1 s, which would reset the 2-second interval before it ever fires. We read
  // those values from refs instead (synced each render below).
  useEffect(() => {
    if (!startTime || isUploading) return;

    const feedbackInterval = setInterval(async () => {
      const motionData = latestMotionDataRef.current.slice(-30); // 10Hz * 3s
      const speedData = latestSpeedDataRef.current.slice(-3);    // 1Hz * 3s

      // Require motion data (for braking/cornering detection).
      // Speed data is optional — speeding detection won't fire until GPS settles,
      // but harsh braking and sharp turns are detected from motion alone.
      if (motionData.length < 10) return;

      try {
        const loc = latestLocationRef.current;
        const accelerationWindow = motionData.map(p => ({
          timestamp: p.timestamp,
          x: p.acceleration.x,
          y: p.acceleration.y,
          z: p.acceleration.z,
          latitude: loc?.latitude,
          longitude: loc?.longitude,
        }));

        const speedWindow = speedData.map(p => ({
          timestamp: p.timestamp,
          speed: p.speed,
          latitude: p.latitude,
          longitude: p.longitude,
          speed_limit: latestSpeedLimitRef.current || 0,
        }));

        const rotationWindow = motionData.map(p => ({
          timestamp: p.timestamp,
          x: p.rotation.x,
          y: p.rotation.y,
          z: p.rotation.z,
        }));

        const response = await client.post('/diagnostic-rides/live-evaluate', {
          acceleration_window: accelerationWindow,
          speed_window: speedWindow,
          rotation_window: rotationWindow,
        });

        if (response.data.events && response.data.events.length > 0) {
          const newEvents = response.data.events;
          setLatestEvents(prev => [...newEvents, ...prev].slice(0, 8));
          // Cap allEvents to MAX_EVENTS to prevent unbounded memory growth and render freezes
          setAllEvents(prev => [...newEvents, ...prev].slice(0, MAX_EVENTS));

          // Auto-map sensor events to feedback criteria (F-codes)
          newEvents.forEach(event => {
            const mapping = DEVICE_EVENT_TO_CODE[event.type];
            if (mapping) {
              handleFeedbackUpdate(mapping.code, 1, {
                ...mapping,
                timestamp: Date.now(),
                elapsed_seconds: latestDurationRef.current,
              });
            }
          });

          // Trigger alert for the most severe new event, but rate-limit per event type
          // to at most once every 15 seconds so the UI doesn't get flooded.
          const ALERT_COOLDOWN_MS = 15000;
          const now = Date.now();
          const alertEvent = (newEvents.find(e => e.severity === 'high') ||
                              newEvents.find(e => e.severity === 'medium'));
          if (alertEvent) {
            const lastAlertTs = lastAlertByTypeRef.current[alertEvent.type] || 0;
            if (now - lastAlertTs >= ALERT_COOLDOWN_MS) {
              lastAlertByTypeRef.current[alertEvent.type] = now;
              triggerAlertRef.current(alertEvent);
            }
          }

          // Stream events to backend for live dashboard
          newEvents.forEach(event => {
            sendMessage({
              type: 'event',
              data: {
                ...event,
                timestamp: Date.now() / 1000,
                location: loc ? {
                  latitude: loc.latitude,
                  longitude: loc.longitude,
                } : null,
              },
            });
          });
        }
      } catch (error) {
        console.error('Live evaluation error:', error);
      }
    }, 2000);

    return () => clearInterval(feedbackInterval);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [startTime, isUploading]);

  // triggerAlert reads from refs so it's always fresh even when called from a
  // long-lived interval closure. Assigning to the ref each render keeps the
  // pointer up to date without requiring it as an effect dependency.
  triggerAlertRef.current = (event) => {
    let msg = '';
    let detail = '';
    let icon = 'warning';

    if (event.type === 'speeding') {
      msg = 'Reduce Speed';
      const limit = event.limit || latestSpeedLimitRef.current || '?';
      const speed = Math.round(event.value || latestSpeedRef.current);
      const excess = Math.round(speed - limit);
      detail = `${speed} km/h in a ${limit} km/h zone (+${excess} km/h over)`;
      if (latestZoneTypeRef.current === 'school') {
        msg = 'SCHOOL ZONE - Slow Down!';
        detail = `Speed limit is ${limit} km/h in this school zone`;
        icon = 'school';
      }
    } else if (event.type === 'harsh_braking') {
      msg = 'Braking Too Hard';
      detail = `${event.value}g force detected. Apply brake gradually.`;
      icon = 'hand-left';
    } else if (event.type === 'sharp_turn') {
      msg = 'Turn More Smoothly';
      detail = `${event.value}g lateral force. Reduce speed before turns.`;
      icon = 'refresh';
    } else if (event.type === 'sudden_stop') {
      msg = 'Sudden Stop Detected';
      detail = `${event.value} km/h speed drop. Maintain safe following distance.`;
      icon = 'stop-circle';
    }

    setAlertMessage({ msg, detail, icon });
    setTimeout(() => setAlertMessage(null), 5000);
  };

  useEffect(() => {
    const initTracking = async () => {
      const gpsSuccess = await gpsTracking.startTracking();
      const motionSuccess = await deviceMotion.startTracking();

      if (!gpsSuccess || !motionSuccess) {
        Alert.alert(
          'Error',
          'Failed to start sensor tracking. Please check permissions.',
          [{ text: 'OK', onPress: () => navigation.goBack() }]
        );
        return;
      }

      // Create ride record on backend to get rideId for live streaming
      try {
        const rideData = {
          ride_type: rideType === 'parent' ? 'parent_supervised' : 'instructor_supervised',
          instructor_id: instructorId,
          booking_id: bookingId || null,
          start_time: new Date().toISOString(),
        };
        const response = await client.post('/diagnostic-rides/', rideData);
        setRideId(response.data.id);
        setStartTime(new Date(response.data.start_time));
      } catch (error) {
        console.error('Error starting ride record:', error);
        Alert.alert('Live Sync Offline', 'Could not connect to live streaming. Ride will be saved locally and uploaded at the end.');
        setStartTime(new Date());
      }
    };

    initTracking();

    return () => {
      gpsTracking.stopTracking();
      deviceMotion.stopTracking();
    };
  }, []);

  // WebSocket Connection Effect
  const reconnectTimeout = useRef(null);
  const reconnectAttempts = useRef(0);
  // Tracks whether the app is currently backgrounded.
  // Android kills TCP connections when backgrounded — this is expected.
  // We use this to suppress spurious errors and avoid reconnect loops in background.
  const isInBackgroundRef = useRef(false);

  const connectWebSocket = useCallback(() => {
    if (!rideId) return;

    // Clean up existing connection if any
    if (ws.current) {
      ws.current.onopen = null;
      ws.current.onmessage = null;
      ws.current.onerror = null;
      ws.current.onclose = null;
      ws.current.close();
    }

    const wsBase = client.defaults.baseURL
      .replace('https://', 'wss://')
      .replace('http://', 'ws://');
    const wsUrl = `${wsBase}/live-ride-stream?ride_id=${rideId}&client_type=mobile`;
    
    console.log(`Connecting to WebSocket (Attempt ${reconnectAttempts.current + 1}):`, wsUrl);
    
    const socket = new WebSocket(wsUrl);
    ws.current = socket;

    let heartbeatInterval;

    socket.onopen = () => {
      console.log('WebSocket connected');
      setIsWsConnected(true);
      reconnectAttempts.current = 0;
      socket.send(JSON.stringify({ type: 'ping' }));
      
      // Flush buffered messages
      while (messageBuffer.current.length > 0) {
        const bufferedMsg = messageBuffer.current.shift();
        socket.send(JSON.stringify(bufferedMsg));
      }

      // Start heartbeat
      heartbeatInterval = setInterval(() => {
        if (socket && socket.readyState === WebSocket.OPEN) {
          socket.send(JSON.stringify({ type: 'ping' }));
        }
      }, 30000);
    };

    socket.onmessage = (e) => {
      try {
        const data = JSON.parse(e.data);
        if (data.type === 'pong') console.log('WS Pong');
      } catch (err) {
        console.error('WS Message Parse Error:', err);
      }
    };

    socket.onerror = (e) => {
      if (isInBackgroundRef.current) {
        // Android kills connections when backgrounded — this is expected, not an error.
        console.log('WebSocket closed (app backgrounded)');
      } else {
        console.error('WebSocket error:', e.message);
      }
      setIsWsConnected(false);
    };

    socket.onclose = (e) => {
      console.log('WebSocket closed:', e.code, e.reason);
      setIsWsConnected(false);
      if (heartbeatInterval) clearInterval(heartbeatInterval);

      // If we're in the background, don't start a retry loop —
      // the AppState handler will reconnect immediately when foregrounded.
      if (isInBackgroundRef.current) return;
      
      // Don't reconnect if we intentionally closed it (e.g. unmount or ride complete)
      if (e.code !== 1000 && rideId && !isUploading) {
        const delay = Math.min(1000 * Math.pow(2, reconnectAttempts.current), 30000);
        reconnectAttempts.current += 1;
        console.log(`Reconnecting in ${delay}ms...`);
        reconnectTimeout.current = setTimeout(connectWebSocket, delay);
      }
    };
  }, [rideId, isUploading]);

  useEffect(() => {
    connectWebSocket();
    return () => {
      if (reconnectTimeout.current) clearTimeout(reconnectTimeout.current);
      if (ws.current) ws.current.close(1000);
    };
  }, [connectWebSocket]);

  // Re-connect WebSocket when app returns to foreground.
  // When the user switches away and back, the OS kills the socket.
  // AppState 'active' fires on every foreground resume.
  const appStateRef = useRef(AppState.currentState);
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (nextState) => {
      const prev = appStateRef.current;
      appStateRef.current = nextState;

      if (nextState === 'background' && rideId) {
        isInBackgroundRef.current = true;
        // Persist the in-memory sync buffer so nothing is lost if the OS kills the app.
        const snapshot = syncBufferRef.current.slice(-200); // last 200 events
        AsyncStorage.setItem(`@ride_buffer_${rideId}`, JSON.stringify(snapshot)).catch(() => {});
      }

      if (prev !== 'active' && nextState === 'active' && rideId && !isUploading) {
        isInBackgroundRef.current = false;
        // Clear any pending exponential-backoff timer — reconnect immediately.
        if (reconnectTimeout.current) clearTimeout(reconnectTimeout.current);
        reconnectAttempts.current = 0;
        connectWebSocket();
      }
    });
    return () => subscription.remove();
  }, [rideId, isUploading, connectWebSocket]);

  const [isOffline, setIsOffline] = useState(false);
  const syncBufferRef = useRef([]);
  const BUFFER_KEY_PREFIX = '@ride_buffer_';

  // Telemetry Streaming & Sync Effect
  // IMPORTANT: only rideId and sendMessage are in the dep array — both are stable.
  // Sensor values (acceleration, rotation, speed, location) change every 100ms–1s;
  // including them would restart the 1 s / 10 s intervals on every tick, making it
  // impossible for either interval to fire reliably.  We read current values from
  // refs instead (synced each render below).
  useEffect(() => {
    if (!rideId) return;

    const streamInterval = setInterval(() => {
      if (!latestLocationRef.current) return; // GPS not fixed yet
      const point = {
        acceleration: latestAccelerationRef.current,
        rotation: latestRotationRef.current,
        speed: latestSpeedRef.current,
        location: {
          latitude: latestLocationRef.current.latitude,
          longitude: latestLocationRef.current.longitude,
        },
        heading: latestLocationRef.current.heading,
        timestamp: Date.now() / 1000,
      };

      // 1. Send via WebSocket for live dashboard (low latency)
      sendMessage({
        type: 'telemetry',
        data: point,
      });

      // 2. Add to local buffer
      syncBufferRef.current.push(point);
    }, 1000);

    // Periodically flush buffer to DB via HTTP
    const syncInterval = setInterval(async () => {
      if (syncBufferRef.current.length === 0) return;
      
      const chunk = [...syncBufferRef.current];
      syncBufferRef.current = []; // Clear buffer

      try {
        await client.post(`/diagnostic-rides/${rideId}/telemetry`, chunk);
        setIsOffline(false);
      } catch (e) {
        console.warn('HTTP Sync failed, returning points to buffer:', e);
        syncBufferRef.current = [...chunk, ...syncBufferRef.current]; // Put back to retry
        setIsOffline(true);
        
        // Persist buffer to AsyncStorage for crash recovery
        try {
          await AsyncStorage.setItem(`${BUFFER_KEY_PREFIX}${rideId}`, JSON.stringify(syncBufferRef.current));
        } catch (storageError) {
          console.error('Failed to persist buffer to storage:', storageError);
        }
      }
    }, 30000); // Sync every 30 seconds (reduced from 10 s to cut network overhead)

    // Deep flush every 5 minutes: force-send any pending buffer and clear it so
    // accumulated telemetry doesn't pile up in memory on long/highway rides.
    const deepFlushInterval = setInterval(async () => {
      if (syncBufferRef.current.length === 0) return;
      const chunk = [...syncBufferRef.current];
      syncBufferRef.current = [];
      try {
        await client.post(`/diagnostic-rides/${rideId}/telemetry`, chunk);
        setIsOffline(false);
      } catch (e) {
        // Don't put back on deep flush — data is already streaming via WebSocket.
        // Losing a 5-min chunk here is preferable to an ever-growing buffer.
        console.warn('Deep flush failed, discarding chunk to free memory:', e.message);
        setIsOffline(true);
      }
    }, 300000); // Every 5 minutes

    return () => {
      clearInterval(streamInterval);
      clearInterval(syncInterval);
      clearInterval(deepFlushInterval);
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rideId, sendMessage]);

  useEffect(() => {
    let interval;
    if (startTime) {
      interval = setInterval(() => {
        const now = new Date();
        const elapsed = Math.floor((now - startTime) / 1000);
        setDuration(elapsed);
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [startTime]);

  const formatTime = (seconds) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
  };

  const getEventIcon = (type) => {
    switch (type) {
      case 'speeding': return 'speedometer';
      case 'harsh_braking': return 'hand-left';
      case 'sharp_turn': return 'refresh';
      case 'sudden_stop': return 'stop-circle';
      default: return 'alert-circle';
    }
  };

  const getEventDescription = (event) => {
    if (event.description) return event.description;
    switch (event.type) {
      case 'speeding':
        return `${Math.round(event.value)} km/h in ${event.limit} km/h zone`;
      case 'harsh_braking':
        return `Harsh braking: ${event.value}g force`;
      case 'sharp_turn':
        return `Sharp turn: ${event.value}g lateral force`;
      case 'sudden_stop':
        return `Sudden stop: ${event.value} km/h drop`;
      default:
        return event.type.replace('_', ' ').toUpperCase();
    }
  };

  const handleCompleteRide = async () => {
    const durationMinutes = duration / 60;

    // Reduced thresholds for development/testing ease
    if (durationMinutes < 1) {
      Alert.alert(
        'Too Short',
        'Diagnostic ride must be at least 20 minutes long in production (reduced to 1 min for testing).',
        [{ text: 'OK' }]
      );
      return;
    }

    if (gpsTracking.distance < 0.1) {
      Alert.alert(
        'Too Short',
        'Diagnostic ride must cover at least 5 km in production (reduced to 100m for testing).',
        [{ text: 'OK' }]
      );
      return;
    }

    Alert.alert(
      'Complete Ride?',
      `Duration: ${formatTime(duration)}\nDistance: ${gpsTracking.distance.toFixed(2)} km\n\nThis will end the ride and submit data for evaluation.`,
      [
        { text: 'Keep Driving', style: 'cancel' },
        { text: 'Complete', onPress: submitRideData },
      ]
    );
  };

  const submitRideData = async () => {
    setIsUploading(true);

    try {
      // Stop tracking locally
      const gpsData = await gpsTracking.stopTracking();
      const motionData = deviceMotion.stopTracking();
      const speedLimitData = speedLimit.getSpeedLimitData();

      const endTime = new Date();

      // Format coach notes if any
      const notes = coachNotesRef.current;
      let evaluatorNotes = null;
      if (notes.length > 0) {
        evaluatorNotes = notes.map(n => {
          const mins = Math.floor(n.elapsed_seconds / 60);
          const secs = n.elapsed_seconds % 60;
          return `[${mins}:${secs < 10 ? '0' : ''}${secs}] ${n.text}`;
        }).join('\n');
      }

      // Build sensor arrays from local refs (fallback when MongoDB/WebSocket unavailable)
      const accelerationData = motionData.map(d => ({
        timestamp: d.timestamp,
        x: d.acceleration.x,
        y: d.acceleration.y,
        z: d.acceleration.z,
      }));
      const rotationData = motionData.map(d => ({
        timestamp: d.timestamp,
        x: d.rotation.x,
        y: d.rotation.y,
        z: d.rotation.z,
      }));

      const rideData = {
        ride_type:
          rideType === 'parent'
            ? 'parent_supervised'
            : 'instructor_supervised',
        instructor_id: instructorId,
        booking_id: bookingId || null,
        start_time: startTime.toISOString(),
        end_time: endTime.toISOString(),
        duration_minutes: duration / 60,
        distance_km: gpsData.distance,
        route_coords: JSON.stringify(gpsData.routeCoordinates),
        speed_data: JSON.stringify(gpsData.speedData),
        acceleration_data: JSON.stringify(accelerationData),
        rotation_data: JSON.stringify(rotationData),
        speed_limit_data: JSON.stringify(speedLimitData),
        evaluator_notes: evaluatorNotes,
        human_feedback: JSON.stringify(
          Object.values(feedbackCountsRef.current).filter(item => item.count > 0)
        ),
      };

      let finalRideId = rideId;
      try {
        if (rideId) {
          // Partial update - the backend schemas should allow null for sensor blobs
          await client.put(`/diagnostic-rides/${rideId}`, rideData);
        } else {
          const response = await client.post('/diagnostic-rides/', rideData);
          finalRideId = response.data.id;
        }
      } catch (uploadError) {
        console.warn('Metadata upload failed, attempting to trigger evaluation anyway:', uploadError);
        if (!finalRideId) throw uploadError;
      }

      try {
        // This triggers the DiagnosticEvaluator which now pulls from granular tables
        await client.post(`/diagnostic-rides/${finalRideId}/evaluate`);
      } catch (evalError) {
        console.error('Evaluation trigger failed:', evalError);
        throw evalError;
      }

      // Auto-complete the booking if this ride was linked to one
      if (bookingId) {
        try {
          await client.post(`/bookings/${bookingId}/complete`);
        } catch (e) {
          console.log('Could not auto-complete booking (this is often non-critical):', e);
        }
      }

      Alert.alert('Success', 'Ride submitted! Evaluating your performance...', [
        {
          text: 'View Results',
          onPress: () => navigation.replace('DiagnosticRideResults', { rideId: finalRideId }),
        },
      ]);
    } catch (error) {
      console.error('Error submitting ride:', error);
      const isNetworkError = error.message.includes('Network Error');
      Alert.alert(
        'Submission Error',
        isNetworkError 
          ? `Could not connect to the server. Please ensure your backend is running at ${client.defaults.baseURL} and your phone is on the same network.`
          : 'Failed to submit ride data. Please try again or contact support.'
      );
    } finally {
      setIsUploading(false);
    }
  };

  // Sync sensor refs on every render so intervals always read the latest values
  // without needing those values in their dep arrays.
  latestMotionDataRef.current = deviceMotion.data;
  latestSpeedDataRef.current = gpsTracking.speedData;
  latestSpeedLimitRef.current = speedLimit.currentSpeedLimit;
  latestZoneTypeRef.current = speedLimit.zoneType;
  latestLocationRef.current = gpsTracking.location;
  latestSpeedRef.current = gpsTracking.speed;
  latestAccelerationRef.current = deviceMotion.acceleration;
  latestRotationRef.current = deviceMotion.rotation;
  latestDurationRef.current = duration;

  // Memoize map props so OSMMap doesn't receive new object references on every
  // render (which would bypass React.memo and re-animate on every GPS tick).
  // Must be before the early return to satisfy the Rules of Hooks.
  const mapRegion = useMemo(() => {
    if (!gpsTracking.location) return null;
    return {
      latitude: gpsTracking.location.latitude,
      longitude: gpsTracking.location.longitude,
      latitudeDelta: 0.01,
      longitudeDelta: 0.01,
    };
  }, [gpsTracking.location?.latitude, gpsTracking.location?.longitude]);

  const mapMarkers = useMemo(() => {
    if (!gpsTracking.location) return [];
    return [{
      latitude: gpsTracking.location.latitude,
      longitude: gpsTracking.location.longitude,
      title: 'Current Position',
    }];
  }, [gpsTracking.location?.latitude, gpsTracking.location?.longitude]);

  const mapPolylines = useMemo(() => [{
    coordinates: gpsTracking.routeCoordinates,
    strokeWidth: 4,
    strokeColor: '#007bff',
  }], [gpsTracking.routeCoordinates]);

  if (!gpsTracking.location) {
    return (
      <View style={styles.loadingContainer}>
        <Text style={styles.loadingText}>Initializing GPS...</Text>
      </View>
    );
  }

  const canComplete = duration >= 60 * 20 && gpsTracking.distance >= 5;

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* Safety Banner */}
      <View style={styles.safetyBanner}>
        <Ionicons name="hand-right" size={20} color="white" />
        <Text style={styles.safetyText}>DRIVER SAFETY MODE — OPERATE BY SUPERVISOR ONLY</Text>
      </View>

      <OSMMap
        style={styles.map}
        region={mapRegion}
        markers={mapMarkers}
        polylines={mapPolylines}
      />

      {/* Debug Indicator */}
      <View style={{ position: 'absolute', top: 10, left: 10, backgroundColor: 'rgba(0,0,0,0.6)', padding: 4, borderRadius: 4, zIndex: 9999 }}>
        <Text style={{ color: '#fff', fontSize: 10, fontWeight: 'bold' }}>OSM MAP ACTIVE v2</Text>
      </View>

      <View style={[styles.overlay, { top: 80 }]}>
        {/* Connection Status Badge */}
        <View style={{ position: 'absolute', top: -30, right: 10, flexDirection: 'row', alignItems: 'center', backgroundColor: 'rgba(255,255,255,0.9)', paddingHorizontal: 8, paddingVertical: 4, borderRadius: 12, borderWidth: 1, borderColor: (isWsConnected && !isOffline) ? '#28a745' : (isOffline ? '#ffc107' : '#dc3545') }}>
          <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: (isWsConnected && !isOffline) ? '#28a745' : (isOffline ? '#ffc107' : '#dc3545'), marginRight: 6 }} />
          <Text style={{ fontSize: 10, fontWeight: 'bold', color: (isWsConnected && !isOffline) ? '#28a745' : (isOffline ? '#ffc107' : '#dc3545') }}>
            {isWsConnected ? (isOffline ? 'BUFFERING DATA' : 'LIVE SYNC ACTIVE') : (isOffline ? 'OFFLINE BUFFERING' : 'SYNC OFFLINE')}
          </Text>
        </View>

        {/* Real-time Alert Banner */}
        {alertMessage && (
          <View style={[
            styles.alertBanner,
            speedLimit.currentSpeedLimit && speedLimit.zoneType === 'school' && styles.schoolAlertBanner
          ]}>
            <Ionicons name={alertMessage.icon || 'warning'} size={20} color="white" />
            <View style={styles.alertContent}>
              <Text style={styles.alertText}>{alertMessage.msg}</Text>
              <Text style={styles.alertDetail}>{alertMessage.detail}</Text>
            </View>
          </View>
        )}

        {/* Stats Row */}
        <View style={styles.statsContainer}>
          <Surface style={styles.statBox} elevation={2}>
            <Text style={styles.statLabel} accessibilityRole="header">Time</Text>
            <Text style={styles.statValue} accessibilityLabel={`Elapsed time ${formatTime(duration)}`}>{formatTime(duration)}</Text>
            <Text style={styles.statTarget}>Target: 20:00</Text>
          </Surface>

          <Surface style={styles.statBox} elevation={2}>
            <Text style={styles.statLabel} accessibilityRole="header">Distance</Text>
            <Text style={styles.statValue} accessibilityLabel={`Distance driven ${gpsTracking.distance.toFixed(2)} kilometers`}>
              {gpsTracking.distance.toFixed(2)} km
            </Text>
            <Text style={styles.statTarget}>Target: 5.0 km</Text>
          </Surface>

          <Surface style={styles.statBox} elevation={2}>
            <Text style={styles.statLabel} accessibilityRole="header">Speed</Text>
            <Text style={[styles.statValue, { color: getSpeedColor() }]} accessibilityLabel={`Current speed ${gpsTracking.speed.toFixed(0)} kilometers per hour`}>
              {gpsTracking.speed.toFixed(0)} km/h
            </Text>
          </Surface>

          <Surface style={[styles.statBox, styles.limitBox]} elevation={4}>
            <Text style={styles.statLabel} accessibilityRole="header">Limit</Text>
            <Text style={styles.statValue} accessibilityLabel={`Speed limit ${speedLimit.currentSpeedLimit || 'unknown'}`}>
              {speedLimit.currentSpeedLimit || '--'}
            </Text>
            {speedLimit.zoneType === 'school' && (
              <Badge style={styles.schoolBadge} size={14}>SCHOOL</Badge>
            )}
            {speedLimit.zoneType !== 'school' && speedLimit.roadType && (
              <Text style={styles.statTarget}>
                {speedLimit.roadType}
              </Text>
            )}
          </Surface>
        </View>

        {/* Mistake Log */}
        {latestEvents.length > 0 && (
          <Card style={styles.eventLogContainer} onPress={() => setReportModalVisible(true)}>
            <Card.Content>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                <Text style={styles.eventLogTitle} accessibilityRole="header">Recent Events</Text>
                <Text style={{ fontSize: 10, color: '#007bff' }}>View All ({allEvents.length})</Text>
              </View>
              <ScrollView style={{ maxHeight: 90 }}>
                {latestEvents.map((event, idx) => (
                  <View key={idx} style={styles.eventItem}>
                    <Ionicons
                      name={getEventIcon(event.type)}
                      size={16}
                      color={event.severity === 'high' ? "#dc3545" : "#ffc107"}
                    />
                    <Text style={styles.eventText}>
                      {getEventDescription(event)}
                    </Text>
                  </View>
                ))}
              </ScrollView>
            </Card.Content>
          </Card>
        )}

        {/* Supervisor Actions */}
        <View style={styles.actionRow}>
          <Button
              mode="outlined"
              icon="flag"
              onPress={() => setFeedbackPanelVisible(true)}
              style={[styles.actionButton, { borderColor: '#e17055' }]}
              labelStyle={{ color: '#e17055' }}
              accessibilityLabel="Open Feedback Panel to flag mistakes"
          >
              Flag {feedbackBadgeCount > 0 ? `(${feedbackBadgeCount})` : ''}
          </Button>

          <Button
              mode="outlined"
              icon="chat-processing"
              onPress={() => setNoteModalVisible(true)}
              style={[styles.actionButton, { borderColor: '#007bff' }]}
              labelStyle={{ color: '#007bff' }}
              accessibilityLabel="Add coach note"
          >
              Note {coachNotesRef.current.length > 0 ? `(${coachNotesRef.current.length})` : ''}
          </Button>
        </View>

        <View style={styles.sensorIndicators}>
          <View style={styles.indicator}>
            <Text style={styles.indicatorDot}>●</Text>
            <Text style={styles.indicatorText}>GPS Active</Text>
          </View>
          <View style={styles.indicator}>
            <Text style={styles.indicatorDot}>●</Text>
            <Text style={styles.indicatorText}>
              Motion: {deviceMotion.data.length} samples
            </Text>
          </View>
          <View style={styles.indicator}>
            <Text style={[
              styles.indicatorDot,
              { color: speedLimit.currentSpeedLimit ? '#28a745' : '#ffc107' }
            ]}>●</Text>
            <Text style={styles.indicatorText}>
              {speedLimit.isLoading ? 'Fetching limit...' :
               speedLimit.currentSpeedLimit ? 'Speed limit active' : 'No limit data'}
            </Text>
          </View>
        </View>

        <Button
          mode="contained"
          onPress={handleCompleteRide}
          disabled={!canComplete || isUploading}
          loading={isUploading}
          style={[
            styles.completeButton,
            (!canComplete || isUploading) && { backgroundColor: '#6c757d' }
          ]}
          contentStyle={{ height: 60 }}
          labelStyle={{ fontSize: 18, fontWeight: 'bold' }}
          accessibilityLabel={canComplete ? "Complete Diagnostic Ride" : "Keep driving to meet requirements"}
          accessibilityRole="button"
        >
          {canComplete ? 'Complete Ride' : 'Keep Driving'}
        </Button>
      </View>
      {/* Feedback Panel */}
      <FeedbackPanel
        visible={feedbackPanelVisible}
        onClose={() => setFeedbackPanelVisible(false)}
        feedbackCounts={feedbackCountsRef.current}
        onUpdateCount={handleFeedbackUpdate}
        elapsedSeconds={duration}
      />

      {/* Live Report Modal */}
      <Portal>
        <Dialog visible={reportModalVisible} onDismiss={() => setReportModalVisible(false)} style={{ maxHeight: '80%' }}>
          <Dialog.Title>Full Live Report</Dialog.Title>
          <Dialog.ScrollArea>
            <ScrollView contentContainerStyle={{ paddingVertical: 10 }}>
              {allEvents.length > 0 ? (
                allEvents.map((event, idx) => (
                  <View key={idx} style={[styles.eventItem, { marginBottom: 12, paddingBottom: 12, borderBottomWidth: 1, borderBottomColor: '#eee' }]}>
                    <Ionicons
                      name={getEventIcon(event.type)}
                      size={24}
                      color={event.severity === 'high' ? "#dc3545" : "#ffc107"}
                    />
                    <View style={{ flex: 1 }}>
                      <Text style={{ fontWeight: 'bold', fontSize: 14 }}>{event.type.replace('_', ' ').toUpperCase()}</Text>
                      <Text style={{ fontSize: 12, color: '#666' }}>{getEventDescription(event)}</Text>
                      <Text style={{ fontSize: 10, color: '#999', marginTop: 2 }}>
                        {new Date(event.timestamp * 1000).toLocaleTimeString()}
                      </Text>
                    </View>
                  </View>
                ))
              ) : (
                <Text style={{ textAlign: 'center', color: '#666', padding: 20 }}>No events recorded yet.</Text>
              )}
            </ScrollView>
          </Dialog.ScrollArea>
          <Dialog.Actions>
            <Button onPress={() => setReportModalVisible(false)}>Close</Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>

      {/* Coach Note Modal */}
      <Modal
        visible={noteModalVisible}
        transparent
        animationType="slide"
        onRequestClose={() => setNoteModalVisible(false)}
      >
        <KeyboardAvoidingView
          behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
          style={styles.modalOverlay}
        >
          <View style={styles.modalContent}>
            <Text style={styles.modalTitle}>Add Coach Note</Text>
            <Text style={styles.modalSubtitle}>Record an observation for the student</Text>
            <TextInput
              style={styles.noteInput}
              placeholder="e.g. Needs to check mirrors more often..."
              placeholderTextColor="#adb5bd"
              multiline
              autoFocus
              value={noteText}
              onChangeText={setNoteText}
            />
            <View style={styles.modalButtons}>
              <TouchableOpacity
                style={styles.modalCancelBtn}
                onPress={() => {
                  setNoteText('');
                  setNoteModalVisible(false);
                }}
              >
                <Text style={styles.modalCancelText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.modalSaveBtn, !noteText.trim() && styles.modalSaveBtnDisabled]}
                disabled={!noteText.trim()}
                onPress={() => {
                  coachNotesRef.current.push({
                    text: noteText.trim(),
                    timestamp: Date.now(),
                    elapsed_seconds: duration,
                  });
                  setNoteText('');
                  setNoteModalVisible(false);
                }}
              >
                <Text style={styles.modalSaveText}>Save Note</Text>
              </TouchableOpacity>
            </View>
          </View>
        </KeyboardAvoidingView>
      </Modal>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  safetyBanner: {
    backgroundColor: '#d63031',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 6,
    gap: 8,
  },
  safetyText: {
    color: 'white',
    fontSize: 10,
    fontWeight: 'bold',
    letterSpacing: 0.5,
  },
  map: {
    flex: 1,
  },
  alertBanner: {
    backgroundColor: 'rgba(214, 48, 49, 0.95)',
    marginHorizontal: 12,
    marginTop: 8,
    padding: 10,
    borderRadius: 10,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
    elevation: 5,
  },
  schoolAlertBanner: {
    backgroundColor: 'rgba(255, 165, 0, 0.95)',
    borderWidth: 2,
    borderColor: '#fff',
  },
  alertContent: {
    flex: 1,
  },
  alertText: {
    color: 'white',
    fontWeight: 'bold',
    fontSize: 16,
  },
  alertDetail: {
    color: 'rgba(255, 255, 255, 0.9)',
    fontSize: 12,
    marginTop: 1,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f8f9fa',
  },
  loadingText: {
    fontSize: 16,
    color: '#6c757d',
  },
  overlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    pointerEvents: 'box-none',
  },
  statsContainer: {
    flexDirection: 'row',
    margin: 8,
    gap: 4,
  },
  statBox: {
    flex: 1,
    backgroundColor: '#fff',
    borderRadius: 10,
    padding: 8,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  limitBox: {
    borderWidth: 2,
    borderColor: '#007bff',
  },
  statLabel: {
    fontSize: 10,
    color: '#6c757d',
    marginBottom: 1,
  },
  statValue: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#1a1a1a',
  },
  statTarget: {
    fontSize: 8,
    color: '#6c757d',
    marginTop: 1,
  },
  schoolBadge: {
    backgroundColor: '#ff9800',
    borderRadius: 4,
    paddingHorizontal: 4,
    paddingVertical: 1,
    marginTop: 1,
  },
  schoolBadgeText: {
    color: 'white',
    fontSize: 7,
    fontWeight: 'bold',
  },
  eventLogContainer: {
    backgroundColor: 'rgba(255, 255, 255, 0.95)',
    marginHorizontal: 8,
    marginBottom: 6,
    borderRadius: 10,
    padding: 10,
    borderWidth: 1,
    borderColor: '#eee',
    maxHeight: 120,
  },
  eventLogTitle: {
    fontSize: 11,
    fontWeight: 'bold',
    color: '#636e72',
    marginBottom: 6,
    textTransform: 'uppercase',
  },
  eventItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginBottom: 4,
  },
  eventText: {
    fontSize: 11,
    color: '#2d3436',
    fontWeight: '500',
    flex: 1,
  },
  actionRow: {
    flexDirection: 'row',
    marginHorizontal: 8,
    marginBottom: 6,
    gap: 8,
  },
  actionButton: {
    flex: 1,
    backgroundColor: 'white',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 8,
    borderRadius: 10,
    gap: 6,
    borderWidth: 1,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  feedbackButton: {
    borderColor: '#e17055',
  },
  feedbackButtonText: {
    color: '#e17055',
    fontWeight: 'bold',
    fontSize: 13,
  },
  feedbackBadge: {
    backgroundColor: '#e17055',
    borderRadius: 8,
    minWidth: 18,
    height: 18,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 4,
  },
  feedbackBadgeText: {
    color: '#fff',
    fontSize: 10,
    fontWeight: 'bold',
  },
  noteButton: {
    borderColor: '#eee',
  },
  noteButtonText: {
    color: '#007bff',
    fontWeight: 'bold',
    fontSize: 13,
  },
  sensorIndicators: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 6,
    marginHorizontal: 8,
    marginBottom: 4,
    flexWrap: 'wrap',
  },
  indicator: {
    flexDirection: 'row',
    backgroundColor: '#fff',
    borderRadius: 15,
    paddingHorizontal: 8,
    paddingVertical: 4,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
  },
  indicatorDot: {
    fontSize: 10,
    color: '#28a745',
    marginRight: 3,
  },
  indicatorText: {
    fontSize: 10,
    color: '#1a1a1a',
  },
  completeButton: {
    position: 'absolute',
    bottom: 24,
    left: 16,
    right: 16,
    borderRadius: 12,
    elevation: 5,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'flex-end',
  },
  modalContent: {
    backgroundColor: '#fff',
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    padding: 20,
    paddingBottom: 32,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#1a1a1a',
    marginBottom: 4,
  },
  modalSubtitle: {
    fontSize: 13,
    color: '#6c757d',
    marginBottom: 16,
  },
  noteInput: {
    borderWidth: 1,
    borderColor: '#dee2e6',
    borderRadius: 12,
    padding: 12,
    fontSize: 15,
    color: '#1a1a1a',
    minHeight: 100,
    textAlignVertical: 'top',
    backgroundColor: '#f8f9fa',
  },
  modalButtons: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 12,
    marginTop: 16,
  },
  modalCancelBtn: {
    paddingVertical: 10,
    paddingHorizontal: 20,
    borderRadius: 8,
  },
  modalCancelText: {
    color: '#6c757d',
    fontSize: 15,
    fontWeight: '600',
  },
  modalSaveBtn: {
    backgroundColor: '#007bff',
    paddingVertical: 10,
    paddingHorizontal: 20,
    borderRadius: 8,
  },
  modalSaveBtnDisabled: {
    backgroundColor: '#adb5bd',
  },
  modalSaveText: {
    color: '#fff',
    fontSize: 15,
    fontWeight: 'bold',
  },
});

export default DiagnosticRideActiveScreen;
