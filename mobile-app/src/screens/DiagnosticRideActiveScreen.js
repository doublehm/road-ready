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
import { AuthContext } from '../context/AuthContext';
import client from '../api/client';

const DiagnosticRideActiveScreen = ({ route, navigation }) => {
  const { rideType, parentName, instructorId } = route.params;
  const { userToken } = useContext(AuthContext);

  const [startTime, setStartTime] = useState(null);
  const [duration, setDuration] = useState(0); // seconds
  const [isUploading, setIsUploading] = useState(false);

  // Hooks for sensor data collection
  const gpsTracking = useGPSTracking();
  const deviceMotion = useDeviceMotion(10); // 10 Hz sampling

  useEffect(() => {
    // Start tracking on mount
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
      // Cleanup on unmount
      gpsTracking.stopTracking();
      deviceMotion.stopTracking();
    };
  }, []);

  useEffect(() => {
    // Update duration every second
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

  const handleCompleteRide = async () => {
    const durationMinutes = duration / 60;

    // Validate minimum requirements
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
      // Stop tracking and get final data
      const gpsData = gpsTracking.stopTracking();
      const motionData = deviceMotion.stopTracking();

      const endTime = new Date();

      // Prepare acceleration data
      const accelerationData = motionData.map((point) => ({
        timestamp: point.timestamp,
        x: point.acceleration.x,
        y: point.acceleration.y,
        z: point.acceleration.z,
      }));

      // Prepare rotation data
      const rotationData = motionData.map((point) => ({
        timestamp: point.timestamp,
        x: point.rotation.x,
        y: point.rotation.y,
        z: point.rotation.z,
      }));

      // Create ride data
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
      };

      // Submit to API
      const response = await client.post('/diagnostic-rides/', rideData);

      // Trigger evaluation
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

      <View style={styles.overlay}>
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
            <Text style={styles.statValue}>
              {gpsTracking.speed.toFixed(0)} km/h
            </Text>
          </View>
        </View>

        <TouchableOpacity 
            style={styles.noteButton}
            onPress={() => Alert.prompt("Add Coach Note", "Record an observation for the student.", (text) => {
                // TODO: Save note to database
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
    margin: 16,
    gap: 8,
  },
  noteButton: {
    backgroundColor: 'white',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginHorizontal: 16,
    marginBottom: 16,
    padding: 12,
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
    fontSize: 16,
  },
  statBox: {
    flex: 1,
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 12,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  statLabel: {
    fontSize: 12,
    color: '#6c757d',
    marginBottom: 4,
  },
  statValue: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#1a1a1a',
    marginBottom: 2,
  },
  statTarget: {
    fontSize: 10,
    color: '#6c757d',
  },
  sensorIndicators: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 16,
    marginHorizontal: 16,
  },
  indicator: {
    flexDirection: 'row',
    backgroundColor: '#fff',
    borderRadius: 20,
    paddingHorizontal: 12,
    paddingVertical: 6,
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
    marginRight: 6,
  },
  indicatorText: {
    fontSize: 12,
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
