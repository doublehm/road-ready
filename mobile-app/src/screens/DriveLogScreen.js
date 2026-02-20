import React, { useState, useEffect, useContext } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Alert, ActivityIndicator } from 'react-native';
import OSMMap from '../components/OSMMap';
import * as Location from 'expo-location';
import { AuthContext } from '../context/AuthContext';
import client from '../api/client';

const DriveLogScreen = ({ navigation }) => {
  const [location, setLocation] = useState(null);
  const [isTracking, setIsTracking] = useState(false);
  const [routeCoordinates, setRouteCoordinates] = useState([]);
  const [distance, setDistance] = useState(0);
  const [startTime, setStartTime] = useState(null);
  const [duration, setDuration] = useState(0);
  const [subscription, setSubscription] = useState(null);

  const { userToken } = useContext(AuthContext);

  useEffect(() => {
    (async () => {
      let { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        Alert.alert('Permission to access location was denied');
        return;
      }

      let location = await Location.getCurrentPositionAsync({});
      setLocation(location.coords);
    })();
  }, []);

  useEffect(() => {
    let interval;
    if (isTracking) {
      interval = setInterval(() => {
        setDuration(prev => prev + 1);
      }, 1000);
    } else {
      clearInterval(interval);
    }
    return () => clearInterval(interval);
  }, [isTracking]);

  const startDrive = async () => {
    Alert.alert(
      "Safety Disclaimer",
      "By starting this session, you acknowledge that Road Ready is a technology marketplace, not a driving school. You are responsible for all on-road safety and insurance compliance. Road Ready is not liable for any incidents during this drive.",
      [
        { text: "Cancel", style: "cancel" },
        { 
          text: "I Agree & Start", 
          onPress: async () => {
            setIsTracking(true);
            setStartTime(new Date());
            setRouteCoordinates([]);
            setDistance(0);
            setDuration(0);

            const sub = await Location.watchPositionAsync(
              {
                accuracy: Location.Accuracy.High,
                timeInterval: 1000,
                distanceInterval: 10,
              },
              (loc) => {
                const { latitude, longitude } = loc.coords;
                const newCoordinate = { latitude, longitude };
                
                setRouteCoordinates(prev => {
                  return [...prev, newCoordinate];
                });
                setLocation(loc.coords);
              }
            );
            setSubscription(sub);
          }
        }
      ]
    );
  };

  const stopDrive = async () => {
    setIsTracking(false);
    if (subscription) {
      subscription.remove();
      setSubscription(null);
    }

    const endTime = new Date();
    const durationMinutes = duration / 60;
    
    // Save to API
    try {
      const logData = {
        start_time: startTime.toISOString(),
        end_time: endTime.toISOString(),
        duration_minutes: durationMinutes,
        distance_km: routeCoordinates.length * 0.01, // Mock distance calc
        route_coords: JSON.stringify(routeCoordinates),
        notes: "Practice Drive"
      };

      await client.post('/drivelogs/', logData);
      Alert.alert("Success", "Drive logged successfully!");
      navigation.goBack();
    } catch (e) {
      console.log("Error logging drive", e);
      Alert.alert("Error", "Failed to save drive log.");
    }
  };

  const formatTime = (seconds) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
  };

  if (!location) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#007bff" />
        <Text>Fetching Location...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <OSMMap
        style={styles.map}
        region={{
            latitude: location.latitude,
            longitude: location.longitude,
        }}
        markers={[
          { latitude: location.latitude, longitude: location.longitude, title: "You" }
        ]}
        polylines={[
          { coordinates: routeCoordinates, strokeWidth: 5, strokeColor: "#007bff" }
        ]}
      />

      <View style={styles.controls}>
        <View style={styles.stats}>
           <Text style={styles.statText}>Time: {formatTime(duration)}</Text>
           <Text style={styles.statText}>Dist: {(routeCoordinates.length * 0.01).toFixed(2)} km</Text>
        </View>

        {!isTracking ? (
          <TouchableOpacity style={[styles.button, styles.startButton]} onPress={startDrive}>
            <Text style={styles.buttonText}>Start Drive 🚗</Text>
          </TouchableOpacity>
        ) : (
          <TouchableOpacity style={[styles.button, styles.stopButton]} onPress={stopDrive}>
            <Text style={styles.buttonText}>Stop & Save 🛑</Text>
          </TouchableOpacity>
        )}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  loadingContainer: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  map: { flex: 1 },
  controls: {
    position: 'absolute',
    bottom: 30,
    left: 20,
    right: 20,
    backgroundColor: 'white',
    padding: 20,
    borderRadius: 15,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    elevation: 5,
  },
  stats: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginBottom: 20,
  },
  statText: { fontSize: 18, fontWeight: 'bold' },
  button: {
    height: 50,
    borderRadius: 10,
    justifyContent: 'center',
    alignItems: 'center',
  },
  startButton: { backgroundColor: '#28a745' },
  stopButton: { backgroundColor: '#dc3545' },
  buttonText: { color: 'white', fontSize: 18, fontWeight: 'bold' },
});

export default DriveLogScreen;
