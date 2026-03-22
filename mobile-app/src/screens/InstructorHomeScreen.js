import React, { useContext } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';
import { AuthContext } from '../context/AuthContext';
import { SafeAreaView } from 'react-native-safe-area-context';
import Ionicons from '@expo/vector-icons/Ionicons';

const InstructorHomeScreen = ({ navigation }) => {
  const { logout, userInfo, unreadCount, pendingBookingsCount } = useContext(AuthContext);

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        
        {/* Header */}
        <View style={styles.header}>
          <View>
            <Text style={styles.greeting}>Welcome back,</Text>
            <Text style={styles.name}>{userInfo?.full_name || 'Instructor'}</Text>
          </View>
          <View style={styles.headerActions}>
            <TouchableOpacity onPress={() => navigation.navigate('Notifications')} style={styles.iconBtn}>
              <Ionicons name="notifications-outline" size={24} color="#E2E8F0" />
              {unreadCount > 0 && <View style={styles.badge}><Text style={styles.badgeText}>{unreadCount}</Text></View>}
            </TouchableOpacity>
            <TouchableOpacity onPress={() => navigation.navigate('EditProfile')} style={styles.iconBtn}>
              <Ionicons name="settings-outline" size={24} color="#E2E8F0" />
            </TouchableOpacity>
            <TouchableOpacity onPress={logout} style={styles.iconBtn}>
              <Ionicons name="log-out-outline" size={24} color="#EF4444" />
            </TouchableOpacity>
          </View>
        </View>

        {/* Stats Row */}
        <View style={styles.statsRow}>
          <TouchableOpacity style={styles.statCard} onPress={() => navigation.navigate('Earnings')}>
            <Text style={styles.statLabel}>EARNINGS</Text>
            <Text style={styles.statValue}>$0.00</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.statCard} onPress={() => navigation.navigate('Schedule')}>
            <Text style={styles.statLabel}>TODAY</Text>
            <Text style={[styles.statValue, { color: '#15803D' }]}>0 Lessons</Text>
          </TouchableOpacity>
        </View>

        {/* Actions */}
        <Text style={styles.sectionTitle}>Fleet Management</Text>
        
        <TouchableOpacity 
          style={styles.actionCard}
          onPress={() => navigation.navigate('BookingRequests')}
        >
          <View style={[styles.iconBg, { backgroundColor: 'rgba(245,158,11,0.15)' }]}>
            <Ionicons name="mail-unread-outline" size={24} color="#D97706" />
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
          <Ionicons name="chevron-forward" size={20} color="#94A3B8" />
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.actionCard}
          onPress={() => navigation.navigate('DiagnosticRideHistory')}
        >
          <View style={[styles.iconBg, { backgroundColor: 'rgba(59,130,246,0.15)' }]}>
            <Ionicons name="speedometer-outline" size={24} color="#4338CA" />
          </View>
          <View style={styles.actionInfo}>
            <Text style={styles.actionTitle}>Diagnostic Telemetry</Text>
            <Text style={styles.actionDesc}>Review student performance</Text>
          </View>
          <Ionicons name="chevron-forward" size={20} color="#94A3B8" />
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.actionCard}
          onPress={() => navigation.navigate('Messages')}
        >
          <View style={[styles.iconBg, { backgroundColor: 'rgba(21,128,61,0.15)' }]}>
            <Ionicons name="chatbubbles-outline" size={24} color="#15803D" />
          </View>
          <View style={styles.actionInfo}>
            <Text style={styles.actionTitle}>Communication Hub</Text>
            <Text style={styles.actionDesc}>Chat with active students</Text>
          </View>
          <Ionicons name="chevron-forward" size={20} color="#94A3B8" />
        </TouchableOpacity>

        <TouchableOpacity 
          style={styles.actionCard}
          onPress={() => navigation.navigate('Schedule')}
        >
          <View style={[styles.iconBg, { backgroundColor: '#1E293B' }]}>
            <Ionicons name="calendar-outline" size={24} color="#E2E8F0" />
          </View>
          <View style={styles.actionInfo}>
            <Text style={styles.actionTitle}>Operational Schedule</Text>
            <Text style={styles.actionDesc}>View and manage bookings</Text>
          </View>
          <Ionicons name="chevron-forward" size={20} color="#94A3B8" />
        </TouchableOpacity>

        <View style={{height: 100}} />
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0B1326' },
  scroll: { padding: 24 },
  header: { 
    flexDirection: 'row', 
    justifyContent: 'space-between', 
    alignItems: 'center', 
    marginBottom: 32,
    marginTop: 10
  },
  greeting: { fontSize: 14, color: '#64748B', fontWeight: '600' },
  name: { fontSize: 28, fontWeight: '800', color: '#FFFFFF', marginTop: 2, letterSpacing: -0.5 },
  headerActions: { flexDirection: 'row', gap: 12 },
  iconBtn: { 
    width: 44, 
    height: 44, 
    borderRadius: 22, 
    backgroundColor: '#131B2E', 
    justifyContent: 'center', 
    alignItems: 'center',
  },

  statsRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 40 },
  statCard: { 
    width: '48%', 
    backgroundColor: '#131B2E', 
    padding: 24, 
    borderRadius: 24, 
  },
  statLabel: { color: '#94A3B8', fontSize: 10, fontWeight: '800', marginBottom: 8, letterSpacing: 1 },
  statValue: { fontSize: 22, fontWeight: '800', color: '#FFFFFF', letterSpacing: -0.5 },

  sectionTitle: { fontSize: 18, fontWeight: '800', marginBottom: 20, color: '#FFFFFF', letterSpacing: -0.5 },

  actionCard: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 20,
    backgroundColor: '#131B2E',
    borderRadius: 24,
    marginBottom: 16,
  },
  iconBg: { width: 56, height: 56, borderRadius: 20, justifyContent: 'center', alignItems: 'center', marginRight: 20 },
  actionInfo: { flex: 1 },
  actionTitle: { fontSize: 16, fontWeight: '800', color: '#FFFFFF', letterSpacing: -0.3 },
  actionDesc: { fontSize: 13, color: '#64748B', marginTop: 2, fontWeight: '500' },
  
  badge: {
    position: 'absolute', top: -4, right: -4, backgroundColor: '#EF4444', borderRadius: 10, 
    width: 18, height: 18, justifyContent: 'center', alignItems: 'center', borderWidth: 2, borderColor: '#131B2E'
  },
  badgeText: { color: 'white', fontSize: 10, fontWeight: '900' },
  
  cardBadge: {
    backgroundColor: '#EF4444', borderRadius: 8, paddingHorizontal: 8, paddingVertical: 4, marginRight: 12
  },
  cardBadgeText: { color: 'white', fontSize: 12, fontWeight: '900' }
});

export default InstructorHomeScreen;