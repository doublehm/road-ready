import React, { useState, useEffect } from 'react';
import { View, Text, FlatList, StyleSheet, Image, TouchableOpacity, Alert, ActivityIndicator } from 'react-native';
import client from '../api/client';

const FindInstructorScreen = () => {
  const [instructors, setInstructors] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchInstructors();
  }, []);

  const fetchInstructors = async () => {
    try {
      const response = await client.get('/instructors/');
      setInstructors(response.data);
    } catch (e) {
      console.log("Error fetching instructors", e);
      Alert.alert("Error", "Could not load instructors.");
    } finally {
      setLoading(false);
    }
  };

  const handleBook = async (instructor) => {
    // Simple booking flow for prototype
    Alert.alert(
      "Confirm Booking",
      `Book a session with ${instructor.city} Instructor?`,
      [
        {
          text: "Cancel",
          style: "cancel"
        },
        {
          text: "Confirm",
          onPress: async () => {
             try {
               await client.post('/bookings/', {
                 instructor_id: instructor.id,
                 date: "2024-01-20", // Mock date
                 time: "10:00", // Mock time
                 duration: 2,
                 pickup_address: "123 Main St",
                 notes: "Mobile App Booking"
               });
               Alert.alert("Success", "Booking request sent!");
             } catch (e) {
               Alert.alert("Error", "Failed to book.");
             }
          }
        }
      ]
    );
  };

  const renderItem = ({ item }) => (
    <View style={styles.card}>
      <View style={styles.cardContent}>
        <View>
           <Text style={styles.name}>Instructor in {item.city}</Text>
           <Text style={styles.rate}>${item.hourly_rate}/hr</Text>
           <Text style={styles.bio}>{item.bio}</Text>
        </View>
      </View>
      <TouchableOpacity style={styles.bookButton} onPress={() => handleBook(item)}>
        <Text style={styles.bookButtonText}>Book Now</Text>
      </TouchableOpacity>
    </View>
  );

  if (loading) {
    return <ActivityIndicator style={{flex: 1}} size="large" color="#007bff" />;
  }

  return (
    <View style={styles.container}>
      <FlatList
        data={instructors}
        keyExtractor={(item) => item.id.toString()}
        renderItem={renderItem}
        contentContainerStyle={styles.list}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f5f5f5' },
  list: { padding: 20 },
  card: {
    backgroundColor: 'white',
    borderRadius: 10,
    padding: 15,
    marginBottom: 15,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    elevation: 3,
  },
  cardContent: { flexDirection: 'row', marginBottom: 10 },
  name: { fontSize: 18, fontWeight: 'bold' },
  rate: { fontSize: 16, color: '#28a745', marginVertical: 5 },
  bio: { color: '#666' },
  bookButton: {
    backgroundColor: '#007bff',
    padding: 10,
    borderRadius: 5,
    alignItems: 'center',
  },
  bookButtonText: { color: 'white', fontWeight: 'bold' },
});

export default FindInstructorScreen;
