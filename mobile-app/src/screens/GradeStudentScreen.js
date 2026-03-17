import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, FlatList, TouchableOpacity, ScrollView, TextInput, Alert, ActivityIndicator } from 'react-native';
import client from '../api/client';
import { FAULT_CATEGORIES } from '../data/faults';
import Ionicons from '@expo/vector-icons/Ionicons';
import { SafeAreaView } from 'react-native-safe-area-context';

const GradeStudentScreen = ({ navigation }) => {
  const [bookings, setBookings] = useState([]);
  const [selectedBooking, setSelectedBooking] = useState(null);
  const [loading, setLoading] = useState(true);
  
  // Grading Form State
  const [feedback, setFeedback] = useState('');
  const [privateNotes, setPrivateNotes] = useState('');
  const [faultCounts, setFaultCounts] = useState({}); // { 'A1': 2, 'B3': 1 }
  const [expandedCategory, setExpandedCategory] = useState(null);

  useEffect(() => {
    fetchBookings();
  }, []);

  const fetchBookings = async () => {
    try {
      const response = await client.get('/bookings/');
      // Filter for accepted bookings that are NOT completed
      // (status 'accepted' usually means confirmed but not yet happened/graded)
      const active = response.data.filter(b => b.status === 'accepted');
      setBookings(active);
    } catch (e) {
      Alert.alert("Error", "Could not load bookings");
    } finally {
      setLoading(false);
    }
  };

  const updateCount = (code, delta) => {
    setFaultCounts(prev => {
      const current = prev[code] || 0;
      const next = Math.max(0, current + delta);
      if (next === 0) {
        const { [code]: _, ...rest } = prev;
        return rest;
      }
      return { ...prev, [code]: next };
    });
  };

  const submitGrade = async () => {
    if (!selectedBooking) return;

    // Convert counts to lists of codes (e.g. A1: 2 -> ["A1", "A1"])
    const generateList = (prefix) => {
      let list = [];
      Object.entries(faultCounts).forEach(([code, count]) => {
        if (code.startsWith(prefix)) {
          for (let i = 0; i < count; i++) list.push(code);
        }
      });
      return list.join(','); // Backend expects comma-separated string, not JSON array string in some contexts, but checking models...
      // models.py says: observation_data = Column(Text) # A1-A8
      // The seed data uses "A1,A3". Let's match that.
    };

    try {
      const sessionData = {
        booking_id: selectedBooking.id,
        duration_minutes: 60, // Default or calculate from booking
        weather_condition: "Sunny",
        road_type: "Urban",
        observation_data: generateList('A'),
        space_margin_data: generateList('B'),
        speed_data: generateList('C'),
        steering_data: generateList('D'),
        communication_data: generateList('E'),
        shared_feedback: feedback,
        instructor_private_notes: privateNotes
      };

      await client.post('/sessions/', sessionData);

      Alert.alert("Success", "Student graded successfully!", [
        { text: "OK", onPress: () => navigation.goBack() }
      ]);
    } catch (e) {
      console.log(e);
      Alert.alert("Error", "Failed to submit grade. " + (e.response?.data?.detail || ""));
    }
  };

  const toggleCategory = (id) => {
    setExpandedCategory(expandedCategory === id ? null : id);
  };

  const renderBooking = ({ item }) => (
    <TouchableOpacity 
      style={styles.card} 
      onPress={() => setSelectedBooking(item)}
    >
      <View style={styles.bookingRow}>
        <Ionicons name="person-circle" size={40} color="#007bff" />
        <View style={{marginLeft: 15}}>
          <Text style={styles.cardTitle}>{item.student?.full_name || `Student #${item.student_id}`}</Text>
          <Text>{item.date} at {item.time}</Text>
        </View>
      </View>
    </TouchableOpacity>
  );

  if (loading) return <ActivityIndicator style={{flex:1}} size="large" />;

  if (!selectedBooking) {
    return (
      <SafeAreaView style={styles.container} edges={['top']}>
        <Text style={styles.header}>Select Student to Grade</Text>
        <FlatList 
          data={bookings}
          renderItem={renderBooking}
          keyExtractor={item => item.id.toString()}
          contentContainerStyle={styles.list}
          ListEmptyComponent={<Text style={styles.emptyText}>No active bookings found.</Text>}
        />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.topBar}>
        <TouchableOpacity onPress={() => setSelectedBooking(null)}>
          <Ionicons name="arrow-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Grading Lesson #{selectedBooking.id}</Text>
        <View style={{width: 24}} />
      </View>

      <ScrollView style={styles.formScroll}>
        <Text style={styles.sectionHeader}>Record Faults</Text>
        
        {FAULT_CATEGORIES.map(cat => (
          <View key={cat.id} style={styles.categoryCard}>
            <TouchableOpacity 
              style={[styles.categoryHeader, { backgroundColor: cat.color }]}
              onPress={() => toggleCategory(cat.id)}
            >
              <Text style={styles.categoryTitle}>{cat.id}. {cat.title}</Text>
              <Ionicons 
                name={expandedCategory === cat.id ? "chevron-up" : "chevron-down"} 
                size={20} color="#555" 
              />
            </TouchableOpacity>
            
            {expandedCategory === cat.id && (
              <View style={styles.categoryContent}>
                {cat.items.map(item => {
                  const count = faultCounts[item.code] || 0;
                  return (
                    <View key={item.code} style={styles.faultRow}>
                      <View style={{flex: 1}}>
                        <Text style={styles.faultCode}>{item.code}</Text>
                        <Text style={styles.faultLabel}>{item.label}</Text>
                      </View>
                      
                      <View style={styles.counter}>
                        <TouchableOpacity onPress={() => updateCount(item.code, -1)} disabled={count===0}>
                          <Ionicons name="remove-circle-outline" size={28} color={count===0 ? "#ccc" : "#dc3545"} />
                        </TouchableOpacity>
                        <Text style={styles.countText}>{count}</Text>
                        <TouchableOpacity onPress={() => updateCount(item.code, 1)}>
                          <Ionicons name="add-circle" size={28} color="#28a745" />
                        </TouchableOpacity>
                      </View>
                    </View>
                  );
                })}
              </View>
            )}
          </View>
        ))}

        <Text style={styles.sectionHeader}>Feedback</Text>
        <TextInput
          style={styles.input}
          multiline
          numberOfLines={4}
          placeholder="Shared feedback for the student..."
          value={feedback}
          onChangeText={setFeedback}
        />

        <Text style={styles.sectionHeader}>Private Notes</Text>
        <TextInput
          style={[styles.input, { backgroundColor: '#fffbe6' }]}
          multiline
          numberOfLines={2}
          placeholder="Notes for yourself..."
          value={privateNotes}
          onChangeText={setPrivateNotes}
        />

        <TouchableOpacity style={styles.submitButton} onPress={submitGrade}>
          <Text style={styles.submitButtonText}>Submit Grading</Text>
        </TouchableOpacity>
        <View style={{height: 50}} />
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f5f5f5' },
  header: { fontSize: 22, fontWeight: 'bold', padding: 20 },
  list: { padding: 15 },
  card: {
    backgroundColor: 'white', padding: 20, borderRadius: 12, marginBottom: 15,
    shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 5, elevation: 2
  },
  bookingRow: { flexDirection: 'row', alignItems: 'center' },
  cardTitle: { fontSize: 18, fontWeight: 'bold', marginBottom: 5 },
  emptyText: { textAlign: 'center', marginTop: 50, color: '#999' },

  topBar: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 15, backgroundColor: 'white', borderBottomWidth: 1, borderBottomColor: '#eee' },
  headerTitle: { fontSize: 18, fontWeight: 'bold' },
  
  formScroll: { padding: 15 },
  sectionHeader: { fontSize: 18, fontWeight: 'bold', marginTop: 20, marginBottom: 10, color: '#333' },
  
  categoryCard: { marginBottom: 10, backgroundColor: 'white', borderRadius: 8, overflow: 'hidden' },
  categoryHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 15 },
  categoryTitle: { fontWeight: 'bold', fontSize: 16 },
  categoryContent: { padding: 10 },
  
  faultRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: '#f0f0f0' },
  faultCode: { fontWeight: 'bold', color: '#666', fontSize: 12 },
  faultLabel: { fontSize: 15, color: '#333' },
  
  counter: { flexDirection: 'row', alignItems: 'center', minWidth: 100, justifyContent: 'flex-end' },
  countText: { fontSize: 18, fontWeight: 'bold', marginHorizontal: 15, minWidth: 20, textAlign: 'center' },
  
  input: { backgroundColor: 'white', padding: 15, borderRadius: 8, borderWidth: 1, borderColor: '#ddd', textAlignVertical: 'top', fontSize: 16 },
  
  submitButton: { backgroundColor: '#007bff', padding: 18, borderRadius: 10, alignItems: 'center', marginTop: 30 },
  submitButtonText: { color: 'white', fontWeight: 'bold', fontSize: 18 }
});

export default GradeStudentScreen;