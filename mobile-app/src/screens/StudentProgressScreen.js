import React, { useState, useEffect } from 'react';
import { View, Text, ScrollView, StyleSheet, ActivityIndicator, TouchableOpacity, Dimensions } from 'react-native';
import client from '../api/client';
import { SafeAreaView } from 'react-native-safe-area-context';
import Ionicons from '@expo/vector-icons/Ionicons';

const StudentProgressScreen = ({ navigation }) => {
  const [sessions, setSessions] = useState([]);
  const [bookings, setBookings] = useState([]);
  const [diagnosticRides, setDiagnosticRides] = useState([]);
  const [progressSummary, setProgressSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({});

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      // Fetch sessions, bookings, diagnostic rides, and consolidated progress
      const [sessionsRes, bookingsRes, diagRes, progressRes] = await Promise.all([
        client.get('/sessions/'),
        client.get('/bookings/'),
        client.get('/diagnostic-rides/'),
        client.get('/users/me/progress')
      ]);
      
      const sessionData = sessionsRes.data;
      const bookingData = bookingsRes.data;
      const diagData = diagRes.data;
      const progressData = progressRes.data;
      
      setSessions(sessionData);
      setBookings(bookingData);
      setDiagnosticRides(diagData);
      setProgressSummary(progressData);
      calculateStats(sessionData, bookingData, progressData);
    } catch (e) {
      console.log(e);
    } finally {
      setLoading(false);
    }
  };

  const calculateStats = (sessionData, bookingData, progressData) => {
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
        trend,
        overallScore: progressData?.overall_score || 0
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

  if (loading) return <ActivityIndicator size="large" color="#3B82F6" style={{flex:1, backgroundColor: '#0B1326'}} />;

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        <View style={styles.headerContainer}>
          <Text style={styles.header}>My Progress</Text>
          <Text style={styles.subtitle}>OPERATIONAL STATUS</Text>
        </View>
        
        {/* Main Stats Grid */}
        <View style={styles.statsGrid}>
            <View style={styles.statBox}>
                <Text style={styles.statLabel}>LESSONS</Text>
                <Text style={styles.statNumber}>{stats.totalSessions}</Text>
            </View>
            <View style={styles.statBox}>
                <Text style={styles.statLabel}>READY</Text>
                <Text style={[styles.statNumber, {color: '#15803D'}]}>{stats.overallScore?.toFixed(0)}%</Text>
            </View>
            <View style={styles.statBox}>
                <Text style={styles.statLabel}>HOURS</Text>
                <Text style={styles.statNumber}>{Math.round(stats.totalHours)}h</Text>
            </View>
        </View>

        {/* Trend Banner */}
        <View style={[styles.trendBanner, stats.trend === 'Improving' ? styles.trendGood : styles.trendNeutral]}>
            <View style={styles.trendIconContainer}>
              <Ionicons name={stats.trend === 'Improving' ? "trending-up" : "analytics"} size={20} color="white" />
            </View>
            <View>
              <Text style={styles.trendLabel}>SYSTEM TREND</Text>
              <Text style={styles.trendText}>{stats.trend.toUpperCase()}</Text>
            </View>
        </View>

        {/* Practice Hours Goal (Visual Bar) */}
        <Text style={styles.sectionTitle}>Road to License</Text>
        <View style={styles.goalContainer}>
            <View style={styles.goalHeader}>
                <Text style={styles.goalLabel}>PRACTICE LOG</Text>
                <Text style={styles.goalValue}>{stats.totalHours} / 60h</Text>
            </View>
            <View style={styles.progressBarBg}>
                <View style={[styles.progressBarFill, { width: `${Math.min((stats.totalHours/60)*100, 100)}%` }]} />
            </View>
            <Text style={styles.goalSub}>Recommended practice hours for optimal safety</Text>
        </View>

        <Text style={styles.sectionTitle}>Performance Analytics</Text>
        <View style={styles.row}>
          <View style={[styles.skillCard, { backgroundColor: 'rgba(239,68,68,0.15)' }]}>
             <Text style={styles.skillTitle}>Observation</Text>
             <Text style={[styles.skillCount, { color: '#B91C1C' }]}>{stats.faultCounts?.A || 0} Issues</Text>
          </View>
          <View style={[styles.skillCard, { backgroundColor: 'rgba(59,130,246,0.15)' }]}>
             <Text style={styles.skillTitle}>Space Margins</Text>
             <Text style={[styles.skillCount, { color: '#4338CA' }]}>{stats.faultCounts?.B || 0} Issues</Text>
          </View>
        </View>

        <View style={styles.row}>
          <View style={[styles.skillCard, { backgroundColor: 'rgba(21,128,61,0.15)' }]}>
             <Text style={styles.skillTitle}>Speed Control</Text>
             <Text style={[styles.skillCount, { color: '#15803D' }]}>{stats.faultCounts?.C || 0} Issues</Text>
          </View>
          <View style={[styles.skillCard, { backgroundColor: 'rgba(245,158,11,0.15)' }]}>
             <Text style={styles.skillTitle}>Steering</Text>
             <Text style={[styles.skillCount, { color: '#D97706' }]}>{stats.faultCounts?.D || 0} Issues</Text>
          </View>
        </View>
        
        <Text style={styles.sectionTitle}>Session History</Text>
        {sessions.map(s => (
            <TouchableOpacity 
              key={s.id} 
              style={styles.feedbackCard}
              onPress={() => navigation.navigate('SessionDetail', { session: s })}
            >
                <View style={{flex: 1}}>
                  <Text style={styles.date}>Lesson #{s.booking_id}</Text>
                  <Text style={styles.comment} numberOfLines={1}>
                    {s.shared_feedback || "No written feedback."}
                  </Text>
                </View>
                <Ionicons name="chevron-forward" size={20} color="#94A3B8" />
            </TouchableOpacity>
        ))}

        <Text style={styles.sectionTitle}>Diagnostic Rides</Text>
        {diagnosticRides.length > 0 ? (
            diagnosticRides.map(r => (
                <TouchableOpacity 
                  key={r.id} 
                  style={styles.diagCard}
                  onPress={() => navigation.navigate('DiagnosticRideDetail', { rideId: r.id })}
                >
                    <View style={{flex: 1}}>
                        <View style={styles.rowBetween}>
                            <Text style={styles.date}>{new Date(r.created_at).toLocaleDateString()}</Text>
                            <View style={[styles.badge, {backgroundColor: r.passed ? '#15803D' : '#EF4444'}]}>
                                <Text style={styles.badgeText}>{r.passed ? 'PASS' : 'FAIL'}</Text>
                            </View>
                        </View>
                        <Text style={styles.diagType}>{r.ride_type.replace('_', ' ').toUpperCase()}</Text>
                        <View style={styles.scoreContainer}>
                          <Text style={styles.scoreLabel}>OVERALL SCORE</Text>
                          <Text style={[styles.scoreText, {color: r.passed ? '#15803D' : '#EF4444'}]}>{r.overall_score?.toFixed(1)}%</Text>
                        </View>
                    </View>
                </TouchableOpacity>
            ))
        ) : (
            <Text style={styles.emptyText}>No diagnostic telemetry recorded.</Text>
        )}
        <View style={{height: 100}} />
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0B1326' },
  scroll: { padding: 24 },
  headerContainer: { marginBottom: 24 },
  header: { fontSize: 32, fontWeight: '800', color: '#FFFFFF', letterSpacing: -1 },
  subtitle: { fontSize: 12, fontWeight: '800', color: '#15803D', letterSpacing: 2, marginTop: 4 },
  
  statsGrid: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 24 },
  statBox: { 
      width: '31%', backgroundColor: '#131B2E', padding: 20, borderRadius: 20, alignItems: 'center',
  },
  statLabel: { fontSize: 10, fontWeight: '800', color: '#94A3B8', marginBottom: 8, letterSpacing: 1 },
  statNumber: { fontSize: 24, fontWeight: '800', color: '#FFFFFF', letterSpacing: -1 },

  trendBanner: { 
      flexDirection: 'row', alignItems: 'center', padding: 20, borderRadius: 24, marginBottom: 32,
      backgroundColor: '#1E293B',
  },
  trendGood: { backgroundColor: '#15803D' },
  trendNeutral: { backgroundColor: '#1E293B' },
  trendIconContainer: { width: 40, height: 40, borderRadius: 20, backgroundColor: 'rgba(255,255,255,0.1)', justifyContent: 'center', alignItems: 'center', marginRight: 16 },
  trendLabel: { color: 'rgba(255,255,255,0.6)', fontWeight: '800', fontSize: 10, letterSpacing: 1 },
  trendText: { color: 'white', fontWeight: '800', fontSize: 18, letterSpacing: -0.5 },

  goalContainer: { backgroundColor: '#131B2E', padding: 24, borderRadius: 24, marginBottom: 40 },
  goalHeader: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 12, alignItems: 'flex-end' },
  goalLabel: { fontWeight: '800', color: '#FFFFFF', fontSize: 13, letterSpacing: 0.5 },
  goalValue: { color: '#15803D', fontWeight: '800', fontSize: 20, letterSpacing: -1 },
  progressBarBg: { height: 12, backgroundColor: '#1E293B', borderRadius: 6, overflow: 'hidden' },
  progressBarFill: { height: '100%', backgroundColor: '#15803D', borderRadius: 6 },
  goalSub: { fontSize: 12, color: '#94A3B8', marginTop: 12, fontWeight: '500' },

  sectionTitle: { fontSize: 18, fontWeight: '800', marginBottom: 20, color: '#FFFFFF', letterSpacing: -0.5 },
  row: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 16 },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 },
  skillCard: { width: '48%', padding: 20, borderRadius: 20 },
  skillTitle: { fontWeight: '800', fontSize: 16, marginBottom: 4, color: '#FFFFFF', letterSpacing: -0.3 },
  skillCount: { fontSize: 13, fontWeight: '700' },
  
  feedbackCard: { 
    backgroundColor: '#131B2E', padding: 20, borderRadius: 20, marginBottom: 12,
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
  },
  diagCard: {
    backgroundColor: '#131B2E', padding: 24, borderRadius: 24, marginBottom: 16,
  },
  date: { fontWeight: '800', color: '#FFFFFF', fontSize: 16, letterSpacing: -0.5 },
  comment: { color: '#64748B', fontWeight: '500', marginTop: 4, fontSize: 14 },
  diagType: { fontSize: 11, fontWeight: '800', color: '#15803D', marginBottom: 16, letterSpacing: 1 },
  scoreContainer: { borderTopWidth: 1, borderTopColor: '#1E293B', paddingTop: 16, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  scoreLabel: { fontSize: 10, fontWeight: '800', color: '#94A3B8', letterSpacing: 1 },
  scoreText: { fontSize: 20, fontWeight: '800', letterSpacing: -1 },
  
  badge: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 10 },
  badgeText: { color: 'white', fontSize: 11, fontWeight: '900' },
  emptyText: { textAlign: 'center', color: '#94A3B8', marginTop: 10, fontWeight: '600' }
});


export default StudentProgressScreen;
