import React, { useState, useEffect, useContext } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Alert,
  SafeAreaView,
} from 'react-native';
import MapView, { Polyline, Marker } from 'react-native-maps';
import Ionicons from 'react-native-vector-icons/Ionicons';
import useGPSTracking from '../hooks/useGPSTracking';
import useDeviceMotion from '../hooks/useDeviceMotion';
import useSpeedLimit from '../hooks/useSpeedLimit';
import { AuthContext } from '../context/AuthContext';
import client from '../api/client';

const DiagnosticRideActiveScreen = ({ route, navigation }) => {
  const { rideType, parentName, instructorId } = route.params;
  const { userToken } = useContext(AuthContext);

  const [startTime, setStartTime] = useState(null);
  const [duration, setDuration] = useState(0); // seconds
  const [isUploading, setIsUploading] = useState(false);
  const [latestEvents, setLatestEvents] = useState([]);
  const [alertMessage, setAlertMessage] = useState(null);

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

        const response = await client.post('/diagnostic-rides/live-evaluate', {
          acceleration_window: accelerationWindow,
          speed_window: speedWindow,
          rotation_window: []
        });

        if (response.data.events && response.data.events.length > 0) {
          const newEvents = response.data.events;
          setLatestEvents(prev => [...newEvents, ...prev].slice(0, 8));

          const highSev = newEvents.find(e => e.severity === 'high');
          if (highSev) {
            triggerAlert(highSev);
          }
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

      setStartTime(new Date());
    };

    initTracking();

    return () => {
      gpsTracking.stopTracking();
      deviceMotion.stopTracking();
    };
  }, []);

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

      const rideData = {
        ride_type:
          rideType === 'parent'
            ? 'parent_supervised'
            : 'instructor_supervised',
        instructor_id: instructorId,
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
      };

      const response = await client.post('/diagnostic-rides/', rideData);
      const rideId = response.data.id;
      await client.post(`/diagnostic-rides/${rideId}/evaluate`);

      Alert.alert('Success', 'Ride submitted! Evaluating your performance...', [
        {
          text: 'View Results',
          onPress: () => navigation.replace('DiagnosticRideResults', { rideId }),
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
    <SafeAreaView style={styles.container}>
      {/* Safety Banner */}
      <View style={styles.safetyBanner}>
        <Ionicons name="hand-right" size={20} color="white" />
        <Text style={styles.safetyText}>DRIVER SAFETY MODE — OPERATE BY SUPERVISOR ONLY</Text>
      </View>

      <MapView
        style={styles.map}
        region={{
          latitude: gpsTracking.location.latitude,
          longitude: gpsTracking.location.longitude,
          latitudeDelta: 0.01,
          longitudeDelta: 0.01,
        }}
      >
        <Marker
          coordinate={{
            latitude: gpsTracking.location.latitude,
            longitude: gpsTracking.location.longitude,
          }}
          title="Current Position"
        />
        <Polyline
          coordinates={gpsTracking.routeCoordinates}
          strokeWidth={4}
          strokeColor="#007bff"
        />
      </MapView>

      {/* Real-time Alert Banner */}
      {alertMessage && (
        <View style={[
          styles.alertBanner,
          speedLimit.zoneType === 'school' && styles.schoolAlertBanner
        ]}>
          <Ionicons name={alertMessage.icon || 'warning'} size={24} color="white" />
          <View style={styles.alertContent}>
            <Text style={styles.alertText}>{alertMessage.msg}</Text>
            <Text style={styles.alertDetail}>{alertMessage.detail}</Text>
          </View>
        </View>
      )}

      <View style={styles.overlay}>
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

        <TouchableOpacity
            style={styles.noteButton}
            onPress={() => Alert.prompt("Add Coach Note", "Record an observation for the student.", (text) => {
                Alert.alert("Note Saved", "Observation recorded successfully.");
            })}
        >
            <Ionicons name="chatbox-ellipses" size={24} color="#007bff" />
            <Text style={styles.noteButtonText}>Add Coach Note</Text>
        </TouchableOpacity>

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
    paddingVertical: 8,
    gap: 10,
  },
  safetyText: {
    color: 'white',
    fontSize: 11,
    fontWeight: 'bold',
    letterSpacing: 0.5,
  },
  map: {
    flex: 1,
  },
  alertBanner: {
    position: 'absolute',
    top: 60,
    left: 16,
    right: 16,
    backgroundColor: 'rgba(214, 48, 49, 0.95)',
    padding: 16,
    borderRadius: 12,
    zIndex: 1000,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 10,
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
    fontSize: 18,
  },
  alertDetail: {
    color: 'rgba(255, 255, 255, 0.9)',
    fontSize: 14,
    marginTop: 2,
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
    margin: 12,
    gap: 6,
  },
  statBox: {
    flex: 1,
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 10,
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
    fontSize: 11,
    color: '#6c757d',
    marginBottom: 2,
  },
  statValue: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#1a1a1a',
  },
  statTarget: {
    fontSize: 9,
    color: '#6c757d',
    marginTop: 2,
  },
  schoolBadge: {
    backgroundColor: '#ff9800',
    borderRadius: 4,
    paddingHorizontal: 6,
    paddingVertical: 2,
    marginTop: 2,
  },
  schoolBadgeText: {
    color: 'white',
    fontSize: 8,
    fontWeight: 'bold',
  },
  eventLogContainer: {
    backgroundColor: 'rgba(255, 255, 255, 0.95)',
    marginHorizontal: 12,
    marginBottom: 8,
    borderRadius: 12,
    padding: 12,
    borderWidth: 1,
    borderColor: '#eee',
    maxHeight: 180,
  },
  eventLogTitle: {
    fontSize: 12,
    fontWeight: 'bold',
    color: '#636e72',
    marginBottom: 8,
    textTransform: 'uppercase',
  },
  eventItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 6,
  },
  eventText: {
    fontSize: 12,
    color: '#2d3436',
    fontWeight: '500',
    flex: 1,
  },
  noteButton: {
    backgroundColor: 'white',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginHorizontal: 12,
    marginBottom: 8,
    padding: 10,
    borderRadius: 12,
    gap: 10,
    borderWidth: 1,
    borderColor: '#eee',
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  noteButtonText: {
    color: '#007bff',
    fontWeight: 'bold',
    fontSize: 14,
  },
  sensorIndicators: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 8,
    marginHorizontal: 12,
    flexWrap: 'wrap',
  },
  indicator: {
    flexDirection: 'row',
    backgroundColor: '#fff',
    borderRadius: 20,
    paddingHorizontal: 10,
    paddingVertical: 5,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
  },
  indicatorDot: {
    fontSize: 12,
    color: '#28a745',
    marginRight: 4,
  },
  indicatorText: {
    fontSize: 11,
    color: '#1a1a1a',
  },
  completeButton: {
    position: 'absolute',
    bottom: 32,
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
});

export default DiagnosticRideActiveScreen;
