import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, FlatList, TouchableOpacity, ScrollView, TextInput, Switch, Alert, ActivityIndicator } from 'react-native';
import client from '../api/client';

const GradeStudentScreen = () => {
  const [bookings, setBookings] = useState([]);
  const [selectedBooking, setSelectedBooking] = useState(null);
  const [loading, setLoading] = useState(true);
  
  // Grading Form State
  const [feedback, setFeedback] = useState('');
  const [faults, setFaults] = useState({
    A1: false, // Shoulder Check
    B1: false, // Lane Position
    C1: false, // Speed
  });

  useEffect(() => {
    fetchBookings();
  }, []);

  const fetchBookings = async () => {
    try {
      const response = await client.get('/bookings/');
      // In a real app, filter for accepted bookings only
      setBookings(response.data);
    } catch (e) {
      Alert.alert("Error", "Could not load bookings");
    } finally {
      setLoading(false);
    }
  };

  const submitGrade = async () => {
    const activeFaults = Object.keys(faults).filter(k => faults[k]);
    
    try {
      await client.post('/sessions/', {
        booking_id: selectedBooking.id,
        duration_minutes: 60,
        weather_condition: "Sunny",
        road_type: "Urban",
        observation_data: JSON.stringify(activeFaults.filter(f => f.startsWith('A'))),
        space_margin_data: JSON.stringify(activeFaults.filter(f => f.startsWith('B'))),
        speed_data: JSON.stringify(activeFaults.filter(f => f.startsWith('C'))),
        steering_data: "[]",
        communication_data: "[]",
        shared_feedback: feedback
      });
      Alert.alert("Success", "Student graded successfully!");
      setSelectedBooking(null);
      setFeedback('');
      setFaults({ A1: false, B1: false, C1: false });
    } catch (e) {
      console.log(e);
      Alert.alert("Error", "Failed to submit grade.");
    }
  };

  const renderBooking = ({ item }) => (
    <TouchableOpacity 
      style={styles.card} 
      onPress={() => setSelectedBooking(item)}
    >
      <Text style={styles.cardTitle}>Student #{item.student_id}</Text>
      <Text>{item.date} at {item.time}</Text>
    </TouchableOpacity>
  );

  if (loading) return <ActivityIndicator style={{flex:1}} size="large" />;

  if (!selectedBooking) {
    return (
      <View style={styles.container}>
        <Text style={styles.header}>Select a Student to Grade</Text>
        <FlatList 
          data={bookings}
          renderItem={renderBooking}
          keyExtractor={item => item.id.toString()}
          contentContainerStyle={styles.list}
        />
      </View>
    );
  }

  return (
    <ScrollView style={styles.container}>
      <TouchableOpacity 
        style={styles.backButton}
        onPress={() => setSelectedBooking(null)}
      >
        <Text style={styles.backButtonText}>← Back to List</Text>
      </TouchableOpacity>

      <Text style={styles.header}>Grading Student #{selectedBooking.student_id}</Text>
      
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Key Faults</Text>
        
        <View style={styles.switchRow}>
          <Text>Missed Shoulder Check (A1)</Text>
          <Switch 
            value={faults.A1} 
            onValueChange={v => setFaults({...faults, A1: v})}
          />
        </View>

        <View style={styles.switchRow}>
          <Text>Poor Lane Position (B1)</Text>
          <Switch 
            value={faults.B1} 
            onValueChange={v => setFaults({...faults, B1: v})}
          />
        </View>

        <View style={styles.switchRow}>
          <Text>Speed Maintenance (C1)</Text>
          <Switch 
            value={faults.C1} 
            onValueChange={v => setFaults({...faults, C1: v})}
          />
        </View>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Feedback</Text>
        <TextInput
          style={styles.input}
          multiline
          numberOfLines={4}
          placeholder="Enter notes for the student..."
          value={feedback}
          onChangeText={setFeedback}
        />
      </View>

      <TouchableOpacity style={styles.submitButton} onPress={submitGrade}>
        <Text style={styles.submitButtonText}>Submit Evaluation</Text>
      </TouchableOpacity>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f5f5f5', padding: 20 },
  header: { fontSize: 22, fontWeight: 'bold', marginBottom: 20 },
  card: {
    backgroundColor: 'white', padding: 20, borderRadius: 10, marginBottom: 15
  },
  cardTitle: { fontSize: 18, fontWeight: 'bold' },
  section: { backgroundColor: 'white', padding: 15, borderRadius: 10, marginBottom: 20 },
  sectionTitle: { fontSize: 16, fontWeight: 'bold', marginBottom: 10 },
  switchRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 15 },
  input: { borderWidth: 1, borderColor: '#ddd', borderRadius: 5, padding: 10, height: 100, textAlignVertical: 'top' },
  submitButton: { backgroundColor: '#28a745', padding: 15, borderRadius: 10, alignItems: 'center', marginBottom: 40 },
  submitButtonText: { color: 'white', fontWeight: 'bold', fontSize: 18 },
  backButton: { marginBottom: 10 },
  backButtonText: { color: '#007bff' }
});

export default GradeStudentScreen;
