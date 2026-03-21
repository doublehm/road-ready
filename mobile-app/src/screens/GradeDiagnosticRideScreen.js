import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, FlatList, TouchableOpacity, ScrollView, TextInput, Alert, ActivityIndicator, Switch } from 'react-native';
import client from '../api/client';
import Ionicons from '@expo/vector-icons/Ionicons';
import { SafeAreaView } from 'react-native-safe-area-context';

const GradeDiagnosticRideScreen = ({ navigation }) => {
  const [rides, setRides] = useState([]);
  const [selectedRide, setSelectedRide] = useState(null);
  const [loading, setLoading] = useState(true);
  
  // Grading Form State
  const [passed, setPassed] = useState(true);
  const [overallScore, setOverallScore] = useState('80');
  const [notes, setNotes] = useState('');
  const [criteria, setCriteria] = useState({
    braking: true,
    speed: true,
    steering: true
  });

  useEffect(() => {
    fetchRides();
  }, []);

  const fetchRides = async () => {
    try {
      const response = await client.get('/diagnostic-rides/');
      // Filter for rides assigned to this instructor that are pending
      const pending = response.data.filter(r => r.status === 'pending');
      setRides(pending);
    } catch (e) {
      Alert.alert("Error", "Could not load diagnostic rides");
    } finally {
      setLoading(false);
    }
  };

  const submitResults = async () => {
    if (!selectedRide) return;

    try {
      const payload = {
        passed: passed ? 'true' : 'false',
        overall_score: parseFloat(overallScore),
        criteria_braking: criteria.braking,
        criteria_speed: criteria.speed,
        criteria_steering: criteria.steering,
        notes: notes
      };

      // We use the web-friendly endpoint we created in app/main.py
      // Note: In a real app, we'd probably use a proper API endpoint with JSON body,
      // but since we added @app.post("/log-diagnostic-ride/{ride_id}") with Form fields,
      // we need to send it as form data or handle it accordingly.
      
      const formData = new FormData();
      formData.append('passed', passed ? 'true' : 'false');
      formData.append('overall_score', overallScore);
      formData.append('criteria_braking', criteria.braking);
      formData.append('criteria_speed', criteria.speed);
      formData.append('criteria_steering', criteria.steering);
      formData.append('notes', notes);

      await client.post(`/log-diagnostic-ride/${selectedRide.id}`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });

      Alert.alert("Success", "Diagnostic results saved!", [
        { text: "OK", onPress: () => {
            setSelectedRide(null);
            fetchRides();
        }}
      ]);
    } catch (e) {
      console.log(e);
      Alert.alert("Error", "Failed to submit results. " + (e.response?.data?.detail || e.message));
    }
  };

  const renderRide = ({ item }) => (
    <TouchableOpacity 
      style={styles.card} 
      onPress={() => setSelectedRide(item)}
    >
      <View style={styles.rideRow}>
        <View style={[styles.iconBg, {backgroundColor: 'rgba(59, 130, 246, 0.15)'}]}>
            <Ionicons name="car-outline" size={30} color="#3B82F6" />
        </View>
        <View style={{marginLeft: 15, flex: 1}}>
          <Text style={styles.cardTitle}>{item.student?.full_name || `Student #${item.student_id}`}</Text>
          <Text style={styles.cardSub}>Scheduled: {item.start_time.split('T')[0]}</Text>
          <Text style={styles.cardType}>{item.ride_type.replace('_', ' ').toUpperCase()}</Text>
        </View>
        <Ionicons name="chevron-forward" size={20} color="#475569" />
      </View>
    </TouchableOpacity>
  );

  if (loading) return <ActivityIndicator style={{flex:1}} size="large" />;

  if (!selectedRide) {
    return (
      <SafeAreaView style={styles.container} edges={['top']}>
        <View style={styles.headerContainer}>
            <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
                <Ionicons name="arrow-back" size={24} color="#fff" />
            </TouchableOpacity>
            <Text style={styles.header}>Pending Diagnostic Rides</Text>
        </View>
        <FlatList 
          data={rides}
          renderItem={renderRide}
          keyExtractor={item => item.id.toString()}
          contentContainerStyle={styles.list}
          ListEmptyComponent={
            <View style={styles.emptyContainer}>
                <Ionicons name="checkmark-done-circle-outline" size={80} color="#475569" />
                <Text style={styles.emptyText}>All diagnostic rides are completed!</Text>
            </View>
          }
        />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.topBar}>
        <TouchableOpacity onPress={() => setSelectedRide(null)}>
          <Ionicons name="arrow-back" size={24} color="#fff" />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Grading Ride #{selectedRide.id}</Text>
        <View style={{width: 24}} />
      </View>

      <ScrollView style={styles.formScroll}>
        <View style={styles.studentInfo}>
            <Text style={styles.studentName}>{selectedRide.student?.full_name || `Student #${selectedRide.student_id}`}</Text>
            <Text style={styles.rideDate}>{selectedRide.start_time.replace('T', ' ')}</Text>
        </View>

        <View style={styles.section}>
            <View style={styles.rowBetween}>
                <Text style={styles.sectionHeader}>Overall Status</Text>
                <View style={styles.passFailContainer}>
                    <Text style={[styles.passFailText, {color: passed ? '#28a745' : '#dc3545'}]}>
                        {passed ? "PASS" : "FAIL"}
                    </Text>
                    <Switch
                        value={passed}
                        onValueChange={setPassed}
                        trackColor={{ false: "#fab1a0", true: "#55efc4" }}
                        thumbColor={passed ? "#00b894" : "#d63031"}
                    />
                </View>
            </View>
        </View>

        <View style={styles.section}>
            <Text style={styles.sectionHeader}>Overall Score (0-100)</Text>
            <TextInput
                style={styles.scoreInput}
                keyboardType="numeric"
                value={overallScore}
                onChangeText={setOverallScore}
                placeholder="e.g. 85"
                placeholderTextColor="#64748B"
            />
        </View>

        <View style={styles.section}>
            <Text style={styles.sectionHeader}>Criteria Evaluation</Text>
            <View style={styles.criteriaList}>
                <TouchableOpacity 
                    style={styles.criteriaItem} 
                    onPress={() => setCriteria({...criteria, braking: !criteria.braking})}
                >
                    <Ionicons 
                        name={criteria.braking ? "checkbox" : "square-outline"} 
                        size={24} color={criteria.braking ? "#28a745" : "#999"} 
                    />
                    <Text style={styles.criteriaLabel}>Smooth Braking</Text>
                </TouchableOpacity>

                <TouchableOpacity 
                    style={styles.criteriaItem} 
                    onPress={() => setCriteria({...criteria, speed: !criteria.speed})}
                >
                    <Ionicons 
                        name={criteria.speed ? "checkbox" : "square-outline"} 
                        size={24} color={criteria.speed ? "#28a745" : "#999"} 
                    />
                    <Text style={styles.criteriaLabel}>Speed Compliance</Text>
                </TouchableOpacity>

                <TouchableOpacity 
                    style={styles.criteriaItem} 
                    onPress={() => setCriteria({...criteria, steering: !criteria.steering})}
                >
                    <Ionicons 
                        name={criteria.steering ? "checkbox" : "square-outline"} 
                        size={24} color={criteria.steering ? "#28a745" : "#999"} 
                    />
                    <Text style={styles.criteriaLabel}>Steering/Cornering</Text>
                </TouchableOpacity>
            </View>
        </View>

        <View style={styles.section}>
            <Text style={styles.sectionHeader}>Instructor Notes</Text>
            <TextInput
                style={styles.input}
                multiline
                numberOfLines={4}
                placeholder="Add your evaluation notes here..."
                placeholderTextColor="#64748B"
                value={notes}
                onChangeText={setNotes}
            />
        </View>

        <TouchableOpacity style={[styles.submitButton, {backgroundColor: passed ? '#15803D' : '#3B82F6'}]} onPress={submitResults}>
          <Text style={styles.submitButtonText}>Save Results</Text>
        </TouchableOpacity>
        <View style={{height: 50}} />
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0B1326' },
  headerContainer: { flexDirection: 'row', alignItems: 'center', padding: 10, backgroundColor: '#131B2E' },
  backBtn: { padding: 10 },
  header: { fontSize: 20, fontWeight: 'bold', marginLeft: 10, color: '#fff' },
  list: { padding: 15 },
  card: {
    backgroundColor: '#131B2E', padding: 15, borderRadius: 12, marginBottom: 15,
    shadowColor: '#000', shadowOpacity: 0.2, shadowRadius: 5, elevation: 2,
    borderWidth: 1, borderColor: '#1E293B'
  },
  rideRow: { flexDirection: 'row', alignItems: 'center' },
  iconBg: { width: 50, height: 50, borderRadius: 10, justifyContent: 'center', alignItems: 'center' },
  cardTitle: { fontSize: 17, fontWeight: 'bold', color: '#fff' },
  cardSub: { fontSize: 13, color: '#94A3B8', marginTop: 2 },
  cardType: { fontSize: 11, fontWeight: 'bold', color: '#3B82F6', marginTop: 5, letterSpacing: 0.5 },
  emptyContainer: { alignItems: 'center', marginTop: 100 },
  emptyText: { textAlign: 'center', marginTop: 20, color: '#64748B', fontSize: 16 },

  topBar: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 15, backgroundColor: '#131B2E', borderBottomWidth: 1, borderBottomColor: '#1E293B' },
  headerTitle: { fontSize: 18, fontWeight: 'bold', color: '#fff' },
  
  formScroll: { padding: 20 },
  studentInfo: { marginBottom: 25, backgroundColor: 'rgba(59, 130, 246, 0.15)', padding: 15, borderRadius: 10 },
  studentName: { fontSize: 20, fontWeight: 'bold', color: '#3B82F6' },
  rideDate: { fontSize: 14, color: '#94A3B8', marginTop: 5 },

  section: { marginBottom: 25 },
  sectionHeader: { fontSize: 16, fontWeight: 'bold', marginBottom: 10, color: '#CBD5E1' },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  
  passFailContainer: { flexDirection: 'row', alignItems: 'center' },
  passFailText: { fontSize: 18, fontWeight: '900', marginRight: 10 },
  
  scoreInput: { backgroundColor: '#131B2E', padding: 15, borderRadius: 10, borderWidth: 1, borderColor: '#1E293B', fontSize: 20, fontWeight: 'bold', textAlign: 'center', width: 100, color: '#fff' },
  
  criteriaList: { backgroundColor: '#131B2E', borderRadius: 10, padding: 10, borderWidth: 1, borderColor: '#1E293B' },
  criteriaItem: { flexDirection: 'row', alignItems: 'center', padding: 12, borderBottomWidth: 1, borderBottomColor: '#1E293B' },
  criteriaLabel: { fontSize: 16, marginLeft: 15, color: '#E2E8F0' },

  input: { backgroundColor: '#131B2E', padding: 15, borderRadius: 10, borderWidth: 1, borderColor: '#1E293B', textAlignVertical: 'top', fontSize: 16, minHeight: 100, color: '#fff' },
  
  submitButton: { padding: 18, borderRadius: 12, alignItems: 'center', marginTop: 10, elevation: 3, shadowColor: '#000', shadowOpacity: 0.2, shadowRadius: 10 },
  submitButtonText: { color: 'white', fontWeight: 'bold', fontSize: 18 }
});

export default GradeDiagnosticRideScreen;
