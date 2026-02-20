import React, { useState, useEffect } from 'react';
import { View, Text, ScrollView, StyleSheet, ActivityIndicator, TouchableOpacity } from 'react-native';
import client from '../api/client';
import { SafeAreaView } from 'react-native-safe-area-context';
import Ionicons from 'react-native-vector-icons/Ionicons';
import { FAULT_CATEGORIES } from '../data/faults';

const CategoryBar = ({ label, score }) => {
    const width = score ? `${Math.min(100, score)}%` : '0%';
    const color = (score || 0) >= 75 ? '#28a745' : (score || 0) >= 60 ? '#ffc107' : '#dc3545';
  
    return (
      <View style={styles.catContainer}>
        <View style={styles.catHeader}>
            <Text style={styles.catLabel}>{label}</Text>
            <Text style={styles.catValue}>{Math.round(score || 0)}</Text>
        </View>
        <View style={styles.catTrack}>
          <View style={[styles.catFill, { width, backgroundColor: color }]} />
        </View>
      </View>
    );
  };

const StudentDetailStatsScreen = ({ route, navigation }) => {
  const { student } = route.params; // Passed from list
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
      // Fetch trends first for the dashboard
      try {
        const trendsRes = await client.get(`/diagnostic-rides/progress-trends?student_id=${student.id}`);
        setTrends(trendsRes.data);
      } catch (trendsErr) {
        console.log('No trends found:', trendsErr);
      }

      // Fetch sessions and filter (prototype style)
      const response = await client.get('/sessions/');
      
      const bookingsRes = await client.get('/bookings/');
      const studentBookings = bookingsRes.data.filter(b => b.student_id === student.id);
      const studentBookingIds = studentBookings.map(b => b.id);
      
      const allMySessions = response.data;
      const studentSessions = allMySessions.filter(s => studentBookingIds.includes(s.booking_id));
      
      setSessions(studentSessions);
      calculateStats(studentSessions);

      // Fetch diagnostic rides for this student
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
        
        {/* Performance Dashboard */}
        <Text style={styles.sectionTitle}>Performance Dashboard</Text>
        <View style={styles.dashboardCard}>
            <View style={styles.dashboardRow}>
                <View style={styles.dashboardItem}>
                    <Text style={styles.dashboardVal}>{trends?.total_rides || 0}</Text>
                    <Text style={styles.dashboardLabel}>Total Rides</Text>
                </View>
                <View style={styles.dashboardItem}>
                    <Text style={styles.dashboardVal}>{trends?.pass_rate || 0}%</Text>
                    <Text style={styles.dashboardLabel}>Pass Rate</Text>
                </View>
                <View style={styles.dashboardItem}>
                    <Text style={[styles.dashboardVal, {color: '#28a745'}]}>{trends?.recent_score || 0}</Text>
                    <Text style={styles.dashboardLabel}>Recent Score</Text>
                </View>
            </View>
            <View style={[styles.dashboardRow, {marginTop: 20, paddingTop: 20, borderTopWidth: 1, borderTopColor: '#f0f0f0'}]}>
                <View style={styles.dashboardItem}>
                    <Text style={styles.dashboardVal}>{trends?.avg_duration_minutes || 0}m</Text>
                    <Text style={styles.dashboardLabel}>Avg Duration</Text>
                </View>
                <View style={styles.dashboardItem}>
                    <Text style={styles.dashboardVal}>{trends?.total_distance_km || 0}km</Text>
                    <Text style={styles.dashboardLabel}>Total Dist.</Text>
                </View>
                <View style={styles.dashboardItem}>
                    <Text style={[styles.dashboardVal, {color: '#ff9800'}]}>{trends?.improvement_areas?.length || 0}</Text>
                    <Text style={styles.dashboardLabel}>Focus Areas</Text>
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
        <Text style={styles.sectionTitle}>Manual Observation Faults</Text>
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

        {/* Diagnostic Ride History */}
        <Text style={styles.sectionTitle}>Diagnostic Ride History</Text>
        {diagnosticRides.length > 0 ? diagnosticRides.map(ride => {
            const scoreColor = (ride.overall_score || 0) >= 80 ? '#28a745' :
                               (ride.overall_score || 0) >= 60 ? '#ffc107' : '#dc3545';
            const rideDate = ride.created_at ? ride.created_at.split('T')[0] : '';
            return (
                <TouchableOpacity
                    key={ride.id}
                    style={styles.rideCard}
                    onPress={() => navigation.navigate('DiagnosticRideDetail', { rideId: ride.id })}
                >
                    <View style={[styles.rideScoreBadge, { backgroundColor: scoreColor }]}>
                        <Text style={styles.rideScoreText}>{Math.round(ride.overall_score || 0)}</Text>
                    </View>
                    <View style={{flex: 1, marginLeft: 12}}>
                        <View style={{flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between'}}>
                            <Text style={styles.sessionDate}>{rideDate}</Text>
                            {ride.passed !== null && (
                                <View style={[styles.passFailBadge, { backgroundColor: ride.passed ? '#28a745' : '#dc3545' }]}>
                                    <Text style={styles.passFailText}>{ride.passed ? 'PASSED' : 'FAILED'}</Text>
                                </View>
                            )}
                        </View>
                        <Text style={styles.sessionSub}>
                            {ride.duration_minutes ? `${Math.round(ride.duration_minutes)} min` : '--'}
                            {' • '}
                            {ride.distance_km ? `${ride.distance_km.toFixed(1)} km` : '--'}
                        </Text>
                    </View>
                    <Ionicons name="chevron-forward" size={20} color="#ccc" />
                </TouchableOpacity>
            );
        }) : (
            <Text style={styles.emptyText}>No diagnostic rides recorded yet.</Text>
        )}

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

  dashboardCard: {
    backgroundColor: 'white', borderRadius: 12, padding: 20, marginBottom: 25,
    shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 5, elevation: 2
  },
  dashboardRow: { flexDirection: 'row', justifyContent: 'space-around' },
  dashboardItem: { alignItems: 'center' },
  dashboardVal: { fontSize: 22, fontWeight: 'bold', color: '#1a1a1a' },
  dashboardLabel: { fontSize: 11, color: '#6c757d', marginTop: 4 },
  
  categoryAverages: { marginTop: 25 },
  catContainer: { marginBottom: 12 },
  catHeader: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 6 },
  catLabel: { fontSize: 13, fontWeight: '600', color: '#495057' },
  catValue: { fontSize: 13, fontWeight: 'bold', color: '#1a1a1a' },
  catTrack: { height: 8, backgroundColor: '#e9ecef', borderRadius: 4, overflow: 'hidden' },
  catFill: { height: '100%', borderRadius: 4 },

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
  
  emptyText: { padding: 20, textAlign: 'center', color: '#999' },

  rideCard: {
      backgroundColor: 'white', padding: 15, borderRadius: 10, marginBottom: 10,
      flexDirection: 'row', alignItems: 'center',
      shadowColor: '#000', shadowOpacity: 0.03, shadowRadius: 3, elevation: 1
  },
  rideScoreBadge: {
      width: 44, height: 44, borderRadius: 22,
      justifyContent: 'center', alignItems: 'center'
  },
  rideScoreText: { color: '#fff', fontSize: 16, fontWeight: 'bold' },
  passFailBadge: { borderRadius: 4, paddingHorizontal: 8, paddingVertical: 2 },
  passFailText: { color: '#fff', fontSize: 10, fontWeight: 'bold' },
});

export default StudentDetailStatsScreen;
