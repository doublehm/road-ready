import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, FlatList, ActivityIndicator, ScrollView, Dimensions, TouchableOpacity } from 'react-native';
import client from '../api/client';
import { SafeAreaView } from 'react-native-safe-area-context';
import Ionicons from 'react-native-vector-icons/Ionicons';

const InstructorEarningsScreen = ({ navigation }) => {
  const [earnings, setEarnings] = useState({ 
    total: 0, 
    history: [],
    monthly: [],
    stats: { students: 0, hours: 0, avgRate: 0 }
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchEarnings();
  }, []);

  const fetchEarnings = async () => {
    try {
      const response = await client.get('/bookings/');
      const allBookings = response.data;
      
      // Filter for completed/paid bookings
      const paidBookings = allBookings.filter(b => b.status === 'completed' || b.status === 'paid' || b.status === 'accepted');
      
      // Calculate Total
      const totalEarned = paidBookings.reduce((sum, b) => sum + (b.instructor_payout || 0), 0);
      const totalHours = paidBookings.reduce((sum, b) => sum + (b.duration || 0), 0);
      
      // Calculate Monthly Breakdown (Last 6 Months)
      const months = {};
      const today = new Date();
      for (let i = 5; i >= 0; i--) {
          const d = new Date(today.getFullYear(), today.getMonth() - i, 1);
          const key = d.toLocaleString('default', { month: 'short' });
          months[key] = 0;
      }

      paidBookings.forEach(b => {
          const date = new Date(b.date);
          const key = date.toLocaleString('default', { month: 'short' });
          if (months.hasOwnProperty(key)) {
              months[key] += (b.instructor_payout || 0);
          }
      });

      // Find max for scaling
      const maxMonth = Math.max(...Object.values(months), 1); // Avoid div by 0
      const monthlyData = Object.entries(months).map(([month, amount]) => ({
          month, 
          amount,
          height: (amount / maxMonth) * 100 // Percentage height
      }));

      // Unique Students
      const uniqueStudents = new Set(paidBookings.map(b => b.student_id)).size;

      setEarnings({
        total: totalEarned,
        history: paidBookings.sort((a,b) => new Date(b.date) - new Date(a.date)),
        monthly: monthlyData,
        stats: {
            students: uniqueStudents,
            hours: totalHours,
            avgRate: totalHours > 0 ? (totalEarned / totalHours).toFixed(2) : 0
        }
      });
      
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  const renderHistoryItem = ({ item }) => (
    <TouchableOpacity 
      style={styles.transactionRow}
      onPress={() => {
        if (item.driving_session) {
          navigation.navigate('SessionDetail', { session: item.driving_session });
        } else {
          // If no session logged yet (just paid?), maybe show alert or nothing
          // Usually paid means completed, so session should exist.
        }
      }}
      disabled={!item.driving_session}
    >
      <View style={styles.iconBox}>
        <Ionicons name="car-sport" size={20} color="#007bff" />
      </View>
      <View style={styles.transInfo}>
        <Text style={styles.transTitle}>Lesson with {item.student?.full_name || 'Student'}</Text>
        <Text style={styles.transDate}>{item.date}</Text>
      </View>
      <View style={{alignItems: 'flex-end'}}>
        <Text style={styles.transAmount}>+${(item.instructor_payout || 0).toFixed(2)}</Text>
        {item.driving_session && <Text style={{fontSize:10, color:'#007bff'}}>View Report</Text>}
      </View>
    </TouchableOpacity>
  );

  if (loading) return <ActivityIndicator size="large" style={{ flex: 1 }} />;

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text style={styles.header}>Business Analytics</Text>

        {/* Main Stats Cards */}
        <View style={styles.grid}>
            <View style={styles.statCard}>
                <Text style={styles.label}>Total Revenue</Text>
                <Text style={styles.value}>${earnings.total.toFixed(0)}</Text>
            </View>
            <View style={styles.statCard}>
                <Text style={styles.label}>Total Hours</Text>
                <Text style={styles.value}>{earnings.stats.hours}h</Text>
            </View>
            <View style={styles.statCard}>
                <Text style={styles.label}>Unique Students</Text>
                <Text style={styles.value}>{earnings.stats.students}</Text>
            </View>
            <View style={styles.statCard}>
                <Text style={styles.label}>Avg Rate/Hr</Text>
                <Text style={[styles.value, {color: '#28a745'}]}>${earnings.stats.avgRate}</Text>
            </View>
        </View>

        {/* Monthly Chart */}
        <View style={styles.chartCard}>
            <Text style={styles.sectionTitle}>Monthly Revenue</Text>
            <View style={styles.chartContainer}>
                {earnings.monthly.map((m, index) => (
                    <View key={index} style={styles.barGroup}>
                        <View style={styles.barTrack}>
                            <View style={[styles.barFill, { height: `${m.height}%` }]} />
                        </View>
                        <Text style={styles.barLabel}>{m.month}</Text>
                    </View>
                ))}
            </View>
        </View>

        {/* My Students */}
        <Text style={styles.sectionTitle}>My Students</Text>
        <View style={{marginBottom: 20}}>
            {Array.from(new Set(earnings.history.map(b => JSON.stringify(b.student)))).map(s => {
                const student = JSON.parse(s);
                return (
                    <TouchableOpacity 
                        key={student.id} 
                        style={styles.studentCard}
                        onPress={() => navigation.navigate('StudentDetailStats', { student })}
                    >
                        <View style={styles.studentAvatar}>
                            <Text style={styles.studentInitials}>{student.full_name[0]}</Text>
                        </View>
                        <Text style={styles.studentName}>{student.full_name}</Text>
                        <Ionicons name="chevron-forward" size={20} color="#ccc" />
                    </TouchableOpacity>
                );
            })}
        </View>

        {/* Recent Transactions */}
        <Text style={styles.sectionTitle}>Recent Transactions</Text>
        <FlatList
          data={earnings.history}
          keyExtractor={item => item.id.toString()}
          renderItem={renderHistoryItem}
          scrollEnabled={false} // Let parent ScrollView handle it
          ListEmptyComponent={<Text style={styles.emptyText}>No earnings yet.</Text>}
        />
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f8f9fa' },
  scroll: { padding: 20 },
  header: { fontSize: 26, fontWeight: 'bold', marginBottom: 20, color: '#333' },

  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', marginBottom: 20 },
  statCard: {
      width: '48%', backgroundColor: 'white', padding: 15, borderRadius: 12, marginBottom: 15,
      shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 5, elevation: 2
  },
  label: { fontSize: 13, color: '#666', marginBottom: 5 },
  value: { fontSize: 22, fontWeight: 'bold', color: '#333' },

  chartCard: {
      backgroundColor: 'white', padding: 20, borderRadius: 15, marginBottom: 25,
      shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 5, elevation: 2
  },
  sectionTitle: { fontSize: 18, fontWeight: 'bold', marginBottom: 15, color: '#333' },
  chartContainer: { 
      flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end', height: 150 
  },
  barGroup: { alignItems: 'center', width: 30 },
  barTrack: { 
      width: 12, height: 120, backgroundColor: '#f1f1f1', borderRadius: 6, 
      justifyContent: 'flex-end', overflow: 'hidden'
  },
  barFill: { width: '100%', backgroundColor: '#007bff', borderRadius: 6 },
  barLabel: { marginTop: 8, fontSize: 12, color: '#666' },

  transactionRow: {
      flexDirection: 'row', alignItems: 'center', backgroundColor: 'white', 
      padding: 15, borderRadius: 12, marginBottom: 10,
      shadowColor: '#000', shadowOpacity: 0.03, shadowRadius: 3, elevation: 1
  },
  iconBox: {
      width: 40, height: 40, borderRadius: 20, backgroundColor: '#e3f2fd', 
      justifyContent: 'center', alignItems: 'center', marginRight: 15
  },
  transInfo: { flex: 1 },
  transTitle: { fontSize: 15, fontWeight: '600', color: '#333' },
  transDate: { fontSize: 12, color: '#888' },
  transAmount: { fontSize: 16, fontWeight: 'bold', color: '#28a745' },
  
  emptyText: { textAlign: 'center', color: '#999', marginTop: 20 },

  studentCard: {
      flexDirection: 'row', alignItems: 'center', backgroundColor: 'white',
      padding: 15, borderRadius: 12, marginBottom: 10,
      shadowColor: '#000', shadowOpacity: 0.03, shadowRadius: 3
  },
  studentAvatar: {
      width: 40, height: 40, borderRadius: 20, backgroundColor: '#6c757d',
      justifyContent: 'center', alignItems: 'center', marginRight: 15
  },
  studentInitials: { color: 'white', fontWeight: 'bold' },
  studentName: { flex: 1, fontSize: 16, fontWeight: '500', color: '#333' }
});

export default InstructorEarningsScreen;