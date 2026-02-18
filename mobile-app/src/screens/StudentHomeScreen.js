import React, { useContext, useState, useEffect } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, Image, ActivityIndicator } from 'react-native';
import { AuthContext } from '../context/AuthContext';
import { SafeAreaView } from 'react-native-safe-area-context';
import Ionicons from 'react-native-vector-icons/Ionicons';
import client from '../api/client';
import { useIsFocused } from '@react-navigation/native'; // Refresh when screen comes into focus

const StudentHomeScreen = ({ navigation }) => {
  const { logout, userInfo, unreadCount } = useContext(AuthContext);
  const [upcomingLesson, setUpcomingLesson] = useState(null);
  const [pendingRequests, setPendingRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const isFocused = useIsFocused();

  useEffect(() => {
    if (isFocused) {
      fetchBookings();
    }
  }, [isFocused]);

  const fetchBookings = async () => {
    try {
      const response = await client.get('/bookings/');
      
      // Upcoming: Status is 'accepted' or 'confirmed'
      const active = response.data.filter(b => 
        (b.status === 'accepted' || b.status === 'confirmed')
      );
      // Sort by date (nearest first)
      active.sort((a, b) => new Date(a.date + ' ' + a.time) - new Date(b.date + ' ' + b.time));
      setUpcomingLesson(active.length > 0 ? active[0] : null);

      // Pending: Status is 'pending'
      const pending = response.data.filter(b => b.status === 'pending');
      // Sort by date (nearest first)
      pending.sort((a, b) => new Date(a.date + ' ' + a.time) - new Date(b.date + ' ' + b.time));
      setPendingRequests(pending);

    } catch (e) {
      console.log("Error fetching bookings", e);
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scroll}>
        
        {/* Header */}
        <View style={styles.header}>
          <View>
            <Text style={styles.greeting}>Hello,</Text>
            <Text style={styles.name}>{userInfo?.full_name || 'Student'}</Text>
          </View>
          <View style={{flexDirection: 'row'}}>
            <TouchableOpacity onPress={() => navigation.navigate('Notifications')} style={{marginRight: 15}}>
              <Ionicons name="notifications-outline" size={24} color="#666" />
              {unreadCount > 0 && (
                  <View style={styles.badge}>
                      <Text style={styles.badgeText}>{unreadCount}</Text>
                  </View>
              )}
            </TouchableOpacity>
            <TouchableOpacity onPress={() => navigation.navigate('StudentEditProfile')} style={{marginRight: 15}}>
              <Ionicons name="settings-outline" size={24} color="#666" />
            </TouchableOpacity>
            <TouchableOpacity onPress={logout}>
              <Ionicons name="log-out-outline" size={24} color="#dc3545" />
            </TouchableOpacity>
          </View>
        </View>

        {/* Progress Card */}
        <View style={styles.progressCard}>
          <View>
            <Text style={styles.progressTitle}>Your Progress</Text>
            <Text style={styles.progressSubtitle}>Ready for the road?</Text>
          </View>
          <TouchableOpacity 
            style={styles.progressBtn}
            onPress={() => navigation.navigate('StudentProgress')}
          >
            <Text style={styles.progressBtnText}>View Stats</Text>
          </TouchableOpacity>
        </View>

        {/* Quick Actions Grid */}
        <Text style={styles.sectionTitle}>Quick Actions</Text>
        <View style={styles.grid}>
          
          <TouchableOpacity 
            style={styles.gridItem} 
            onPress={() => navigation.navigate('Find Instructor')}
          >
            <View style={[styles.iconBg, { backgroundColor: '#e3f2fd' }]}>
              <Ionicons name="search" size={28} color="#007bff" />
            </View>
            <Text style={styles.gridLabel}>Book Lesson</Text>
          </TouchableOpacity>

          <TouchableOpacity 
            style={styles.gridItem} 
            onPress={() => navigation.navigate('Learn')}
          >
            <View style={[styles.iconBg, { backgroundColor: '#fff3cd' }]}>
              <Ionicons name="book" size={28} color="#ffc107" />
            </View>
            <Text style={styles.gridLabel}>Study Guide</Text>
          </TouchableOpacity>

          <TouchableOpacity 
            style={styles.gridItem} 
            onPress={() => navigation.navigate('Quiz')}
          >
            <View style={[styles.iconBg, { backgroundColor: '#d4edda' }]}>
              <Ionicons name="checkmark-circle" size={28} color="#28a745" />
            </View>
            <Text style={styles.gridLabel}>Practice Quiz</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.gridItem}
            onPress={() => navigation.navigate('DriveLog')}
          >
            <View style={[styles.iconBg, { backgroundColor: '#f8d7da' }]}>
              <Ionicons name="car" size={28} color="#dc3545" />
            </View>
            <Text style={styles.gridLabel}>Drive Log</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.gridItem}
            onPress={() => navigation.navigate('DiagnosticRideIntro')}
          >
            <View style={[styles.iconBg, { backgroundColor: '#e0d9f7' }]}>
              <Ionicons name="speedometer" size={28} color="#6610f2" />
            </View>
            <Text style={styles.gridLabel}>Diagnostic Ride</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.gridItem}
            onPress={() => navigation.navigate('DiagnosticRideHistory')}
          >
            <View style={[styles.iconBg, { backgroundColor: '#d1ecf1' }]}>
              <Ionicons name="analytics" size={28} color="#0c5460" />
            </View>
            <Text style={styles.gridLabel}>Ride History</Text>
          </TouchableOpacity>

        </View>

        {/* Pending Requests */}
        {pendingRequests.length > 0 && (
          <View style={{marginBottom: 20}}>
            <Text style={styles.sectionTitle}>Pending Requests</Text>
            {pendingRequests.map((request) => (
              <View key={request.id} style={[styles.lessonCard, {borderColor: '#ffc107', borderLeftWidth: 4}]}>
                <View style={styles.lessonHeader}>
                  <Text style={styles.lessonDate}>{request.date}</Text>
                  <Text style={[styles.lessonTime, {color: '#856404'}]}>{request.time}</Text>
                </View>
                <View style={styles.lessonBody}>
                  <View style={styles.row}>
                    <Ionicons name="person" size={16} color="#666" />
                    <Text style={styles.lessonInfo}>{request.instructor?.user?.full_name || `Instructor #${request.instructor_id}`}</Text>
                  </View>
                  <View style={styles.row}>
                    <Ionicons name="hourglass-outline" size={16} color="#ffc107" />
                    <Text style={{marginLeft: 8, color: '#856404', fontStyle: 'italic'}}>Awaiting Approval</Text>
                  </View>
                </View>
                <View style={styles.actionRow}>
                  <TouchableOpacity 
                    style={[styles.messageBtn, {backgroundColor: '#ffc107'}]}
                    onPress={() => navigation.navigate('Chat', { 
                      recipientId: request.instructor?.user_id, 
                      name: request.instructor?.user?.full_name || 'Instructor' 
                    })}
                  >
                    <Ionicons name="chatbubble-outline" size={16} color="black" style={{marginRight: 5}} />
                    <Text style={[styles.messageBtnText, {color: 'black'}]}>Message</Text>
                  </TouchableOpacity>
                </View>
              </View>
            ))}
          </View>
        )}

        {/* Recent Activity */}
        <Text style={styles.sectionTitle}>Upcoming Lesson</Text>
        
        {loading ? (
          <ActivityIndicator color="#007bff" />
        ) : upcomingLesson ? (
          <View style={styles.lessonCard}>
            <View style={styles.lessonHeader}>
              <Text style={styles.lessonDate}>{upcomingLesson.date}</Text>
              <Text style={styles.lessonTime}>{upcomingLesson.time}</Text>
            </View>
            <View style={styles.lessonBody}>
              <View style={styles.row}>
                <Ionicons name="person" size={16} color="#666" />
                <Text style={styles.lessonInfo}>{upcomingLesson.instructor?.user?.full_name || `Instructor #${upcomingLesson.instructor_id}`}</Text>
              </View>
              <View style={styles.row}>
                <Ionicons name="location" size={16} color="#666" />
                <Text style={styles.lessonInfo} numberOfLines={1}>{upcomingLesson.pickup_address}</Text>
              </View>
            </View>
            <View style={styles.actionRow}>
              <TouchableOpacity style={styles.lessonAction}>
                <Text style={styles.lessonActionText}>View Details</Text>
              </TouchableOpacity>
              <TouchableOpacity 
                style={styles.messageBtn}
                onPress={() => navigation.navigate('Chat', { 
                  recipientId: upcomingLesson.instructor?.user_id, 
                  name: upcomingLesson.instructor?.user?.full_name || 'Instructor' 
                })}
              >
                <Ionicons name="chatbubble-outline" size={16} color="white" style={{marginRight: 5}} />
                <Text style={styles.messageBtnText}>Message</Text>
              </TouchableOpacity>
            </View>
          </View>
        ) : (
          <View style={styles.emptyState}>
            <Text style={styles.emptyText}>No upcoming lessons scheduled.</Text>
            <TouchableOpacity onPress={() => navigation.navigate('Find Instructor')}>
              <Text style={styles.linkText}>Find an instructor now</Text>
            </TouchableOpacity>
          </View>
        )}

      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  scroll: { padding: 20 },
  header: { 
    flexDirection: 'row', 
    justifyContent: 'space-between', 
    alignItems: 'center', 
    marginBottom: 25 
  },
  greeting: { fontSize: 16, color: '#666' },
  name: { fontSize: 24, fontWeight: 'bold', color: '#333' },
  
  progressCard: {
    backgroundColor: '#007bff',
    borderRadius: 15,
    padding: 20,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 30,
    shadowColor: '#007bff',
    shadowOpacity: 0.3,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 4 },
    elevation: 5
  },
  progressTitle: { color: 'white', fontSize: 18, fontWeight: 'bold' },
  progressSubtitle: { color: 'rgba(255,255,255,0.8)', fontSize: 14 },
  progressBtn: { backgroundColor: 'white', paddingVertical: 8, paddingHorizontal: 15, borderRadius: 20 },
  progressBtnText: { color: '#007bff', fontWeight: 'bold', fontSize: 12 },

  sectionTitle: { fontSize: 18, fontWeight: 'bold', marginBottom: 15, color: '#333' },
  
  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', marginBottom: 20 },
  gridItem: { 
    width: '48%', 
    backgroundColor: '#f8f9fa', 
    padding: 15, 
    borderRadius: 15, 
    alignItems: 'center', 
    marginBottom: 15,
    borderWidth: 1,
    borderColor: '#eee'
  },
  iconBg: { width: 50, height: 50, borderRadius: 25, justifyContent: 'center', alignItems: 'center', marginBottom: 10 },
  gridLabel: { fontWeight: '600', color: '#333' },

  emptyState: { alignItems: 'center', padding: 20, backgroundColor: '#f9f9f9', borderRadius: 10 },
  emptyText: { color: '#888', marginBottom: 5 },
  linkText: { color: '#007bff', fontWeight: 'bold' },

  lessonCard: {
    backgroundColor: 'white',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#e0e0e0',
    padding: 15,
    shadowColor: '#000',
    shadowOpacity: 0.05,
    shadowRadius: 5,
    elevation: 2
  },
  lessonHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#f0f0f0',
    paddingBottom: 8
  },
  lessonDate: { fontWeight: 'bold', fontSize: 16, color: '#333' },
  lessonTime: { color: '#007bff', fontWeight: 'bold' },
  lessonBody: { marginBottom: 10 },
  row: { flexDirection: 'row', alignItems: 'center', marginBottom: 5 },
  lessonInfo: { marginLeft: 8, color: '#555' },
  actionRow: { 
    flexDirection: 'row', 
    justifyContent: 'space-between', 
    alignItems: 'center',
    marginTop: 10,
    borderTopWidth: 1,
    borderTopColor: '#f0f0f0',
    paddingTop: 10
  },
  lessonAction: { alignItems: 'center' },
  lessonActionText: { color: '#007bff', fontWeight: '600' },
  
  messageBtn: {
    flexDirection: 'row',
    backgroundColor: '#007bff',
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 20,
    alignItems: 'center'
  },
  messageBtnText: { color: 'white', fontWeight: 'bold', fontSize: 12 },

  badge: {
      position: 'absolute', top: -5, right: -5, backgroundColor: 'red', borderRadius: 8, 
      width: 16, height: 16, justifyContent: 'center', alignItems: 'center'
  },
  badgeText: { color: 'white', fontSize: 10, fontWeight: 'bold' }
});

export default StudentHomeScreen;
