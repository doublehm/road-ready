import React, { useContext } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';
import { AuthContext } from '../context/AuthContext';
import { SafeAreaView } from 'react-native-safe-area-context';
import Ionicons from '@expo/vector-icons/Ionicons';

const InstructorHomeScreen = ({ navigation }) => {
  const { logout, userInfo, unreadCount, pendingBookingsCount } = useContext(AuthContext);

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView contentContainerStyle={styles.scroll}>
        
        {/* Header */}
        <View style={styles.header}>
          <View>
            <Text style={styles.greeting}>Welcome back,</Text>
            <Text style={styles.name}>{userInfo?.full_name || 'Instructor'}</Text>
          </View>
          <View style={styles.headerIcons}>
            <TouchableOpacity onPress={() => navigation.navigate('Notifications')} style={styles.iconBtn}>
              <View>
                <Ionicons name="notifications-outline" size={24} color="#666" />
                {unreadCount > 0 && (
                  <View style={styles.badge}>
                    <Text style={styles.badgeText}>{unreadCount}</Text>
                  </View>
                )}
              </View>
            </TouchableOpacity>
            <TouchableOpacity onPress={() => navigation.navigate('EditProfile')} style={styles.iconBtn}>
              <Ionicons name="settings-outline" size={24} color="#666" />
            </TouchableOpacity>
            <TouchableOpacity onPress={logout} style={styles.iconBtn}>
              <Ionicons name="log-out-outline" size={24} color="#dc3545" />
            </TouchableOpacity>
          </View>
        </View>

        {/* Stats Row */}
        <View style={styles.statsRow}>
          <TouchableOpacity style={styles.statCard} onPress={() => navigation.navigate('Earnings')}>
            <Text style={styles.statLabel}>Earnings</Text>
            <Text style={styles.statValue}>$0.00</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.statCard} onPress={() => navigation.navigate('Schedule')}>
            <Text style={styles.statLabel}>Today</Text>
            <Text style={styles.statValue}>0 Lessons</Text>
          </TouchableOpacity>
        </View>

        {/* Actions */}
        <Text style={styles.sectionTitle}>Manage Students</Text>
        
        <TouchableOpacity 
          style={styles.actionCard}
          onPress={() => navigation.navigate('BookingRequests')}
        >
          <View style={[styles.iconBg, { backgroundColor: '#fff3cd' }]}>
            <Ionicons name="notifications-outline" size={24} color="#ffc107" />
          </View>
          <View style={styles.actionInfo}>
            <Text style={styles.actionTitle}>Booking Requests</Text>
            <Text style={styles.actionDesc}>Approve incoming lessons</Text>
          </View>
          {pendingBookingsCount > 0 && (
            <View style={styles.cardBadge}>
              <Text style={styles.cardBadgeText}>{pendingBookingsCount}</Text>
            </View>
          )}
          <Ionicons name="chevron-forward" size={20} color="#ccc" />
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.actionCard}
          onPress={() => navigation.navigate('DiagnosticRideHistory')}
        >
          <View style={[styles.iconBg, { backgroundColor: '#e3f2fd' }]}>
            <Ionicons name="car-outline" size={24} color="#007bff" />
          </View>
          <View style={styles.actionInfo}>
            <Text style={styles.actionTitle}>Students Diagnostic Sessions</Text>
            <Text style={styles.actionDesc}>View and review diagnostic rides</Text>
          </View>
          <Ionicons name="chevron-forward" size={20} color="#ccc" />
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.actionCard}
          onPress={() => navigation.navigate('Messages')}
        >
          <View style={[styles.iconBg, { backgroundColor: '#e3f2fd' }]}>
            <Ionicons name="chatbubbles-outline" size={24} color="#007bff" />
          </View>
          <View style={styles.actionInfo}>
            <Text style={styles.actionTitle}>Messages</Text>
            <Text style={styles.actionDesc}>Chat with students</Text>
          </View>
          <Ionicons name="chevron-forward" size={20} color="#ccc" />
        </TouchableOpacity>

        <TouchableOpacity 
          style={styles.actionCard}
          onPress={() => navigation.navigate('Schedule')}
        >
          <View style={[styles.iconBg, { backgroundColor: '#fff3cd' }]}>
            <Ionicons name="calendar-outline" size={24} color="#ffc107" />
          </View>
          <View style={styles.actionInfo}>
            <Text style={styles.actionTitle}>My Schedule</Text>
            <Text style={styles.actionDesc}>View upcoming bookings</Text>
          </View>
          <Ionicons name="chevron-forward" size={20} color="#ccc" />
        </TouchableOpacity>

      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  scroll: { padding: 20 },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 25 },
  greeting: { fontSize: 16, color: '#666' },
  name: { fontSize: 24, fontWeight: 'bold', color: '#333' },
  headerIcons: { flexDirection: 'row' },
  iconBtn: { marginLeft: 15 },

  statsRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 30 },
  statCard: { 
    width: '48%', 
    backgroundColor: '#f8f9fa', 
    padding: 20, 
    borderRadius: 15, 
    borderWidth: 1,
    borderColor: '#eee'
  },
  statLabel: { color: '#666', fontSize: 14, marginBottom: 5 },
  statValue: { fontSize: 24, fontWeight: 'bold', color: '#333' },

  sectionTitle: { fontSize: 18, fontWeight: 'bold', marginBottom: 15, color: '#333' },

  actionCard: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 15,
    backgroundColor: 'white',
    borderRadius: 12,
    marginBottom: 15,
    borderWidth: 1,
    borderColor: '#eee',
    elevation: 2,
    shadowColor: '#000',
    shadowOpacity: 0.05,
    shadowOffset: { width: 0, height: 2 }
  },
  iconBg: { width: 50, height: 50, borderRadius: 25, justifyContent: 'center', alignItems: 'center', marginRight: 15 },
  actionInfo: { flex: 1 },
  actionTitle: { fontSize: 16, fontWeight: 'bold', color: '#333' },
  actionDesc: { fontSize: 14, color: '#888' },
  
  badge: {
    position: 'absolute', right: -6, top: -3, backgroundColor: 'red', borderRadius: 10, width: 20, height: 20, justifyContent: 'center', alignItems: 'center'
  },
  badgeText: { color: 'white', fontSize: 10, fontWeight: 'bold' },
  
  cardBadge: {
    backgroundColor: '#dc3545', borderRadius: 12, paddingHorizontal: 8, paddingVertical: 4, marginRight: 10
  },
  cardBadgeText: { color: 'white', fontSize: 12, fontWeight: 'bold' }
});

export default InstructorHomeScreen;