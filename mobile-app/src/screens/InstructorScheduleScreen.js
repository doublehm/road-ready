import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, FlatList, TouchableOpacity, ActivityIndicator } from 'react-native';
import client from '../api/client';
import { SafeAreaView } from 'react-native-safe-area-context';
import Ionicons from 'react-native-vector-icons/Ionicons';

const InstructorScheduleScreen = ({ navigation }) => {
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
        <Text style={styles.date}>{item.date}</Text>
        <Text style={[
          styles.status, 
          item.status === 'accepted' ? styles.statusActive : 
          item.status === 'completed' ? styles.statusCompleted : 
          styles.statusInactive
        ]}>
          {item.status.toUpperCase()}
        </Text>
      </View>
      
      <View style={styles.row}>
        <Ionicons name="time-outline" size={16} color="#666" />
        <Text style={styles.info}>{item.time} ({item.duration}h)</Text>
      </View>
      
      <View style={styles.row}>
        <Ionicons name="person-outline" size={16} color="#666" />
        <Text style={styles.info}>{item.student?.full_name || `Student #${item.student_id}`}</Text>
      </View>

      <View style={styles.row}>
        <Ionicons name="location-outline" size={16} color="#666" />
        <Text style={styles.info} numberOfLines={1}>{item.pickup_address}</Text>
      </View>

      {item.notes ? (
        <View style={styles.noteBox}>
          <Text style={styles.noteText}>"{item.notes}"</Text>
        </View>
      ) : null}

      {/* Message Button */}
      {activeTab === 'upcoming' && (
        <TouchableOpacity 
          style={styles.messageBtn}
          onPress={() => navigation.navigate('Chat', { 
            recipientId: item.student_id, 
            name: item.student?.full_name || 'Student' 
          })}
        >
          <Ionicons name="chatbubble-outline" size={16} color="#007bff" style={{marginRight: 5}} />
          <Text style={styles.messageBtnText}>Message Student</Text>
        </TouchableOpacity>
      )}
    </View>
  );

  if (loading) {
    return <ActivityIndicator size="large" style={{ flex: 1 }} />;
  }

  const data = getFilteredBookings();

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <Text style={styles.header}>My Schedule</Text>
      
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
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Ionicons name="calendar-clear-outline" size={50} color="#ccc" />
            <Text style={styles.emptyText}>
              {activeTab === 'upcoming' ? "No upcoming lessons." : "No lesson history."}
            </Text>
          </View>
        }
      />
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f5f5f5' },
  header: { fontSize: 24, fontWeight: 'bold', padding: 20, backgroundColor: 'white' },
  
  tabContainer: { flexDirection: 'row', backgroundColor: 'white', paddingHorizontal: 20, paddingBottom: 10 },
  tab: { marginRight: 20, paddingBottom: 8 },
  activeTab: { borderBottomWidth: 2, borderBottomColor: '#007bff' },
  tabText: { fontSize: 16, color: '#666', fontWeight: '500' },
  activeTabText: { color: '#007bff' },

  list: { padding: 16 },
  card: {
    backgroundColor: 'white',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
    shadowColor: '#000',
    shadowOpacity: 0.05,
    shadowOffset: { width: 0, height: 2 },
    shadowRadius: 4,
    elevation: 2,
    borderLeftWidth: 4,
    borderLeftColor: '#007bff'
  },
  cardHistory: { borderLeftColor: '#6c757d', opacity: 0.8 },
  
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 12 },
  date: { fontSize: 18, fontWeight: 'bold', color: '#333' },
  
  status: { fontSize: 12, fontWeight: 'bold', paddingHorizontal: 8, paddingVertical: 2, borderRadius: 4, overflow: 'hidden' },
  statusActive: { backgroundColor: '#e3f2fd', color: '#007bff' },
  statusCompleted: { backgroundColor: '#d4edda', color: '#28a745' },
  statusInactive: { backgroundColor: '#f8f9fa', color: '#666' },

  row: { flexDirection: 'row', alignItems: 'center', marginBottom: 6 },
  info: { fontSize: 15, color: '#555', marginLeft: 8 },
  
  noteBox: { marginTop: 10, backgroundColor: '#fff3cd', padding: 8, borderRadius: 6 },
  noteText: { fontStyle: 'italic', color: '#856404', fontSize: 13 },

  messageBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 10,
    marginTop: 15,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#007bff',
    backgroundColor: '#fff'
  },
  messageBtnText: { color: '#007bff', fontWeight: 'bold', fontSize: 14 },

  emptyContainer: { alignItems: 'center', marginTop: 50 },
  emptyText: { marginTop: 10, fontSize: 16, color: '#999' }
});

export default InstructorScheduleScreen;