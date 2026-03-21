import React, { useState, useEffect, useContext } from 'react';
import { View, Text, StyleSheet, FlatList, TouchableOpacity, ActivityIndicator } from 'react-native';
import client from '../api/client';
import { SafeAreaView } from 'react-native-safe-area-context';
import Ionicons from '@expo/vector-icons/Ionicons';
import { AuthContext } from '../context/AuthContext';

const InstructorScheduleScreen = ({ navigation }) => {
  const { userInfo } = useContext(AuthContext);
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('upcoming'); // 'upcoming' or 'history'

  useEffect(() => {
    fetchSchedule();
  }, []);

  const fetchSchedule = async () => {
    try {
      const response = await client.get('/bookings/');
      setBookings(response.data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  const getFilteredBookings = () => {
    const now = new Date();
    
    if (activeTab === 'upcoming') {
      return bookings.filter(b => 
        // Status is accepted OR pending approval (if you want to show them here too, but usually Requests screen handles pending)
        // Let's show Accepted and Active.
        (b.status === 'accepted' || b.status === 'confirmed')
      ).sort((a, b) => new Date(a.date + 'T' + a.time) - new Date(b.date + 'T' + b.time));
    } else {
      return bookings.filter(b => 
        // History: Completed, Rejected, Cancelled
        ['completed', 'rejected', 'cancelled'].includes(b.status)
      ).sort((a, b) => new Date(b.date + 'T' + b.time) - new Date(a.date + 'T' + a.time)); // Newest first
    }
  };

  const renderItem = ({ item }) => (
    <View style={[styles.card, activeTab === 'history' && styles.cardHistory]}>
      <View style={styles.cardHeader}>
        <Text style={styles.date}>{new Date(item.date).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })}</Text>
        <View style={[
          styles.statusBadge, 
          item.status === 'accepted' ? styles.statusActive : 
          item.status === 'completed' ? styles.statusCompleted : 
          styles.statusInactive
        ]}>
          <Text style={[
            styles.statusText,
            item.status === 'accepted' ? styles.statusActiveText : 
            item.status === 'completed' ? styles.statusCompletedText : 
            styles.statusInactiveText
          ]}>
            {item.status.toUpperCase()}
          </Text>
        </View>
      </View>
      
      <View style={styles.infoGrid}>
        <View style={styles.row}>
          <Ionicons name="time-sharp" size={16} color="#94A3B8" />
          <Text style={styles.info}>{item.time} ({item.duration}h)</Text>
        </View>
        
        <View style={styles.row}>
          <Ionicons name="person-sharp" size={16} color="#94A3B8" />
          <Text style={styles.info}>{item.student?.full_name || `Student #${item.student_id}`}</Text>
        </View>

        <View style={styles.row}>
          <Ionicons name="location-sharp" size={16} color="#94A3B8" />
          <Text style={styles.info} numberOfLines={1}>{item.pickup_address}</Text>
        </View>
      </View>

      {item.notes && item.notes.includes('Diagnostic Ride') ? (
        <View style={styles.diagNoteBox}>
          <Ionicons name="speedometer" size={16} color="#4338CA" />
          <Text style={styles.diagNoteText}>DIAGNOSTIC TELEMETRY SESSION</Text>
        </View>
      ) : item.notes ? (
        <View style={styles.noteBox}>
          <Text style={styles.noteText}>"{item.notes}"</Text>
        </View>
      ) : null}

      <View style={styles.cardActions}>
        {activeTab === 'upcoming' && (
          <TouchableOpacity
            style={styles.messageBtn}
            onPress={() => navigation.navigate('Chat', {
              recipientId: item.student_id,
              name: item.student?.full_name || 'Student'
            })}
          >
            <Ionicons name="chatbubble-ellipses-outline" size={18} color="#1E293B" />
            <Text style={styles.messageBtnText}>Message</Text>
          </TouchableOpacity>
        )}

        {activeTab === 'upcoming' && item.status === 'accepted' && item.notes && item.notes.includes('Diagnostic Ride') && (
          <TouchableOpacity
            style={styles.startRideBtn}
            onPress={() => navigation.navigate('SupervisorHandoff', {
              rideParams: {
                rideType: 'instructor',
                parentName: null,
                instructorId: userInfo?.instructor_profile?.id,
                bookingId: item.id,
                studentId: item.student_id,
              }
            })}
          >
            <Ionicons name="play-circle" size={18} color="#fff" />
            <Text style={styles.startRideBtnText}>Start Diagnostic</Text>
          </TouchableOpacity>
        )}
      </View>
    </View>
  );

  if (loading) {
    return (
      <View style={styles.loaderContainer}>
        <ActivityIndicator size="large" color="#1E293B" />
      </View>
    );
  }

  const data = getFilteredBookings();

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Operational Schedule</Text>
        <Text style={styles.headerSubtitle}>MISSION LOGS</Text>
      </View>
      
      {/* Tabs */}
      <View style={styles.tabContainer}>
        <TouchableOpacity 
          style={[styles.tab, activeTab === 'upcoming' && styles.activeTab]}
          onPress={() => setActiveTab('upcoming')}
        >
          <Text style={[styles.tabText, activeTab === 'upcoming' && styles.activeTabText]}>Upcoming</Text>
        </TouchableOpacity>
        <TouchableOpacity 
          style={[styles.tab, activeTab === 'history' && styles.activeTab]}
          onPress={() => setActiveTab('history')}
        >
          <Text style={[styles.tabText, activeTab === 'history' && styles.activeTabText]}>History</Text>
        </TouchableOpacity>
      </View>

      <FlatList
        data={data}
        keyExtractor={(item) => item.id.toString()}
        renderItem={renderItem}
        contentContainerStyle={styles.list}
        showsVerticalScrollIndicator={false}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Ionicons name="calendar-outline" size={64} color="#CBD5E1" />
            <Text style={styles.emptyText}>
              {activeTab === 'upcoming' ? "No upcoming mission parameters." : "No historical mission logs."}
            </Text>
          </View>
        }
      />
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F6FAFE' },
  header: { padding: 24, backgroundColor: 'white' },
  headerTitle: { fontSize: 28, fontWeight: '800', color: '#1E293B', letterSpacing: -1 },
  headerSubtitle: { fontSize: 11, fontWeight: '800', color: '#15803D', letterSpacing: 2, marginTop: 4 },
  loaderContainer: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#F6FAFE' },
  
  tabContainer: { 
    flexDirection: 'row', 
    backgroundColor: 'white', 
    paddingHorizontal: 24, 
    paddingBottom: 4,
    gap: 24
  },
  tab: { paddingBottom: 12 },
  activeTab: { borderBottomWidth: 3, borderBottomColor: '#1E293B' },
  tabText: { fontSize: 15, color: '#94A3B8', fontWeight: '700' },
  activeTabText: { color: '#1E293B' },

  list: { padding: 24, paddingBottom: 100 },
  card: {
    backgroundColor: 'white',
    borderRadius: 24,
    padding: 24,
    marginBottom: 20,
    shadowColor: '#1E293B',
    shadowOpacity: 0.04,
    shadowRadius: 15,
    elevation: 2,
  },
  cardHistory: { opacity: 0.7 },
  
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 },
  date: { fontSize: 18, fontWeight: '800', color: '#1E293B', letterSpacing: -0.5 },
  
  statusBadge: { paddingHorizontal: 10, paddingVertical: 4, borderRadius: 8 },
  statusText: { fontSize: 10, fontWeight: '900' },
  statusActive: { backgroundColor: '#DCFCE7' },
  statusActiveText: { color: '#15803D' },
  statusCompleted: { backgroundColor: '#F1F5F9' },
  statusCompletedText: { color: '#64748B' },
  statusInactive: { backgroundColor: '#FEE2E2' },
  statusInactiveText: { color: '#EF4444' },

  infoGrid: { gap: 10, marginBottom: 20 },
  row: { flexDirection: 'row', alignItems: 'center' },
  info: { fontSize: 15, color: '#64748B', marginLeft: 10, fontWeight: '600' },
  
  diagNoteBox: { 
    flexDirection: 'row', 
    alignItems: 'center', 
    backgroundColor: '#E0E7FF', 
    padding: 12, 
    borderRadius: 12,
    gap: 8,
    marginBottom: 16
  },
  diagNoteText: { color: '#4338CA', fontSize: 11, fontWeight: '800', letterSpacing: 0.5 },
  
  noteBox: { backgroundColor: '#F8FAFC', padding: 12, borderRadius: 12, marginBottom: 16 },
  noteText: { fontStyle: 'italic', color: '#64748B', fontSize: 14, fontWeight: '500' },

  cardActions: { flexDirection: 'row', gap: 12 },
  messageBtn: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 12,
    borderRadius: 12,
    backgroundColor: '#F1F5F9',
    gap: 8
  },
  messageBtnText: { color: '#1E293B', fontWeight: '800', fontSize: 14 },

  startRideBtn: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 12,
    borderRadius: 12,
    backgroundColor: '#1E293B',
    gap: 8
  },
  startRideBtnText: { color: '#fff', fontWeight: '800', fontSize: 14 },

  emptyContainer: { alignItems: 'center', marginTop: 80 },
  emptyText: { marginTop: 16, fontSize: 15, color: '#94A3B8', fontWeight: '600', textAlign: 'center' }
});

export default InstructorScheduleScreen;