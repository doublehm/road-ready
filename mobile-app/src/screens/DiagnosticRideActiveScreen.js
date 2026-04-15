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
  ActivityIndicator,
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Card, Surface, IconButton, FAB, Portal, Dialog, Button } from 'react-native-paper';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import OSMMap from '../components/OSMMap';
import Ionicons from '@expo/vector-icons/Ionicons';
import useGPSTracking from '../hooks/useGPSTracking';
import useDeviceMotion from '../hooks/useDeviceMotion';
import useSpeedLimit from '../hooks/useSpeedLimit';
import { AuthContext } from '../context/AuthContext';
import client from '../api/client';
import FeedbackPanel from '../components/FeedbackPanel';
import GForceOverlay from '../components/GForceOverlay';
import TelemetryPanel from '../components/TelemetryPanel';
import { DEVICE_EVENT_TO_CODE } from '../data/faults';

const DiagnosticRideActiveScreen = ({ route, navigation }) => {
  const { rideType, parentName, instructorId, bookingId, studentId } = route.params;
  const { userToken } = useContext(AuthContext);
  const insets = useSafeAreaInsets();

  const [startTime, setStartTime] = useState(null);
  const [duration, setDuration] = useState(0);
  const [isUploading, setIsUploading] = useState(false);
  const [latestEvents, setLatestEvents] = useState([]);
  const [alertMessage, setAlertMessage] = useState(null);
  const [noteModalVisible, setNoteModalVisible] = useState(false);
  const [noteText, setNoteText] = useState('');
  const coachNotesRef = useRef([]);
  const [feedbackPanelVisible, setFeedbackPanelVisible] = useState(false);
  const feedbackCountsRef = useRef({});
  const [feedbackBadgeCount, setFeedbackBadgeCount] = useState(0);
  const [reportModalVisible, setReportModalVisible] = useState(false);
  const [allEvents, setAllEvents] = useState([]);
  const [autoFlagCounts, setAutoFlagCounts] = useState({});
  const [speedAnalysis, setSpeedAnalysis] = useState({
    speedingSamples: 0,
    totalSpeedSamples: 0,
    maxExcessKmh: 0,
    schoolZoneViolations: 0,
    speedSum: 0,
    speedSqSum: 0,
  });
  const [rideId, setRideId] = useState(null);
  const [isWsConnected, setIsWsConnected] = useState(false);
  const [chunkErrors, setChunkErrors] = useState(0);
  const ws = useRef(null);
  const messageBuffer = useRef([]);

  const sendMessage = useCallback((msg) => {
    if (ws.current && ws.current.readyState === WebSocket.OPEN) {
      while (messageBuffer.current.length > 0) {
        const bufferedMsg = messageBuffer.current.shift();
        ws.current.send(JSON.stringify(bufferedMsg));
      }
      ws.current.send(JSON.stringify(msg));
    } else {
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
  const prevSpeedRef = useRef(0);
  const latestAccelerationRef = useRef({ x: 0, y: 0, z: 0 });
  const prevAccelerationRef = useRef({ x: 0, y: 0, z: 0 });
  const latestRotationRef = useRef({ x: 0, y: 0, z: 0 });
  const latestDurationRef = useRef(0);
  const triggerAlertRef = useRef(null);
  const lastAlertByTypeRef = useRef({});
  const MAX_EVENTS = 300;

  // Accumulate speed analysis from live GPS data
  useEffect(() => {
    if (!startTime || !gpsTracking.speed) return;
    const spd = gpsTracking.speed;
    const limit = speedLimit.currentSpeedLimit;
    setSpeedAnalysis(prev => {
      const total = prev.totalSpeedSamples + 1;
      const newSum = prev.speedSum + spd;
      const newSqSum = prev.speedSqSum + spd * spd;
      let speeding = prev.speedingSamples;
      let maxExc = prev.maxExcessKmh;
      let szViolations = prev.schoolZoneViolations;
      if (limit && spd > limit) {
        speeding += 1;
        const exc = spd - limit;
        if (exc > maxExc) maxExc = exc;
      }
      if (limit && spd > limit && speedLimit.zoneType === 'school') {
        szViolations += 1;
      }
      return {
        speedingSamples: speeding,
        totalSpeedSamples: total,
        maxExcessKmh: maxExc,
        schoolZoneViolations: szViolations,
        speedSum: newSum,
        speedSqSum: newSqSum,
      };
    });
  }, [gpsTracking.speed, startTime]);

  // Feedback criteria handler — preserves full metadata
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

  // Auto-flag when TelemetryPanel gauge hits red threshold
  const handleTelemetryThreshold = useCallback((info) => {
    const mapping = DEVICE_EVENT_TO_CODE[info.faultType];
    if (mapping) {
      handleFeedbackUpdate(mapping.code, 1, {
        ...mapping,
        timestamp: Date.now(),
        elapsed_seconds: latestDurationRef.current,
      });
    }

    setAutoFlagCounts(prev => ({
      ...prev,
      [info.faultType]: (prev[info.faultType] || 0) + 1,
    }));
  }, [handleFeedbackUpdate]);

  // Real-time feedback loop using evaluate-chunk endpoint — runs every 2 seconds.
  // IMPORTANT: only stable values (startTime, isUploading) are in the dep array.
  // Sensor state arrays change every ~1 s, which would reset the 2-second interval
  // before it ever fires. We read those values from refs instead (synced each render).
  useEffect(() => {
    if (!startTime || isUploading) return;

    const feedbackInterval = setInterval(async () => {
      if (!latestLocationRef.current || !rideId) return;

      const motionData = latestMotionDataRef.current.slice(-30); // 10Hz * 3s
      const speedData = latestSpeedDataRef.current.slice(-3);    // 1Hz * 3s

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

        const payload = {
          ride_id: rideId,
          location: loc,
          speed: latestSpeedRef.current,
          acceleration_data: accelerationWindow,
          current_speed_limit: latestSpeedLimitRef.current,
          zone_type: latestZoneTypeRef.current,
          timestamp: Date.now() / 1000,
        };

        const response = await client.post('/diagnostic-rides/evaluate-chunk', payload);
        const newEvents = response.data.events || [];

        // Reset chunk error counter on success
        setChunkErrors(0);

        if (newEvents.length > 0) {
          setLatestEvents(prev => [...newEvents, ...prev].slice(0, 8));
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

          // Update auto-detected flag counts for the summary banner
          setAutoFlagCounts(prev => {
            const updated = { ...prev };
            newEvents.forEach(event => {
              const key = event.type;
              updated[key] = (updated[key] || 0) + 1;
            });
            return updated;
          });

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
        const msg = error?.response?.data?.detail || error.message || 'Unknown error';
        console.error('Chunk evaluation error:', msg);
        setChunkErrors(prev => prev + 1);
      }
    }, 2000);

    return () => clearInterval(feedbackInterval);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [startTime, isUploading]);

  // Detailed alert messages with school zone detection, g-force values, etc.
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
    } else if (event.type === 'harsh_acceleration') {
      msg = 'Accelerating Too Hard';
      detail = `${event.value}g forward force. Apply throttle gradually.`;
      icon = 'rocket';
    } else if (event.type === 'erratic_speed') {
      msg = 'Erratic Speed Pattern';
      detail = `Speed oscillating. Maintain a steady pace.`;
      icon = 'pulse';
    }

    setAlertMessage({ msg, detail, icon });
    setTimeout(() => setAlertMessage(null), 5000);
  };

  // Init tracking with sensor cleanup on unmount
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

  // WebSocket connection with exponential backoff reconnection
  const reconnectTimeout = useRef(null);
  const reconnectAttempts = useRef(0);
  // Tracks whether the app is currently backgrounded.
  // Android kills TCP connections when backgrounded — this is expected.
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

      // Heartbeat ping every 30 seconds
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

      // If backgrounded, don't start a retry loop — AppState handler reconnects on foreground
      if (isInBackgroundRef.current) return;

      // Exponential backoff: 1s, 2s, 4s... up to 30s
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

  // AppState listener — persist buffer on background, reconnect on foreground
  const appStateRef = useRef(AppState.currentState);
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (nextState) => {
      const prev = appStateRef.current;
      appStateRef.current = nextState;

      if (nextState === 'background' && rideId) {
        isInBackgroundRef.current = true;
        const snapshot = syncBufferRef.current.slice(-200);
        AsyncStorage.setItem(`@ride_buffer_${rideId}`, JSON.stringify(snapshot)).catch(() => {});
      }

      if (prev !== 'active' && nextState === 'active' && rideId && !isUploading) {
        isInBackgroundRef.current = false;
        if (reconnectTimeout.current) clearTimeout(reconnectTimeout.current);
        reconnectAttempts.current = 0;
        connectWebSocket();
      }
    });
    return () => subscription.remove();
  }, [rideId, isUploading, connectWebSocket]);

  // Offline buffer / telemetry sync
  const [isOffline, setIsOffline] = useState(false);
  const syncBufferRef = useRef([]);
  const BUFFER_KEY_PREFIX = '@ride_buffer_';

  useEffect(() => {
    if (!rideId) return;

    // Stream telemetry every 1s
    const streamInterval = setInterval(() => {
      if (!latestLocationRef.current) return;
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

      sendMessage({
        type: 'telemetry',
        data: point,
      });

      syncBufferRef.current.push(point);
    }, 1000);

    // HTTP flush every 30 seconds
    const syncInterval = setInterval(async () => {
      if (syncBufferRef.current.length === 0) return;

      const chunk = [...syncBufferRef.current];
      syncBufferRef.current = [];

      try {
        await client.post(`/diagnostic-rides/${rideId}/telemetry`, chunk);
        setIsOffline(false);
      } catch (e) {
        console.warn('HTTP Sync failed, returning points to buffer:', e);
        syncBufferRef.current = [...chunk, ...syncBufferRef.current];
        setIsOffline(true);

        try {
          await AsyncStorage.setItem(`${BUFFER_KEY_PREFIX}${rideId}`, JSON.stringify(syncBufferRef.current));
        } catch (storageError) {
          console.error('Failed to persist buffer to storage:', storageError);
        }
      }
    }, 30000);

    // Deep flush every 5 minutes
    const deepFlushInterval = setInterval(async () => {
      if (syncBufferRef.current.length === 0) return;
      const chunk = [...syncBufferRef.current];
      syncBufferRef.current = [];
      try {
        await client.post(`/diagnostic-rides/${rideId}/telemetry`, chunk);
        setIsOffline(false);
      } catch (e) {
        console.warn('Deep flush failed, discarding chunk to free memory:', e.message);
        setIsOffline(true);
      }
    }, 300000);

    return () => {
      clearInterval(streamInterval);
      clearInterval(syncInterval);
      clearInterval(deepFlushInterval);
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rideId, sendMessage]);

  // Duration timer — separate useEffect
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
      case 'harsh_acceleration': return 'rocket';
      case 'erratic_speed': return 'pulse';
      case 'lane_weaving': return 'swap-horizontal';
      case 'friction_circle_violation': return 'warning';
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

  // Retry helper for flaky networks — tries up to `attempts` times with backoff.
  const retryRequest = async (fn, attempts = 3) => {
    for (let i = 0; i < attempts; i++) {
      try {
        return await fn();
      } catch (err) {
        const isLast = i === attempts - 1;
        const isNetworkErr =
          err.message?.includes('Network Error') ||
          err.code === 'ECONNABORTED';
        if (isLast || !isNetworkErr) throw err;
        const delay = 1000 * Math.pow(2, i);
        console.log(`Retry ${i + 1}/${attempts} in ${delay}ms...`);
        await new Promise(r => setTimeout(r, delay));
      }
    }
  };

  const PENDING_RIDE_KEY = '@pending_ride_payload';

  const submitRideData = async () => {
    setIsUploading(true);

    try {
      // Stop tracking locally
      const gpsData = await gpsTracking.stopTracking();
      const motionData = deviceMotion.stopTracking();
      const speedLimitData = speedLimit.getSpeedLimitData();

      const endTime = new Date();

      // Format coach notes
      const notes = coachNotesRef.current;
      let evaluatorNotes = null;
      if (notes.length > 0) {
        evaluatorNotes = notes.map(n => {
          const mins = Math.floor(n.elapsed_seconds / 60);
          const secs = n.elapsed_seconds % 60;
          return `[${mins}:${secs < 10 ? '0' : ''}${secs}] ${n.text}`;
        }).join('\n');
      }

      // Build sensor arrays from local refs
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

      const ridePayload = {
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
        await retryRequest(async () => {
          if (rideId) {
            await client.put(`/diagnostic-rides/${rideId}`, ridePayload, { timeout: 30000 });
          } else {
            const response = await client.post('/diagnostic-rides/', ridePayload, { timeout: 30000 });
            finalRideId = response.data.id;
          }
        });
      } catch (uploadError) {
        console.warn('Metadata upload failed, attempting to trigger evaluation anyway:', uploadError);
        if (!finalRideId) {
          // Save payload locally so it can be retried later
          try {
            await AsyncStorage.setItem(PENDING_RIDE_KEY, JSON.stringify(ridePayload));
            console.log('Ride payload saved to local storage for later retry');
          } catch (_) {}
          throw uploadError;
        }
      }

      try {
        await retryRequest(() =>
          client.post(`/diagnostic-rides/${finalRideId}/evaluate`, null, { timeout: 30000 })
        );
      } catch (evalError) {
        console.error('Evaluation trigger failed:', evalError);
        throw evalError;
      }

      // Clear any saved pending payload on success
      AsyncStorage.removeItem(PENDING_RIDE_KEY).catch(() => {});

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
      const isNetworkError =
        error.message?.includes('Network Error') ||
        error.code === 'ECONNABORTED';
      Alert.alert(
        'Submission Error',
        isNetworkError
          ? 'Network is unstable. Your ride data has been saved locally and will be retried automatically when connectivity improves.'
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

  // Use useEffect to update prev values only when new data actually arrives.
  // This ensures the delta used for jerk and acceleration is non-zero.
  useEffect(() => {
    if (gpsTracking.speed !== latestSpeedRef.current) {
      prevSpeedRef.current = latestSpeedRef.current;
      latestSpeedRef.current = gpsTracking.speed;
    }
  }, [gpsTracking.speed]);

  useEffect(() => {
    if (deviceMotion.acceleration !== latestAccelerationRef.current) {
      prevAccelerationRef.current = latestAccelerationRef.current;
      latestAccelerationRef.current = deviceMotion.acceleration;
    }
  }, [deviceMotion.acceleration]);

  latestRotationRef.current = deviceMotion.rotation;
  latestDurationRef.current = duration;

  const mapRegion = useMemo(() => ({
    latitude: gpsTracking.location?.latitude || 49.2827,
    longitude: gpsTracking.location?.longitude || -123.1207,
    latitudeDelta: 0.005,
    longitudeDelta: 0.005,
  }), [gpsTracking.location]);

  const mapMarkers = useMemo(() => [{
    id: 'current',
    coordinate: {
      latitude: gpsTracking.location?.latitude || 49.2827,
      longitude: gpsTracking.location?.longitude || -123.1207,
    },
    title: 'Current Position',
  }], [gpsTracking.location]);

  const mapPolylines = useMemo(() => [{
    id: 'route',
    coordinates: gpsTracking.routeCoordinates,
    strokeWidth: 4,
    strokeColor: '#15803D',
  }], [gpsTracking.routeCoordinates]);

  if (!gpsTracking.location) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#15803D" />
        <Text style={[styles.loadingText, { marginTop: 16 }]}>LOCKING GPS SIGNAL...</Text>
      </View>
    );
  }

  const canComplete = duration >= 60 && gpsTracking.distance >= 0.1;

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.mapContainer}>
        <OSMMap
          style={styles.map}
          region={mapRegion}
          markers={mapMarkers}
          polylines={mapPolylines}
          showPositionMarker
        />
        <View style={{ position: 'absolute', top: insets.top + 50, left: 12, backgroundColor: 'rgba(11, 19, 38, 0.6)', paddingHorizontal: 8, paddingVertical: 4, borderRadius: 8 }}>
          <Text style={{ color: '#94A3B8', fontSize: 9, fontWeight: '800', letterSpacing: 0.5 }}>OSM LIVE ENGINE</Text>
        </View>
      </View>

      {/* GUARDIAN DRIVE banner with shield-checkmark icon */}
      <View style={[styles.safetyBanner, { paddingTop: insets.top, height: 40 + insets.top }]}>
        <Ionicons name="shield-checkmark" size={18} color="white" />
        <Text style={styles.safetyText}>GUARDIAN DRIVE — ACTIVE DIAGNOSTIC</Text>
      </View>

      {/* Connection Status with offline states */}
      <View style={[styles.connectionStatus, { top: insets.top + 8 }]}>
        <View style={[styles.connectionDot, {
          backgroundColor: (isWsConnected && !isOffline) ? '#15803D' : (isOffline ? '#F59E0B' : '#EF4444')
        }]} />
        <Text style={[styles.connectionText, {
          color: (isWsConnected && !isOffline) ? '#15803D' : (isOffline ? '#F59E0B' : '#EF4444')
        }]}>
          {isWsConnected
            ? (isOffline ? 'BUFFERING DATA' : 'LIVE SYNC ACTIVE')
            : (isOffline ? 'OFFLINE BUFFERING' : 'SYNC OFFLINE')}
        </Text>
      </View>

      <View style={[styles.overlay, { top: 50 + insets.top }]}>
        {/* Live Telemetry Dashboard (includes ride stats) */}
        <TelemetryPanel
          acceleration={latestAccelerationRef.current}
          rotation={latestRotationRef.current}
          prevAcceleration={prevAccelerationRef.current}
          sampleIntervalMs={150} // Matches STATE_THROTTLE_MS in useDeviceMotion
          heading={gpsTracking.location?.heading}
          speed={latestSpeedRef.current}
          prevSpeed={prevSpeedRef.current}
          isActive={!!startTime}
          onThresholdExceeded={handleTelemetryThreshold}
          duration={duration}
          distance={gpsTracking.distance}
          speedLimit={speedLimit.currentSpeedLimit}
          zoneType={speedLimit.zoneType}
          roadName={speedLimit.roadName}
          altitude={gpsTracking.location?.altitude}
          schoolZoneViolations={speedAnalysis.schoolZoneViolations}
          speedingPercent={speedAnalysis.totalSpeedSamples > 0
            ? (speedAnalysis.speedingSamples / speedAnalysis.totalSpeedSamples) * 100
            : null}
          maxExcessKmh={speedAnalysis.maxExcessKmh}
          speedVariance={speedAnalysis.totalSpeedSamples > 2
            ? Math.sqrt(speedAnalysis.speedSqSum / speedAnalysis.totalSpeedSamples
                - Math.pow(speedAnalysis.speedSum / speedAnalysis.totalSpeedSamples, 2))
            : null}
        />

        {/* Auto-Detected Flags Summary */}
        {Object.keys(autoFlagCounts).length > 0 && (
          <View style={styles.autoFlagBanner}>
            <Text style={styles.autoFlagTitle}>Auto-Detected Issues</Text>
            <View style={styles.autoFlagRow}>
              {Object.entries(autoFlagCounts).map(([type, count]) => (
                <View key={type} style={styles.autoFlagChip}>
                  <Ionicons name={getEventIcon(type)} size={12} color="#F59E0B" />
                  <Text style={styles.autoFlagLabel}>
                    {type.replace(/_/g, ' ')}
                  </Text>
                  <View style={styles.autoFlagCountBadge}>
                    <Text style={styles.autoFlagCount}>{count}</Text>
                  </View>
                </View>
              ))}
            </View>
          </View>
        )}

        {/* Chunk Evaluation Error Indicator */}
        {chunkErrors >= 3 && (
          <View style={styles.chunkErrorBanner}>
            <Ionicons name="warning" size={14} color="#F59E0B" />
            <Text style={styles.chunkErrorText}>
              Analysis connection unstable ({chunkErrors} errors) — events may be missed
            </Text>
          </View>
        )}

        {/* Mistake Log */}
        {latestEvents.length > 0 && (
          <TouchableOpacity
            style={styles.eventLogContainer}
            onPress={() => setReportModalVisible(true)}
            activeOpacity={0.9}
          >
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
              <Text style={styles.eventLogTitle}>Critical Events</Text>
              <Text style={{ fontSize: 10, color: '#3B82F6' }}>View All ({allEvents.length})</Text>
            </View>
            <ScrollView style={{ maxHeight: 90 }}>
              {latestEvents.map((event, idx) => (
                <View key={idx} style={styles.eventItem}>
                  <Ionicons name={getEventIcon(event.type)} size={18} color={event.severity === 'high' ? "#EF4444" : "#F59E0B"} />
                  <Text style={styles.eventText}>{getEventDescription(event)}</Text>
                </View>
              ))}
            </ScrollView>
          </TouchableOpacity>
        )}

        {/* Supervisor Actions */}
        <View style={styles.actionRow}>
          <TouchableOpacity style={styles.actionButton} onPress={() => setFeedbackPanelVisible(true)}>
            <Ionicons name="flag" size={20} color="#EF4444" />
            <Text style={[styles.actionButtonText, { color: '#EF4444' }]}>Flag {feedbackBadgeCount > 0 ? `(${feedbackBadgeCount})` : ''}</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.actionButton} onPress={() => setNoteModalVisible(true)}>
            <Ionicons name="create" size={20} color="#3B82F6" />
            <Text style={[styles.actionButtonText, { color: '#3B82F6' }]}>Note {coachNotesRef.current.length > 0 ? `(${coachNotesRef.current.length})` : ''}</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.sensorIndicators}>
          <View style={styles.indicator}>
            <View style={[styles.indicatorDot, { backgroundColor: '#15803D' }]} />
            <Text style={styles.indicatorText}>GPS Active</Text>
          </View>
          <View style={styles.indicator}>
            <View style={[styles.indicatorDot, { backgroundColor: '#15803D' }]} />
            <Text style={styles.indicatorText}>Motion: {deviceMotion.data.length} samples</Text>
          </View>
          <View style={styles.indicator}>
            <View style={[styles.indicatorDot, {
              backgroundColor: speedLimit.currentSpeedLimit ? '#15803D' : '#F59E0B'
            }]} />
            <Text style={styles.indicatorText}>
              {speedLimit.isLoading ? 'Fetching limit...' :
               speedLimit.currentSpeedLimit ? 'Speed limit active' : 'No limit data'}
            </Text>
          </View>
        </View>

        {/* Real-time Alert Banner */}
        {alertMessage && (
          <View style={[styles.alertBanner, speedLimit.zoneType === 'school' && styles.schoolAlertBanner]}>
            <Ionicons name={alertMessage.icon || 'warning'} size={24} color="white" />
            <View style={styles.alertContent}>
              <Text style={styles.alertText}>{alertMessage.msg}</Text>
              <Text style={styles.alertDetail}>{alertMessage.detail}</Text>
            </View>
          </View>
        )}

        <Button
          mode="contained"
          onPress={handleCompleteRide}
          disabled={!canComplete || isUploading}
          loading={isUploading}
          style={[styles.completeButton, (!canComplete || isUploading) && styles.completeButtonDisabled]}
          contentStyle={{ height: 64 }}
          labelStyle={{ fontSize: 18, fontWeight: '800' }}
          accessibilityLabel={canComplete ? "Complete Diagnostic Ride" : "Keep driving to meet requirements"}
          accessibilityRole="button"
        >
          {canComplete ? 'COMPLETE RIDE' : 'MINIMUM REQUIREMENTS'}
        </Button>
      </View>

      <GForceOverlay
        acceleration={deviceMotion.acceleration}
        isActive={!!startTime}
      />

      <FeedbackPanel
        visible={feedbackPanelVisible}
        onClose={() => setFeedbackPanelVisible(false)}
        feedbackCounts={feedbackCountsRef.current}
        onUpdateCount={handleFeedbackUpdate}
        elapsedSeconds={duration}
      />

      <Portal>
        <Dialog visible={reportModalVisible} onDismiss={() => setReportModalVisible(false)} style={{ backgroundColor: '#131B2E', borderRadius: 24, maxHeight: '80%' }}>
          <Dialog.Title style={{ color: 'white' }}>Performance Feed</Dialog.Title>
          <Dialog.ScrollArea>
            <ScrollView contentContainerStyle={{ paddingVertical: 16 }}>
              {allEvents.length > 0 ? (
                allEvents.map((event, idx) => (
                  <View key={idx} style={[styles.eventItem, { marginBottom: 12, paddingBottom: 12, borderBottomWidth: 1, borderBottomColor: '#1E293B' }]}>
                    <Ionicons name={getEventIcon(event.type)} size={24} color={event.severity === 'high' ? "#EF4444" : "#F59E0B"} />
                    <View style={{ flex: 1, marginLeft: 12 }}>
                      <Text style={{ fontWeight: '800', color: 'white', fontSize: 14 }}>{event.type.replace('_', ' ').toUpperCase()}</Text>
                      <Text style={{ color: '#94A3B8', fontSize: 12 }}>{getEventDescription(event)}</Text>
                      <Text style={{ fontSize: 10, color: '#64748B', marginTop: 2 }}>
                        {new Date(event.timestamp * 1000).toLocaleTimeString()}
                      </Text>
                    </View>
                  </View>
                ))
              ) : (
                <Text style={{ textAlign: 'center', color: '#94A3B8', padding: 20 }}>No events recorded yet.</Text>
              )}
            </ScrollView>
          </Dialog.ScrollArea>
          <Dialog.Actions>
            <Button onPress={() => setReportModalVisible(false)}>CLOSE</Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>

      <Modal visible={noteModalVisible} transparent animationType="slide" onRequestClose={() => setNoteModalVisible(false)}>
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <Text style={styles.modalTitle}>Add Coach Note</Text>
            <Text style={styles.modalSubtitle}>Record an observation for the student</Text>
            <TextInput
              style={styles.noteInput}
              placeholder="e.g. Needs to check mirrors more often..."
              placeholderTextColor="#64748B"
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
  container: { flex: 1, backgroundColor: '#0B1326' },
  safetyBanner: {
    backgroundColor: 'rgba(30, 41, 59, 0.8)',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    position: 'absolute',
    top: 0, left: 0, right: 0,
    zIndex: 10,
    gap: 8,
  },
  safetyText: { color: 'white', fontSize: 11, fontWeight: '800', letterSpacing: 1 },
  mapContainer: { ...StyleSheet.absoluteFillObject, backgroundColor: '#131B2E' },
  map: { ...StyleSheet.absoluteFillObject },
  alertBanner: {
    backgroundColor: 'rgba(239, 68, 68, 0.9)',
    marginHorizontal: 16,
    position: 'absolute',
    bottom: 110,
    left: 0, right: 0,
    zIndex: 20,
    padding: 16,
    borderRadius: 20,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  schoolAlertBanner: { backgroundColor: 'rgba(245, 158, 11, 0.9)', borderWidth: 2, borderColor: '#fff' },
  alertContent: { flex: 1 },
  alertText: { color: 'white', fontWeight: '800', fontSize: 16 },
  alertDetail: { color: 'rgba(255, 255, 255, 0.8)', fontSize: 13, marginTop: 2 },
  loadingContainer: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#0B1326' },
  loadingText: { fontSize: 16, color: '#94A3B8', fontWeight: '600' },
  overlay: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, pointerEvents: 'box-none' },
  connectionStatus: {
    position: 'absolute',
    right: 16,
    zIndex: 100,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(19, 27, 46, 0.9)',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
  },
  connectionDot: { width: 6, height: 6, borderRadius: 3, marginRight: 8 },
  connectionText: { fontSize: 10, fontWeight: '800', letterSpacing: 0.5 },
  eventLogContainer: { backgroundColor: 'rgba(19, 27, 46, 0.8)', marginHorizontal: 16, marginTop: 12, borderRadius: 24, padding: 16 },
  eventLogTitle: { fontSize: 12, fontWeight: '800', color: '#94A3B8', marginBottom: 12, textTransform: 'uppercase' },
  eventItem: { flexDirection: 'row', alignItems: 'center', gap: 10, marginBottom: 8 },
  eventText: { flex: 1, color: 'white', fontSize: 13, fontWeight: '600' },
  autoFlagBanner: { backgroundColor: 'rgba(19, 27, 46, 0.85)', marginHorizontal: 16, marginTop: 10, borderRadius: 16, padding: 12 },
  autoFlagTitle: { fontSize: 11, fontWeight: '800', color: '#94A3B8', textTransform: 'uppercase', marginBottom: 8 },
  autoFlagRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  autoFlagChip: { flexDirection: 'row', alignItems: 'center', backgroundColor: 'rgba(245, 158, 11, 0.15)', borderRadius: 12, paddingHorizontal: 8, paddingVertical: 4, gap: 4 },
  autoFlagLabel: { color: '#CBD5E1', fontSize: 11, fontWeight: '600', textTransform: 'capitalize' },
  autoFlagCountBadge: { backgroundColor: '#F59E0B', borderRadius: 8, minWidth: 18, height: 18, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 4 },
  autoFlagCount: { color: '#0F172A', fontSize: 11, fontWeight: '900' },
  chunkErrorBanner: { flexDirection: 'row', alignItems: 'center', gap: 6, marginHorizontal: 16, marginTop: 8, backgroundColor: 'rgba(245, 158, 11, 0.15)', borderRadius: 10, paddingHorizontal: 10, paddingVertical: 6 },
  chunkErrorText: { color: '#F59E0B', fontSize: 11, fontWeight: '600', flex: 1 },
  actionRow: { flexDirection: 'row', marginHorizontal: 16, marginTop: 16, gap: 12 },
  actionButton: { flex: 1, height: 56, backgroundColor: '#131B2E', borderRadius: 20, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  actionButtonText: { fontWeight: '800', fontSize: 14 },
  sensorIndicators: { flexDirection: 'row', marginHorizontal: 16, marginTop: 16, gap: 16, flexWrap: 'wrap' },
  indicator: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  indicatorDot: { width: 8, height: 8, borderRadius: 4 },
  indicatorText: { color: '#64748B', fontSize: 11, fontWeight: '700' },
  completeButton: { position: 'absolute', bottom: 30, left: 16, right: 16, borderRadius: 20, backgroundColor: '#15803D' },
  completeButtonDisabled: { backgroundColor: '#1E293B', opacity: 0.5 },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(11, 19, 38, 0.8)', justifyContent: 'flex-end' },
  modalContent: { backgroundColor: '#131B2E', borderTopLeftRadius: 32, borderTopRightRadius: 32, padding: 24, paddingBottom: 40 },
  modalTitle: { fontSize: 22, fontWeight: '800', color: 'white', marginBottom: 4 },
  modalSubtitle: { fontSize: 13, color: '#94A3B8', marginBottom: 16 },
  noteInput: { backgroundColor: 'rgba(255,255,255,0.05)', borderRadius: 16, padding: 16, color: 'white', fontSize: 16, minHeight: 120, textAlignVertical: 'top', marginTop: 16 },
  modalButtons: { flexDirection: 'row', justifyContent: 'flex-end', marginTop: 24, gap: 12 },
  modalCancelBtn: { paddingVertical: 10, paddingHorizontal: 20, borderRadius: 8 },
  modalCancelText: { color: '#94A3B8', fontSize: 15, fontWeight: '600' },
  modalSaveBtn: { backgroundColor: '#15803D', paddingVertical: 10, paddingHorizontal: 20, borderRadius: 8 },
  modalSaveBtnDisabled: { backgroundColor: '#1E293B' },
  modalSaveText: { color: '#fff', fontSize: 15, fontWeight: 'bold' },
});

export default DiagnosticRideActiveScreen;
