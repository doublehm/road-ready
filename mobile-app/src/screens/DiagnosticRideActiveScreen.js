import React, { useState, useEffect, useContext, useRef, useCallback } from 'react';
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
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import OSMMap from '../components/OSMMap';
import Ionicons from 'react-native-vector-icons/Ionicons';
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
  const [rideId, setRideId] = useState(null);
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

  // Real-time feedback loop
  useEffect(() => {
    if (!startTime || isUploading) return;

    const feedbackInterval = setInterval(async () => {
      const motionData = deviceMotion.data.slice(-30); // 10Hz * 3s
      const speedData = gpsTracking.speedData.slice(-3); // 1Hz * 3s

      if (motionData.length < 10 || speedData.length < 1) return;

      try {
        const accelerationWindow = motionData.map(p => ({
          timestamp: p.timestamp,
          x: p.acceleration.x,
          y: p.acceleration.y,
          z: p.acceleration.z,
          latitude: gpsTracking.location?.latitude,
          longitude: gpsTracking.location?.longitude
        }));

        const speedWindow = speedData.map(p => ({
          timestamp: p.timestamp,
          speed: p.speed,
          latitude: p.latitude,
          longitude: p.longitude,
          speed_limit: speedLimit.currentSpeedLimit || 0,
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

          // Auto-map sensor events to feedback criteria (F-codes)
          newEvents.forEach(event => {
            const mapping = DEVICE_EVENT_TO_CODE[event.type];
            if (mapping) {
              handleFeedbackUpdate(mapping.code, 1, {
                ...mapping,
                timestamp: Date.now(),
                elapsed_seconds: duration,
              });
            }
          });

          // Trigger alert for any detected event (high or medium severity)
          const alertEvent = newEvents.find(e => e.severity === 'high') ||
                             newEvents.find(e => e.severity === 'medium');
          if (alertEvent) {
            triggerAlert(alertEvent);
          }

          // Stream events to backend for live dashboard
          newEvents.forEach(event => {
            sendMessage({
              type: 'event',
              data: {
                ...event,
                timestamp: Date.now() / 1000,
                location: gpsTracking.location ? {
                  latitude: gpsTracking.location.latitude,
                  longitude: gpsTracking.location.longitude,
                } : null
              }
            });
          });
        }
      } catch (error) {
        console.error('Live evaluation error:', error);
      }
    }, 2000);

    return () => clearInterval(feedbackInterval);
  }, [startTime, isUploading, deviceMotion.data, gpsTracking.speedData, speedLimit.currentSpeedLimit]);

  const triggerAlert = (event) => {
    let msg = '';
    let detail = '';
    let icon = 'warning';

    if (event.type === 'speeding') {
      msg = 'Reduce Speed';
      const limit = event.limit || speedLimit.currentSpeedLimit || '?';
      const speed = Math.round(event.value || gpsTracking.speed);
      const excess = Math.round(speed - limit);
      detail = `${speed} km/h in a ${limit} km/h zone (+${excess} km/h over)`;
      if (speedLimit.zoneType === 'school') {
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
  useEffect(() => {
    if (!rideId) return;

    const wsBase = client.defaults.baseURL.replace('http', 'ws');
    const wsUrl = `${wsBase}/live-ride-stream?ride_id=${rideId}&client_type=mobile`;
    
    console.log('Connecting to WebSocket:', wsUrl);
    ws.current = new WebSocket(wsUrl);

    ws.current.onopen = () => {
      console.log('WebSocket connected');
      ws.current.send(JSON.stringify({ type: 'ping' }));
      
      // Flush buffered messages
      while (messageBuffer.current.length > 0) {
        const bufferedMsg = messageBuffer.current.shift();
        ws.current.send(JSON.stringify(bufferedMsg));
      }
    };

    ws.current.onmessage = (e) => {
      try {
        const data = JSON.parse(e.data);
        if (data.type === 'pong') console.log('WS Pong');
      } catch (err) {
        console.error('WS Message Parse Error:', err);
      }
    };

    ws.current.onerror = (e) => console.error('WebSocket error:', e.message);
    ws.current.onclose = (e) => console.log('WebSocket closed:', e.code, e.reason);

    return () => {
      if (ws.current) ws.current.close();
    };
  }, [rideId]);

  // Telemetry Streaming Effect
  useEffect(() => {
    if (!rideId || !gpsTracking.location) return;

    const streamInterval = setInterval(() => {
      sendMessage({
        type: 'telemetry',
        data: {
          acceleration: deviceMotion.acceleration,
          speed: gpsTracking.speed,
          location: {
            latitude: gpsTracking.location.latitude,
            longitude: gpsTracking.location.longitude,
          },
          heading: gpsTracking.location.heading,
          timestamp: Date.now() / 1000,
        }
      });
    }, 2000);

    return () => clearInterval(streamInterval);
  }, [rideId, deviceMotion.acceleration, gpsTracking.speed, gpsTracking.location, sendMessage]);

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

    if (durationMinutes < 20) {
      Alert.alert(
        'Too Short',
        'Diagnostic ride must be at least 20 minutes long. Continue driving or cancel.',
        [{ text: 'OK' }]
      );
      return;
    }

    if (gpsTracking.distance < 5) {
      Alert.alert(
        'Too Short',
        'Diagnostic ride must cover at least 5 km. Continue driving or cancel.',
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
      const gpsData = gpsTracking.stopTracking();
      const motionData = deviceMotion.stopTracking();
      const speedLimitData = speedLimit.getSpeedLimitData();

      const endTime = new Date();

      const accelerationData = motionData.map((point) => ({
        timestamp: point.timestamp,
        x: point.acceleration.x,
        y: point.acceleration.y,
        z: point.acceleration.z,
      }));

      const rotationData = motionData.map((point) => ({
        timestamp: point.timestamp,
        x: point.rotation.x,
        y: point.rotation.y,
        z: point.rotation.z,
      }));

      // Extract heading data from speed data points
      const headingData = gpsData.speedData
        .filter(p => p.heading !== undefined && p.heading !== null)
        .map(p => ({
          timestamp: p.timestamp,
          heading: p.heading,
          latitude: p.latitude,
          longitude: p.longitude,
        }));

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
        acceleration_data: JSON.stringify(accelerationData),
        rotation_data: JSON.stringify(rotationData),
        speed_data: JSON.stringify(gpsData.speedData),
        speed_limit_data: JSON.stringify(speedLimitData),
        heading_data: JSON.stringify(headingData),
        evaluator_notes: evaluatorNotes,
        human_feedback: JSON.stringify(
          Object.values(feedbackCountsRef.current).filter(item => item.count > 0)
        ),
      };

      let finalRideId = rideId;
      if (rideId) {
        await client.put(`/diagnostic-rides/${rideId}`, rideData);
      } else {
        const response = await client.post('/diagnostic-rides/', rideData);
        finalRideId = response.data.id;
      }

      await client.post(`/diagnostic-rides/${finalRideId}/evaluate`);

      // Auto-complete the booking if this ride was linked to one
      if (bookingId) {
        try {
          await client.post(`/bookings/${bookingId}/complete`);
        } catch (e) {
          console.log('Could not auto-complete booking:', e);
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
      Alert.alert(
        'Error',
        'Failed to submit ride data. Please try again or contact support.'
      );
    } finally {
      setIsUploading(false);
    }
  };

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
        region={{
          latitude: gpsTracking.location.latitude,
          longitude: gpsTracking.location.longitude,
        }}
        markers={[
          {
            latitude: gpsTracking.location.latitude,
            longitude: gpsTracking.location.longitude,
            title: "Current Position"
          }
        ]}
        polylines={[
          {
            coordinates: gpsTracking.routeCoordinates,
            strokeWidth: 4,
            strokeColor: "#007bff"
          }
        ]}
      />

      {/* Debug Indicator */}
      <View style={{ position: 'absolute', top: 10, left: 10, backgroundColor: 'rgba(0,0,0,0.6)', padding: 4, borderRadius: 4, zIndex: 9999 }}>
        <Text style={{ color: '#fff', fontSize: 10, fontWeight: 'bold' }}>OSM MAP ACTIVE v2</Text>
      </View>

      <View style={[styles.overlay, { top: 80 }]}>
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
          <View style={styles.statBox}>
            <Text style={styles.statLabel}>Time</Text>
            <Text style={styles.statValue}>{formatTime(duration)}</Text>
            <Text style={styles.statTarget}>Target: 20:00</Text>
          </View>

          <View style={styles.statBox}>
            <Text style={styles.statLabel}>Distance</Text>
            <Text style={styles.statValue}>
              {gpsTracking.distance.toFixed(2)} km
            </Text>
            <Text style={styles.statTarget}>Target: 5.0 km</Text>
          </View>

          <View style={styles.statBox}>
            <Text style={styles.statLabel}>Speed</Text>
            <Text style={[styles.statValue, { color: getSpeedColor() }]}>
              {gpsTracking.speed.toFixed(0)} km/h
            </Text>
          </View>

          <View style={[styles.statBox, styles.limitBox]}>
            <Text style={styles.statLabel}>Limit</Text>
            <Text style={styles.statValue}>
              {speedLimit.currentSpeedLimit || '--'}
            </Text>
            {speedLimit.zoneType === 'school' && (
              <View style={styles.schoolBadge}>
                <Text style={styles.schoolBadgeText}>SCHOOL</Text>
              </View>
            )}
            {speedLimit.zoneType !== 'school' && speedLimit.roadType && (
              <Text style={styles.statTarget}>
                {speedLimit.roadType}
              </Text>
            )}
          </View>
        </View>

        {/* Mistake Log */}
        {latestEvents.length > 0 && (
          <View style={styles.eventLogContainer}>
            <Text style={styles.eventLogTitle}>Recent Events</Text>
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
          </View>
        )}

        {/* Supervisor Actions */}
        <View style={styles.actionRow}>
          <TouchableOpacity
              style={[styles.actionButton, styles.feedbackButton]}
              onPress={() => setFeedbackPanelVisible(true)}
          >
              <Ionicons name="flag" size={20} color="#e17055" />
              <Text style={styles.feedbackButtonText}>
                Flag{feedbackBadgeCount > 0 ? ` (${feedbackBadgeCount})` : ''}
              </Text>
              {feedbackBadgeCount > 0 && (
                <View style={styles.feedbackBadge}>
                  <Text style={styles.feedbackBadgeText}>{feedbackBadgeCount}</Text>
                </View>
              )}
          </TouchableOpacity>

          <TouchableOpacity
              style={[styles.actionButton, styles.noteButton]}
              onPress={() => setNoteModalVisible(true)}
          >
              <Ionicons name="chatbox-ellipses" size={20} color="#007bff" />
              <Text style={styles.noteButtonText}>
                Note{coachNotesRef.current.length > 0 ? ` (${coachNotesRef.current.length})` : ''}
              </Text>
          </TouchableOpacity>
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

        <TouchableOpacity
          style={[
            styles.completeButton,
            !canComplete && styles.completeButtonDisabled,
            isUploading && styles.completeButtonDisabled,
          ]}
          onPress={handleCompleteRide}
          disabled={!canComplete || isUploading}
        >
          <Text style={styles.completeButtonText}>
            {isUploading
              ? 'Submitting...'
              : canComplete
              ? 'Complete Ride'
              : 'Keep Driving'}
          </Text>
          {!canComplete && (
            <Text style={styles.completeButtonSubtext}>
              {duration < 60 * 20
                ? `${Math.ceil(20 - duration / 60)} min remaining`
                : `${(5 - gpsTracking.distance).toFixed(1)} km remaining`}
            </Text>
          )}
        </TouchableOpacity>
      </View>
      {/* Feedback Panel */}
      <FeedbackPanel
        visible={feedbackPanelVisible}
        onClose={() => setFeedbackPanelVisible(false)}
        feedbackCounts={feedbackCountsRef.current}
        onUpdateCount={handleFeedbackUpdate}
        elapsedSeconds={duration}
      />

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
    backgroundColor: '#28a745',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.2,
    shadowRadius: 8,
    elevation: 5,
  },
  completeButtonDisabled: {
    backgroundColor: '#6c757d',
  },
  completeButtonText: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#fff',
  },
  completeButtonSubtext: {
    fontSize: 14,
    color: '#fff',
    marginTop: 4,
    opacity: 0.9,
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
