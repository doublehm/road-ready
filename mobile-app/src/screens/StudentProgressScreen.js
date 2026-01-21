import React, { useState, useEffect } from 'react';
import { View, Text, ScrollView, StyleSheet, ActivityIndicator, TouchableOpacity, Dimensions } from 'react-native';
import client from '../api/client';
import { SafeAreaView } from 'react-native-safe-area-context';
import Ionicons from 'react-native-vector-icons/Ionicons';

const StudentProgressScreen = ({ navigation }) => {
  const [sessions, setSessions] = useState([]);
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({});

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      // Fetch both sessions (reports) and bookings (financials)
      const [sessionsRes, bookingsRes] = await Promise.all([
        client.get('/sessions/'),
        client.get('/bookings/')
      ]);
      
      const sessionData = sessionsRes.data;
      const bookingData = bookingsRes.data.filter(b => b.student_id === 4); // Filter for current user (prototype hack, ideally backend filters)
      
      setSessions(sessionData);
      setBookings(bookingData);
      calculateStats(sessionData, bookingData);
    } catch (e) {
      console.log(e);
    } finally {
      setLoading(false);
    }
  };

  const calculateStats = (sessionData, bookingData) => {
    let faultCounts = { A: 0, B: 0, C: 0, D: 0, E: 0 };
    let totalMinutes = 0;
    
    // Faults & Time
    sessionData.forEach(s => {
       totalMinutes += s.duration_minutes;
       const count = (str) => (str ? str.split(',').length : 0);
       faultCounts.A += count(s.observation_data);
       faultCounts.B += count(s.space_margin_data);
       faultCounts.C += count(s.speed_data);
       faultCounts.D += count(s.steering_data);
       faultCounts.E += count(s.communication_data);
    });

    // Money (Completed bookings only)
    const completed = bookingData.filter(b => b.status === 'completed' || b.status === 'paid');
    const totalSpent = completed.reduce((sum, b) => sum + (b.total_amount || 0), 0);

    // Improvement Trend (Compare last session faults to average)
    let trend = "Stable";
    if (sessionData.length > 1) {
        // Sort by id (proxy for date)
        const sorted = [...sessionData].sort((a,b) => b.id - a.id);
        const last = sorted[0];
        const lastFaults = countFaults(last);
        const prevAvg = (countTotalFaults(sorted.slice(1)) / (sorted.length - 1));
        
        if (lastFaults < prevAvg) trend = "Improving";
        else if (lastFaults > prevAvg) trend = "Needs Focus";
    }

    setStats({ 
        totalSessions: sessionData.length,
        totalHours: (totalMinutes / 60).toFixed(1),
        totalSpent,
        faultCounts,
        trend
    });
  };

  const countFaults = (s) => {
      if(!s) return 0;
      return (s.observation_data?.split(',').length || 0) + 
             (s.space_margin_data?.split(',').length || 0) + 
             (s.speed_data?.split(',').length || 0) + 
             (s.steering_data?.split(',').length || 0) +
             (s.communication_data?.split(',').length || 0);
  };

  const countTotalFaults = (list) => {
      return list.reduce((sum, s) => sum + countFaults(s), 0);
  };

  if (loading) return <ActivityIndicator size="large" style={{flex:1}} />;

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text style={styles.header}>My Progress</Text>
        
        {/* Main Stats Grid */}
        <View style={styles.statsGrid}>
            <View style={styles.statBox}>
                <Text style={styles.statLabel}>Lessons</Text>
                <Text style={styles.statNumber}>{stats.totalSessions}</Text>
            </View>
            <View style={styles.statBox}>
                <Text style={styles.statLabel}>Hours</Text>
                <Text style={styles.statNumber}>{stats.totalHours}h</Text>
            </View>
            <View style={styles.statBox}>
                <Text style={styles.statLabel}>Spent</Text>
                <Text style={[styles.statNumber, {color: '#28a745'}]}>${stats.totalSpent}</Text>
            </View>
        </View>

        {/* Trend Banner */}
        <View style={[styles.trendBanner, stats.trend === 'Improving' ? styles.trendGood : styles.trendNeutral]}>
            <Ionicons name={stats.trend === 'Improving' ? "trending-up" : "analytics"} size={24} color="white" />
            <Text style={styles.trendText}>Status: {stats.trend}</Text>
        </View>

        {/* Practice Hours Goal (Visual Bar) */}
        <Text style={styles.sectionTitle}>Road to License</Text>
        <View style={styles.goalContainer}>
            <View style={styles.goalHeader}>
                <Text style={styles.goalLabel}>Practice Hours</Text>
                <Text style={styles.goalValue}>{stats.totalHours} / 60h</Text>
            </View>
            <View style={styles.progressBarBg}>
                <View style={[styles.progressBarFill, { width: `${Math.min((stats.totalHours/60)*100, 100)}%` }]} />
            </View>
            <Text style={styles.goalSub}>Recommended practice before road test</Text>
        </View>

        <Text style={styles.sectionTitle}>Areas for Improvement</Text>
        <View style={styles.row}>
          <View style={[styles.skillCard, { backgroundColor: '#ffebee' }]}>
             <Text style={styles.skillTitle}>Observation</Text>
             <Text style={styles.skillCount}>{stats.faultCounts?.A || 0} Issues</Text>
          </View>
          <View style={[styles.skillCard, { backgroundColor: '#e3f2fd' }]}>
             <Text style={styles.skillTitle}>Space Margins</Text>
             <Text style={styles.skillCount}>{stats.faultCounts?.B || 0} Issues</Text>
          </View>
        </View>

        <View style={styles.row}>
          <View style={[styles.skillCard, { backgroundColor: '#e8f5e9' }]}>
             <Text style={styles.skillTitle}>Speed Control</Text>
             <Text style={styles.skillCount}>{stats.faultCounts?.C || 0} Issues</Text>
          </View>
          <View style={[styles.skillCard, { backgroundColor: '#fff3cd' }]}>
             <Text style={styles.skillTitle}>Steering</Text>
             <Text style={styles.skillCount}>{stats.faultCounts?.D || 0} Issues</Text>
          </View>
        </View>
        
        <Text style={styles.sectionTitle}>Session History</Text>
        {sessions.map(s => (
            <TouchableOpacity 
              key={s.id} 
              style={styles.feedbackCard}
              onPress={() => navigation.navigate('SessionDetail', { session: s })}
            >
                <View>
                  <Text style={styles.date}>Lesson #{s.booking_id}</Text>
                  <Text style={styles.comment} numberOfLines={2}>
                    {s.shared_feedback || "No written feedback."}
                  </Text>
                </View>
                <Ionicons name="chevron-forward" size={20} color="#ccc" />
            </TouchableOpacity>
        ))}
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  scroll: { padding: 20 },
  header: { fontSize: 28, fontWeight: 'bold', marginBottom: 20, color: '#333' },
  
  statsGrid: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 20 },
  statBox: { 
      width: '31%', backgroundColor: '#f8f9fa', padding: 15, borderRadius: 12, alignItems: 'center',
      borderWidth: 1, borderColor: '#eee'
  },
  statLabel: { fontSize: 12, color: '#666', marginBottom: 5 },
  statNumber: { fontSize: 20, fontWeight: 'bold', color: '#333' },

  trendBanner: { 
      flexDirection: 'row', alignItems: 'center', padding: 15, borderRadius: 12, marginBottom: 25,
      backgroundColor: '#6c757d'
  },
  trendGood: { backgroundColor: '#28a745' },
  trendNeutral: { backgroundColor: '#6c757d' },
  trendText: { color: 'white', fontWeight: 'bold', marginLeft: 10, fontSize: 16 },

  goalContainer: { marginBottom: 25 },
  goalHeader: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 8 },
  goalLabel: { fontWeight: '600', color: '#333' },
  goalValue: { color: '#007bff', fontWeight: 'bold' },
  progressBarBg: { height: 10, backgroundColor: '#e9ecef', borderRadius: 5, overflow: 'hidden' },
  progressBarFill: { height: '100%', backgroundColor: '#007bff' },
  goalSub: { fontSize: 12, color: '#888', marginTop: 5 },

  sectionTitle: { fontSize: 18, fontWeight: 'bold', marginTop: 10, marginBottom: 15 },
  row: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 15 },
  skillCard: { width: '48%', padding: 15, borderRadius: 10 },
  skillTitle: { fontWeight: 'bold', fontSize: 16, marginBottom: 5 },
  skillCount: { fontSize: 14, color: '#555' },
  
  feedbackCard: { 
    backgroundColor: 'white', padding: 15, borderRadius: 12, marginBottom: 10,
    borderWidth: 1, borderColor: '#eee',
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between'
  },
  date: { fontWeight: 'bold', marginBottom: 5, color: '#333' },
  comment: { fontStyle: 'italic', color: '#666', width: '90%' }
});

export default StudentProgressScreen;
