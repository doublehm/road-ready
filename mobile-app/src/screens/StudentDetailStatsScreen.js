import React, { useState, useEffect } from 'react';
import { View, Text, ScrollView, StyleSheet, ActivityIndicator, TouchableOpacity } from 'react-native';
import client from '../api/client';
import { SafeAreaView } from 'react-native-safe-area-context';
import Ionicons from '@expo/vector-icons/Ionicons';
import { FAULT_CATEGORIES } from '../data/faults';

const CategoryBar = ({ label, score }) => {
    const width = score ? `${Math.min(100, score)}%` : '0%';
    const color = (score || 0) >= 80 ? '#15803D' : (score || 0) >= 60 ? '#D97706' : '#EF4444';
  
    return (
      <View style={styles.catContainer}>
        <View style={styles.catHeader}>
            <Text style={styles.catLabel}>{label.toUpperCase()}</Text>
            <Text style={[styles.catValue, { color }]}>{Math.round(score || 0)}%</Text>
        </View>
        <View style={styles.catTrack}>
          <View style={[styles.catFill, { width, backgroundColor: color }]} />
        </View>
      </View>
    );
  };

const StudentDetailStatsScreen = ({ route, navigation }) => {
  const { student } = route.params; 
  const [sessions, setSessions] = useState([]);
  const [diagnosticRides, setDiagnosticRides] = useState([]);
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({});
  const [trends, setTrends] = useState(null);

  useEffect(() => {
    fetchStudentData();
  }, []);

  const fetchStudentData = async () => {
    try {
      try {
        const trendsRes = await client.get(`/diagnostic-rides/progress-trends?student_id=${student.id}`);
        setTrends(trendsRes.data);
      } catch (trendsErr) {
        console.log('No trends found:', trendsErr);
      }

      const response = await client.get('/sessions/');
      const bookingsRes = await client.get('/bookings/');
      const studentBookings = bookingsRes.data.filter(b => b.student_id === student.id);
      const studentBookingIds = studentBookings.map(b => b.id);
      
      const allMySessions = response.data;
      const studentSessions = allMySessions.filter(s => studentBookingIds.includes(s.booking_id));
      
      setSessions(studentSessions);
      calculateStats(studentSessions);

      try {
        const ridesRes = await client.get(`/diagnostic-rides/student/${student.id}/rides`);
        setDiagnosticRides(ridesRes.data || []);
      } catch (ridesErr) {
        console.log('No diagnostic rides found:', ridesErr);
      }
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

    const topFaults = Object.entries(faultCounts)
        .sort((a,b) => b[1] - a[1])
        .slice(0, 5); 

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

  if (loading) return <ActivityIndicator size="large" style={{flex:1}} color="#1E293B" />;

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Ionicons name="chevron-back" size={24} color="#1E293B" />
        </TouchableOpacity>
        <View>
          <Text style={styles.headerTitle}>{student.full_name}</Text>
          <Text style={styles.headerSubtitle}>STUDENT ANALYTICS</Text>
        </View>
        <View style={{width: 44}} />
      </View>

      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        
        {/* Performance Dashboard */}
        <Text style={styles.sectionTitle}>Performance Dashboard</Text>
        <View style={styles.dashboardCard}>
            <View style={styles.dashboardRow}>
                <View style={styles.dashboardItem}>
                    <Text style={styles.dashboardVal}>{trends?.total_rides || 0}</Text>
                    <Text style={styles.dashboardLabel}>TOTAL RIDES</Text>
                </View>
                <View style={styles.dashboardItem}>
                    <Text style={[styles.dashboardVal, {color: '#15803D'}]}>{trends?.pass_rate || 0}%</Text>
                    <Text style={styles.dashboardLabel}>PASS RATE</Text>
                </View>
                <View style={styles.dashboardItem}>
                    <Text style={[styles.dashboardVal, {color: '#1E293B'}]}>{trends?.recent_score || 0}</Text>
                    <Text style={styles.dashboardLabel}>RECENT SCORE</Text>
                </View>
            </View>
            <View style={styles.dashboardDivider} />
            <View style={styles.dashboardRow}>
                <View style={styles.dashboardItem}>
                    <Text style={styles.dashboardVal}>{trends?.avg_duration_minutes || 0}m</Text>
                    <Text style={styles.dashboardLabel}>AVG DURATION</Text>
                </View>
                <View style={styles.dashboardItem}>
                    <Text style={styles.dashboardVal}>{trends?.total_distance_km || 0}km</Text>
                    <Text style={styles.dashboardLabel}>TOTAL DIST.</Text>
                </View>
                <View style={styles.dashboardItem}>
                    <Text style={[styles.dashboardVal, {color: '#D97706'}]}>{trends?.improvement_areas?.length || 0}</Text>
                    <Text style={styles.dashboardLabel}>FOCUS AREAS</Text>
                </View>
            </View>

            {/* Category Breakdown */}
            <View style={styles.categoryAverages}>
                <CategoryBar label="Braking" score={trends?.category_averages?.braking} />
                <CategoryBar label="Speed" score={trends?.category_averages?.speed} />
                <CategoryBar label="Cornering" score={trends?.category_averages?.cornering} />
            </View>
        </View>

        {/* Top Issues */}
        <Text style={styles.sectionTitle}>Observation Faults</Text>
        <View style={styles.issuesCard}>
            {stats.topFaults && stats.topFaults.length > 0 ? (
                stats.topFaults.map(([code, count], index) => (
                    <View key={code} style={styles.issueRow}>
                        <View style={styles.issueRank}>
                            <Text style={styles.rankText}>{index + 1}</Text>
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
                <View style={styles.emptyContainer}>
                  <Text style={styles.emptyText}>No major faults recorded.</Text>
                </View>
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
                    <Text style={styles.sessionSub}>{s.duration_minutes} min duration</Text>
                </View>
                <Ionicons name="chevron-forward" size={20} color="#94A3B8" />
            </TouchableOpacity>
        ))}

        {/* Diagnostic Ride History */}
        <Text style={styles.sectionTitle}>Diagnostic Ride History</Text>
        {diagnosticRides.length > 0 ? diagnosticRides.map(ride => {
            const scoreColor = (ride.overall_score || 0) >= 80 ? '#15803D' :
                               (ride.overall_score || 0) >= 60 ? '#D97706' : '#EF4444';
            const rideDate = ride.created_at ? new Date(ride.created_at).toLocaleDateString() : '';
            return (
                <TouchableOpacity
                    key={ride.id}
                    style={styles.rideCard}
                    onPress={() => navigation.navigate('DiagnosticRideDetail', { rideId: ride.id })}
                >
                    <View style={[styles.rideScoreBadge, { backgroundColor: scoreColor }]}>
                        <Text style={styles.rideScoreText}>{Math.round(ride.overall_score || 0)}</Text>
                    </View>
                    <View style={{flex: 1, marginLeft: 16}}>
                        <View style={{flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between'}}>
                            <Text style={styles.sessionDate}>{rideDate}</Text>
                            {ride.passed !== null && (
                                <View style={[styles.passFailBadge, { backgroundColor: ride.passed ? '#15803D' : '#EF4444' }]}>
                                    <Text style={styles.passFailText}>{ride.passed ? 'PASS' : 'FAIL'}</Text>
                                </View>
                            )}
                        </View>
                        <Text style={styles.sessionSub}>
                            {ride.duration_minutes ? `${Math.round(ride.duration_minutes)}m` : '--'}
                            {' • '}
                            {ride.distance_km ? `${ride.distance_km.toFixed(1)}km` : '--'}
                        </Text>
                    </View>
                    <Ionicons name="chevron-forward" size={20} color="#94A3B8" />
                </TouchableOpacity>
            );
        }) : (
            <View style={styles.emptyContainer}>
              <Text style={styles.emptyText}>No diagnostic rides recorded.</Text>
            </View>
        )}
        <View style={{ height: 60 }} />
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F6FAFE' },
  header: { 
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', 
    paddingHorizontal: 20, paddingVertical: 16, backgroundColor: 'white',
    shadowColor: '#1E293B', shadowOpacity: 0.04, shadowRadius: 10, elevation: 2
  },
  backBtn: { width: 44, height: 44, borderRadius: 22, justifyContent: 'center', alignItems: 'center', backgroundColor: '#F1F5F9' },
  headerTitle: { fontSize: 20, fontWeight: '800', color: '#1E293B', letterSpacing: -0.5 },
  headerSubtitle: { fontSize: 10, fontWeight: '800', color: '#15803D', letterSpacing: 1 },
  scroll: { padding: 24 },

  dashboardCard: {
    backgroundColor: 'white', borderRadius: 24, padding: 24, marginBottom: 32,
    shadowColor: '#1E293B', shadowOpacity: 0.06, shadowRadius: 20, elevation: 4
  },
  dashboardRow: { flexDirection: 'row', justifyContent: 'space-around' },
  dashboardItem: { alignItems: 'center' },
  dashboardVal: { fontSize: 24, fontWeight: '800', color: '#1E293B', letterSpacing: -1 },
  dashboardLabel: { fontSize: 10, fontWeight: '800', color: '#94A3B8', marginTop: 4, letterSpacing: 1 },
  dashboardDivider: { height: 1, backgroundColor: '#F1F5F9', marginVertical: 24 },
  
  categoryAverages: { marginTop: 8 },
  catContainer: { marginBottom: 16 },
  catHeader: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 8 },
  catLabel: { fontSize: 11, fontWeight: '800', color: '#64748B', letterSpacing: 1 },
  catValue: { fontSize: 12, fontWeight: '800' },
  catTrack: { height: 10, backgroundColor: '#F1F5F9', borderRadius: 5, overflow: 'hidden' },
  catFill: { height: '100%', borderRadius: 5 },

  sectionTitle: { fontSize: 18, fontWeight: '800', marginBottom: 20, color: '#1E293B', letterSpacing: -0.5 },
  
  issuesCard: {
      backgroundColor: 'white', borderRadius: 24, overflow: 'hidden', marginBottom: 32,
      shadowColor: '#1E293B', shadowOpacity: 0.04, shadowRadius: 15, elevation: 2
  },
  issueRow: { 
      flexDirection: 'row', alignItems: 'center', padding: 20, 
      borderBottomWidth: 1, borderBottomColor: '#F1F5F9' 
  },
  issueRank: { 
      width: 32, height: 32, borderRadius: 10, backgroundColor: '#F1F5F9', 
      justifyContent: 'center', alignItems: 'center', marginRight: 16 
  },
  rankText: { fontWeight: '800', color: '#64748B', fontSize: 13 },
  issueLabel: { fontSize: 16, fontWeight: '700', color: '#1E293B', letterSpacing: -0.3 },
  issueCode: { fontSize: 12, color: '#94A3B8', fontWeight: '500' },
  issueCount: { backgroundColor: '#FEE2E2', paddingHorizontal: 10, paddingVertical: 4, borderRadius: 10 },
  countText: { color: '#EF4444', fontWeight: '800', fontSize: 12 },

  sessionRow: {
      backgroundColor: 'white', padding: 20, borderRadius: 20, marginBottom: 12,
      flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
      shadowColor: '#1E293B', shadowOpacity: 0.04, shadowRadius: 10, elevation: 2
  },
  sessionDate: { fontWeight: '800', fontSize: 17, color: '#1E293B', letterSpacing: -0.5 },
  sessionSub: { color: '#64748B', fontSize: 14, fontWeight: '500', marginTop: 2 },
  
  emptyContainer: { padding: 40, alignItems: 'center' },
  emptyText: { textAlign: 'center', color: '#94A3B8', fontWeight: '600' },

  rideCard: {
      backgroundColor: 'white', padding: 20, borderRadius: 24, marginBottom: 12,
      flexDirection: 'row', alignItems: 'center',
      shadowColor: '#1E293B', shadowOpacity: 0.04, shadowRadius: 15, elevation: 2
  },
  rideScoreBadge: {
      width: 52, height: 52, borderRadius: 18,
      justifyContent: 'center', alignItems: 'center'
  },
  rideScoreText: { color: '#fff', fontSize: 20, fontWeight: '900' },
  passFailBadge: { borderRadius: 8, paddingHorizontal: 10, paddingVertical: 4 },
  passFailText: { color: '#fff', fontSize: 10, fontWeight: '900' },
});

export default StudentDetailStatsScreen;
