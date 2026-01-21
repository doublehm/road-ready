import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, TextInput, TouchableOpacity, ScrollView, Alert, ActivityIndicator, Platform } from 'react-native';
import client from '../api/client';
import { SafeAreaView } from 'react-native-safe-area-context';
import MapView, { Marker } from 'react-native-maps';
import * as Location from 'expo-location';
import DateTimePicker from '@react-native-community/datetimepicker';

const BookingFlowScreen = ({ route, navigation }) => {
  const { instructor } = route.params;
  
  // State
  const [date, setDate] = useState(new Date());
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [showTimePicker, setShowTimePicker] = useState(false);
  
  const [duration, setDuration] = useState('2');
  const [address, setAddress] = useState('');
  const [region, setRegion] = useState({
    latitude: 49.2827,
    longitude: -123.1207,
    latitudeDelta: 0.05,
    longitudeDelta: 0.05,
  });
  const [marker, setMarker] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    (async () => {
      let { status } = await Location.requestForegroundPermissionsAsync();
      if (status === 'granted') {
        let location = await Location.getCurrentPositionAsync({});
        setRegion({
          ...region,
          latitude: location.coords.latitude,
          longitude: location.coords.longitude,
        });
      }
    })();
  }, []);

  const handleMapPress = (e) => {
    setMarker(e.nativeEvent.coordinate);
  };

  const onDateChange = (event, selectedDate) => {
    setShowDatePicker(false);
    if (selectedDate) setDate(selectedDate);
  };

  const onTimeChange = (event, selectedTime) => {
    setShowTimePicker(false);
    if (selectedTime) {
        // Merge time into date object
        const newDate = new Date(date);
        newDate.setHours(selectedTime.getHours());
        newDate.setMinutes(selectedTime.getMinutes());
        setDate(newDate);
    }
  };

  const handleBook = async () => {
    if (!address) {
      Alert.alert('Missing Info', 'Please select a pickup address.');
      return;
    }

    setLoading(true);
    try {
      const dateStr = date.toISOString().split('T')[0];
      const timeStr = date.toTimeString().split(' ')[0].substring(0, 5); // HH:MM

      await client.post('/bookings/', {
        instructor_id: instructor.id,
        date: dateStr,
        time: timeStr,
        duration: parseInt(duration),
        pickup_address: address,
        pickup_lat: marker?.latitude,
        pickup_lng: marker?.longitude,
        notes: "Mobile Booking"
      });
      
      Alert.alert('Success', 'Booking request sent!', [
        { text: 'OK', onPress: () => navigation.navigate('Dashboard') }
      ]);
    } catch (e) {
      Alert.alert('Error', 'Booking failed.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text style={styles.header}>Book Session</Text>
        
        {/* Date & Time Selection */}
        <Text style={styles.label}>Select Date & Time</Text>
        
        <View style={styles.dateTimeRow}>
            <TouchableOpacity style={styles.dateTimeBtn} onPress={() => setShowDatePicker(true)}>
                <Text style={styles.dateTimeText}>{date.toLocaleDateString()}</Text>
            </TouchableOpacity>
            
            <TouchableOpacity style={styles.dateTimeBtn} onPress={() => setShowTimePicker(true)}>
                <Text style={styles.dateTimeText}>{date.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}</Text>
            </TouchableOpacity>
        </View>

        {showDatePicker && (
            <DateTimePicker
                value={date}
                mode="date"
                display="default"
                onChange={onDateChange}
                minimumDate={new Date()}
            />
        )}

        {showTimePicker && (
            <DateTimePicker
                value={date}
                mode="time"
                display="default"
                onChange={onTimeChange}
            />
        )}

        {/* Duration */}
        <Text style={styles.label}>Duration</Text>
        <View style={styles.durationRow}>
            {['1', '1.5', '2'].map(d => (
              <TouchableOpacity 
                key={d} 
                style={[styles.durationBtn, duration === d && styles.durationBtnActive]}
                onPress={() => setDuration(d)}
              >
                <Text style={[styles.durationText, duration === d && styles.textActive]}>{d} hr</Text>
              </TouchableOpacity>
            ))}
        </View>

        {/* Map & Address */}
        <Text style={styles.label}>Pickup Location</Text>
        <View style={styles.mapContainer}>
          <MapView 
            style={styles.map} 
            region={region}
            onPress={handleMapPress}
          >
            {marker && <Marker coordinate={marker} />}
          </MapView>
        </View>
        <TextInput 
          style={styles.input} 
          placeholder="Enter address..." 
          value={address} 
          onChangeText={setAddress} 
        />

        {/* Summary */}
        <View style={styles.summary}>
          <View style={styles.summaryRow}>
            <Text>Total</Text>
            <Text style={styles.totalPrice}>${(instructor.hourly_rate * parseFloat(duration)).toFixed(2)}</Text>
          </View>
        </View>

        <TouchableOpacity 
          style={styles.confirmBtn} 
          onPress={handleBook}
          disabled={loading}
        >
          {loading ? <ActivityIndicator color="white" /> : <Text style={styles.confirmText}>Confirm Booking</Text>}
        </TouchableOpacity>

        <View style={{height: 40}} />
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  scroll: { padding: 20 },
  header: { fontSize: 28, fontWeight: 'bold', color: '#333', marginBottom: 20 },
  label: { fontWeight: '600', marginBottom: 10, marginTop: 10, color: '#333', fontSize: 16 },
  
  dateTimeRow: { flexDirection: 'row', justifyContent: 'space-between' },
  dateTimeBtn: { 
      flex: 0.48, padding: 15, backgroundColor: '#f0f0f0', borderRadius: 10, alignItems: 'center' 
  },
  dateTimeText: { fontSize: 16, color: '#333' },

  durationRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 10 },
  durationBtn: { 
    flex: 1, padding: 12, borderRadius: 8, borderWidth: 1, borderColor: '#ddd', 
    alignItems: 'center', marginHorizontal: 5, backgroundColor: '#fff'
  },
  durationBtnActive: { backgroundColor: '#007bff', borderColor: '#007bff' },
  durationText: { color: '#666' },
  textActive: { color: 'white' },

  mapContainer: { height: 200, borderRadius: 12, overflow: 'hidden', marginBottom: 10 },
  map: { flex: 1 },
  input: { 
    borderWidth: 1, borderColor: '#ddd', borderRadius: 8, padding: 12, 
    fontSize: 16, backgroundColor: '#f9f9f9', marginBottom: 20 
  },

  summary: { backgroundColor: '#f8f9fa', padding: 20, borderRadius: 10, marginBottom: 20 },
  summaryRow: { flexDirection: 'row', justifyContent: 'space-between' },
  totalPrice: { fontWeight: 'bold', fontSize: 18, color: '#28a745' },

  confirmBtn: { backgroundColor: '#28a745', padding: 16, borderRadius: 10, alignItems: 'center' },
  confirmText: { color: 'white', fontWeight: 'bold', fontSize: 18 }
});

export default BookingFlowScreen;
