import React, { useContext, useState, useEffect } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, Image, ActivityIndicator, Dimensions } from 'react-native';
import { AuthContext } from '../context/AuthContext';
import { SafeAreaView } from 'react-native-safe-area-context';
import Ionicons from '@expo/vector-icons/Ionicons';
import client from '../api/client';
import { useIsFocused } from '@react-navigation/native';

const { width } = Dimensions.get('window');

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
      const active = response.data.filter(b => 
        (b.status === 'accepted' || b.status === 'confirmed')
      );
      active.sort((a, b) => new Date(a.date + ' ' + a.time) - new Date(b.date + ' ' + b.time));
      setUpcomingLesson(active.length > 0 ? active[0] : null);

      const pending = response.data.filter(b => b.status === 'pending');
      pending.sort((a, b) => new Date(a.date + ' ' + a.time) - new Date(b.date + ' ' + b.time));
      setPendingRequests(pending);
    } catch (e) {
      console.log("Error fetching bookings", e);
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        
        {/* Header */}
        <View style={styles.header}>
          <View>
            <Text style={styles.greeting}>Good Morning,</Text>
            <Text style={styles.name}>{userInfo?.full_name || 'Driver'}</Text>
          </View>
          <View style={styles.headerActions}>
            <TouchableOpacity onPress={() => navigation.navigate('Notifications')} style={styles.iconBtn}>
              <Ionicons name="notifications-outline" size={24} color="#FFFFFF" />
              {unreadCount > 0 && <View style={styles.badge}><Text style={styles.badgeText}>{unreadCount}</Text></View>}
            </TouchableOpacity>
            <TouchableOpacity onPress={() => navigation.navigate('StudentEditProfile')} style={styles.iconBtn}>
              <Ionicons name="settings-outline" size={24} color="#FFFFFF" />
            </TouchableOpacity>
          </View>
        </View>

        {/* Hero: Next Lesson Card */}
        <Text style={styles.sectionTitle}>Next Lesson</Text>
        {loading ? (
          <ActivityIndicator color="#3B82F6" />
        ) : upcomingLesson ? (
          <TouchableOpacity 
            style={styles.heroCard}
            onPress={() => navigation.navigate('SessionDetail', { bookingId: upcomingLesson.id })}
          >
            <View style={styles.heroGradient} />
            <View style={styles.heroContent}>
              <View>
                <Text style={styles.heroLabel}>TOMORROW</Text>
                <Text style={styles.heroTime}>{upcomingLesson.time}</Text>
                <Text style={styles.heroInstructor}>{upcomingLesson.instructor?.user?.full_name}</Text>
              </View>
              <View style={styles.heroStatus}>
                <Ionicons name="checkmark-circle" size={32} color="#15803D" />
              </View>
            </View>
            <View style={styles.heroFooter}>
              <Ionicons name="location-sharp" size={16} color="rgba(255,255,255,0.6)" />
              <Text style={styles.heroAddress} numberOfLines={1}>{upcomingLesson.pickup_address}</Text>
            </View>
          </TouchableOpacity>
        ) : (
          <TouchableOpacity 
            style={styles.emptyHero}
            onPress={() => navigation.navigate('Find Instructor')}
          >
            <Text style={styles.emptyHeroText}>No lessons scheduled</Text>
            <Text style={styles.emptyHeroLink}>Find an instructor →</Text>
          </TouchableOpacity>
        )}

        {/* Progress Ribbon */}
        <View style={styles.progressSection}>
          <View style={styles.progressHeader}>
            <Text style={styles.progressLabel}>License Readiness</Text>
            <Text style={styles.progressValue}>65%</Text>
          </View>
          <View style={styles.progressTrack}>
            <View style={[styles.progressFill, { width: '65%' }]} />
          </View>
        </View>

        {/* Quick Actions Grid */}
        <Text style={styles.sectionTitle}>Drive Center</Text>
        <View style={styles.grid}>
          <TouchableOpacity style={styles.gridItem} onPress={() => navigation.navigate('DiagnosticRideIntro')}>
            <View style={[styles.iconBg, { backgroundColor: 'rgba(59,130,246,0.15)' }]}>
              <Ionicons name="speedometer" size={24} color="#4338CA" />
            </View>
            <Text style={styles.gridLabel}>Diagnostic</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.gridItem} onPress={() => navigation.navigate('Learn')}>
            <View style={[styles.iconBg, { backgroundColor: 'rgba(245,158,11,0.15)' }]}>
              <Ionicons name="book" size={24} color="#D97706" />
            </View>
            <Text style={styles.gridLabel}>Study</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.gridItem} onPress={() => navigation.navigate('DriveLog')}>
            <View style={[styles.iconBg, { backgroundColor: 'rgba(21,128,61,0.15)' }]}>
              <Ionicons name="car" size={24} color="#15803D" />
            </View>
            <Text style={styles.gridLabel}>Logbook</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.gridItem} onPress={() => navigation.navigate('Quiz')}>
            <View style={[styles.iconBg, { backgroundColor: 'rgba(239,68,68,0.15)' }]}>
              <Ionicons name="checkmark-circle" size={24} color="#B91C1C" />
            </View>
            <Text style={styles.gridLabel}>Practice</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.gridItem} onPress={() => navigation.navigate('DiagnosticRideHistory')}>
            <View style={[styles.iconBg, { backgroundColor: 'rgba(59,130,246,0.15)' }]}>
              <Ionicons name="analytics" size={24} color="#4338CA" />
            </View>
            <Text style={styles.gridLabel}>Ride History</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.gridItem} onPress={() => navigation.navigate('StudentProgress')}>
            <View style={[styles.iconBg, { backgroundColor: 'rgba(21,128,61,0.15)' }]}>
              <Ionicons name="trending-up" size={24} color="#15803D" />
            </View>
            <Text style={styles.gridLabel}>Progress</Text>
          </TouchableOpacity>
        </View>

        {/* Pending Requests */}
        {pendingRequests.length > 0 && (
          <View style={{ marginBottom: 32 }}>
            <Text style={styles.sectionTitle}>Pending Requests</Text>
            {pendingRequests.map((request) => (
              <View key={request.id} style={styles.pendingCard}>
                <View style={styles.pendingHeader}>
                  <Text style={styles.pendingDate}>{request.date}</Text>
                  <Text style={styles.pendingTime}>{request.time}</Text>
                </View>
                <View style={styles.pendingBody}>
                  <View style={styles.pendingRow}>
                    <Ionicons name="person" size={16} color="#64748B" />
                    <Text style={styles.pendingInfo}>{request.instructor?.user?.full_name || `Instructor #${request.instructor_id}`}</Text>
                  </View>
                  <View style={styles.pendingRow}>
                    <Ionicons name="hourglass-outline" size={16} color="#F59E0B" />
                    <Text style={{ marginLeft: 8, color: '#F59E0B', fontWeight: '600', fontStyle: 'italic' }}>Awaiting Approval</Text>
                  </View>
                </View>
                <TouchableOpacity
                  style={styles.pendingMsgBtn}
                  onPress={() => navigation.navigate('Chat', {
                    recipientId: request.instructor?.user_id,
                    name: request.instructor?.user?.full_name || 'Instructor'
                  })}
                >
                  <Ionicons name="chatbubble-outline" size={16} color="white" style={{ marginRight: 6 }} />
                  <Text style={{ color: 'white', fontWeight: '700' }}>Message</Text>
                </TouchableOpacity>
              </View>
            ))}
          </View>
        )}

        <TouchableOpacity style={styles.logoutBtn} onPress={logout}>
          <Text style={styles.logoutText}>Sign Out</Text>
        </TouchableOpacity>

      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0B1326' },
  scroll: { paddingHorizontal: 20, paddingBottom: 100 },
  header: { 
    flexDirection: 'row', 
    justifyContent: 'space-between', 
    alignItems: 'center', 
    marginTop: 20,
    marginBottom: 30 
  },
  greeting: { fontSize: 14, color: '#94A3B8', fontWeight: '500' },
  name: { fontSize: 28, fontWeight: '800', color: '#FFFFFF', marginTop: 2 },
  headerActions: { flexDirection: 'row', gap: 12 },
  iconBtn: { 
    width: 44, 
    height: 44, 
    borderRadius: 22, 
    backgroundColor: '#131B2E', 
    justifyContent: 'center', 
    alignItems: 'center',
  },
  sectionTitle: { 
    fontSize: 18, 
    fontWeight: '800', 
    marginBottom: 16, 
    color: '#FFFFFF',
    letterSpacing: -0.5
  },
  heroCard: {
    backgroundColor: '#1E293B',
    borderRadius: 24,
    padding: 24,
    marginBottom: 32,
    overflow: 'hidden'
  },
  heroGradient: {
    position: 'absolute',
    top: 0, right: 0, bottom: 0, left: 0,
    backgroundColor: 'rgba(21, 128, 61, 0.1)',
  },
  heroContent: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  heroLabel: { color: '#94A3B8', fontSize: 12, fontWeight: '800', letterSpacing: 1 },
  heroTime: { color: 'white', fontSize: 32, fontWeight: '800', marginTop: 4, letterSpacing: -1 },
  heroInstructor: { color: 'white', fontSize: 18, fontWeight: '500', marginTop: 4, opacity: 0.9 },
  heroFooter: { 
    flexDirection: 'row', 
    alignItems: 'center', 
    marginTop: 24, 
    paddingTop: 16, 
    borderTopWidth: 1, 
    borderTopColor: 'rgba(255,255,255,0.1)' 
  },
  heroAddress: { color: 'rgba(255,255,255,0.6)', marginLeft: 8, fontSize: 13 },
  emptyHero: {
    backgroundColor: '#131B2E',
    borderRadius: 24,
    padding: 32,
    alignItems: 'center',
    marginBottom: 32,
    borderWidth: 2,
    borderColor: '#1E293B',
    borderStyle: 'dashed'
  },
  emptyHeroText: { color: '#64748B', fontSize: 16, fontWeight: '600' },
  emptyHeroLink: { color: '#15803D', fontWeight: '800', marginTop: 8 },
  progressSection: { marginBottom: 40 },
  progressHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 12 },
  progressLabel: { fontSize: 15, fontWeight: '700', color: '#FFFFFF' },
  progressValue: { fontSize: 20, fontWeight: '800', color: '#15803D' },
  progressTrack: { height: 12, backgroundColor: '#1E293B', borderRadius: 6, overflow: 'hidden' },
  progressFill: { height: '100%', backgroundColor: '#15803D', borderRadius: 6 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 16, marginBottom: 40 },
  gridItem: { 
    width: (width - 56) / 2, 
    backgroundColor: '#131B2E', 
    padding: 20, 
    borderRadius: 20, 
    alignItems: 'center',
  },
  iconBg: { width: 52, height: 52, borderRadius: 16, justifyContent: 'center', alignItems: 'center', marginBottom: 12 },
  gridLabel: { fontWeight: '700', color: '#FFFFFF', fontSize: 15 },
  badge: {
    position: 'absolute', top: -4, right: -4, backgroundColor: '#EF4444', borderRadius: 10, 
    width: 18, height: 18, justifyContent: 'center', alignItems: 'center', borderWidth: 2, borderColor: '#0B1326'
  },
  badgeText: { color: 'white', fontSize: 10, fontWeight: '900' },
  logoutBtn: { padding: 20, alignItems: 'center' },
  logoutText: { color: '#94A3B8', fontWeight: '700', fontSize: 14 },
  pendingCard: {
    backgroundColor: '#131B2E',
    borderRadius: 20,
    padding: 20,
    marginBottom: 12,
    borderLeftWidth: 4,
    borderLeftColor: '#F59E0B',
  },
  pendingHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  pendingDate: { fontSize: 16, fontWeight: '800', color: '#FFFFFF' },
  pendingTime: { fontSize: 16, fontWeight: '700', color: '#F59E0B' },
  pendingBody: { marginBottom: 12 },
  pendingRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 6 },
  pendingInfo: { marginLeft: 8, color: '#64748B', fontSize: 14, fontWeight: '500' },
  pendingMsgBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#F59E0B',
    paddingVertical: 10,
    borderRadius: 12,
  },
});

export default StudentHomeScreen;
