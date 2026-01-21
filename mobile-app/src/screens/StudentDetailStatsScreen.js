import React, { useState, useEffect } from 'react';
import { View, Text, ScrollView, StyleSheet, ActivityIndicator, TouchableOpacity } from 'react-native';
import client from '../api/client';
import { SafeAreaView } from 'react-native-safe-area-context';
import Ionicons from 'react-native-vector-icons/Ionicons';
import { FAULT_CATEGORIES } from '../data/faults';

const StudentDetailStatsScreen = ({ route, navigation }) => {
  const { student } = route.params; // Passed from list
  const [sessions, setSessions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({});

  useEffect(() => {
    fetchStudentData();
  }, []);

  const fetchStudentData = async () => {
    try {
      // In a real app, we'd have /instructors/students/{id}/progress
      // Here we fetch all sessions and filter (prototype style)
      const response = await client.get('/sessions/');
      
      // Filter sessions where the student matches
      // But /sessions/ endpoint returns sessions for the *current user*.
      // If I am instructor, it returns sessions I taught.
      // I need to filter these sessions by the student's ID from the booking.
      
      // We need to fetch bookings first to link session -> booking -> student_id
      const bookingsRes = await client.get('/bookings/');
      const studentBookings = bookingsRes.data.filter(b => b.student_id === student.id);
      const studentBookingIds = studentBookings.map(b => b.id);
      
      const allMySessions = response.data;
      const studentSessions = allMySessions.filter(s => studentBookingIds.includes(s.booking_id));
      
      setSessions(studentSessions);
      calculateStats(studentSessions);
    } catch (e) {
      console.log(e);
    } finally {
      setLoading(false);
    }
  };

  const calculateStats = (data) => {
    let faultCounts = {};
    
    data.forEach(s => {
       const process = (str) => {
           if(!str) return;
           str.split(',').forEach(code => {
               const c = code.trim();
               if(c) faultCounts[c] = (faultCounts[c] || 0) + 1;
           });
       };
       process(s.observation_data);
       process(s.space_margin_data);
       process(s.speed_data);
       process(s.steering_data);
       process(s.communication_data);
    });

    // Sort faults by frequency
    const topFaults = Object.entries(faultCounts)
        .sort((a,b) => b[1] - a[1])
        .slice(0, 5); // Top 5

    setStats({ 
        totalLessons: data.length,
        topFaults
    });
  };

  const getFaultLabel = (code) => {
      for(let cat of FAULT_CATEGORIES) {
          const item = cat.items.find(i => i.code === code);
          if(item) return item.label;
      }
      return code;
  };

  if (loading) return <ActivityIndicator size="large" style={{flex:1}} />;

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Ionicons name="arrow-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{student.full_name}'s Progress</Text>
        <View style={{width: 24}} />
      </View>

      <ScrollView contentContainerStyle={styles.scroll}>
        
        {/* Summary */}
        <View style={styles.card}>
            <View style={styles.statRow}>
                <View style={styles.statItem}>
                    <Text style={styles.statVal}>{stats.totalLessons}</Text>
                    <Text style={styles.statLabel}>Lessons Completed</Text>
                </View>
                <View style={[styles.statItem, {borderLeftWidth: 1, borderColor: '#eee'}]}>
                    <Text style={styles.statVal}>Active</Text>
                    <Text style={styles.statLabel}>Status</Text>
                </View>
            </View>
        </View>

        {/* Top Issues */}
        <Text style={styles.sectionTitle}>Top Areas for Improvement</Text>
        <View style={styles.issuesCard}>
            {stats.topFaults && stats.topFaults.length > 0 ? (
                stats.topFaults.map(([code, count], index) => (
                    <View key={code} style={styles.issueRow}>
                        <View style={styles.issueRank}>
                            <Text style={styles.rankText}>#{index + 1}</Text>
                        </View>
                        <View style={{flex: 1}}>
                            <Text style={styles.issueLabel}>{getFaultLabel(code)}</Text>
                            <Text style={styles.issueCode}>{code}</Text>
                        </View>
                        <View style={styles.issueCount}>
                            <Text style={styles.countText}>{count}x</Text>
                        </View>
                    </View>
                ))
            ) : (
                <Text style={styles.emptyText}>No major faults recorded yet.</Text>
            )}
        </View>

        {/* Lesson History */}
        <Text style={styles.sectionTitle}>Lesson History</Text>
        {sessions.map(s => (
            <TouchableOpacity 
                key={s.id} 
                style={styles.sessionRow}
                onPress={() => navigation.navigate('SessionDetail', { session: s })}
            >
                <View>
                    <Text style={styles.sessionDate}>Lesson #{s.booking_id}</Text>
                    <Text style={styles.sessionSub}>{s.duration_minutes} min</Text>
                </View>
                <Ionicons name="chevron-forward" size={20} color="#ccc" />
            </TouchableOpacity>
        ))}

      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f5f5f5' },
  header: { 
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', 
    padding: 15, backgroundColor: 'white', borderBottomWidth: 1, borderBottomColor: '#eee' 
  },
  headerTitle: { fontSize: 18, fontWeight: 'bold', color: '#333' },
  scroll: { padding: 20 },

  card: {
      backgroundColor: 'white', borderRadius: 12, padding: 20, marginBottom: 25,
      shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 5, elevation: 2
  },
  statRow: { flexDirection: 'row' },
  statItem: { flex: 1, alignItems: 'center' },
  statVal: { fontSize: 24, fontWeight: 'bold', color: '#007bff' },
  statLabel: { fontSize: 13, color: '#666', marginTop: 5 },

  sectionTitle: { fontSize: 18, fontWeight: 'bold', marginBottom: 15, color: '#333' },
  
  issuesCard: {
      backgroundColor: 'white', borderRadius: 12, overflow: 'hidden', marginBottom: 25,
      borderWidth: 1, borderColor: '#eee'
  },
  issueRow: { 
      flexDirection: 'row', alignItems: 'center', padding: 15, 
      borderBottomWidth: 1, borderBottomColor: '#f9f9f9' 
  },
  issueRank: { 
      width: 30, height: 30, borderRadius: 15, backgroundColor: '#fff3cd', 
      justifyContent: 'center', alignItems: 'center', marginRight: 15 
  },
  rankText: { fontWeight: 'bold', color: '#856404', fontSize: 12 },
  issueLabel: { fontSize: 16, fontWeight: '500', color: '#333' },
  issueCode: { fontSize: 12, color: '#888' },
  issueCount: { backgroundColor: '#ffebee', paddingHorizontal: 10, paddingVertical: 4, borderRadius: 10 },
  countText: { color: '#dc3545', fontWeight: 'bold', fontSize: 12 },

  sessionRow: {
      backgroundColor: 'white', padding: 15, borderRadius: 10, marginBottom: 10,
      flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center'
  },
  sessionDate: { fontWeight: 'bold', fontSize: 16 },
  sessionSub: { color: '#666' },
  
  emptyText: { padding: 20, textAlign: 'center', color: '#999' }
});

export default StudentDetailStatsScreen;
