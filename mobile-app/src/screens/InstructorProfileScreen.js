import React from 'react';
import { View, Text, StyleSheet, Image, ScrollView, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Ionicons from '@expo/vector-icons/Ionicons';

const InstructorProfileScreen = ({ route, navigation }) => {
  const { instructor, forDiagnosticRide } = route.params;

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView>
        {/* Header Image / Avatar */}
        <View style={styles.header}>
          <View style={styles.avatar}>
            <Text style={styles.avatarText}>{instructor.user?.full_name?.[0] || 'I'}</Text>
          </View>
          <View style={{flexDirection: 'row', alignItems: 'center'}}>
            <Text style={styles.name}>{instructor.user?.full_name || 'Instructor'}</Text>
            {instructor.is_verified && (
              <Ionicons name="checkmark-circle" size={24} color="#3B82F6" style={{marginLeft: 8}} />
            )}
          </View>
          {instructor.is_verified && (
            <Text style={{color: '#3B82F6', fontWeight: 'bold', fontSize: 12, marginTop: 4}}>
              VERIFIED ACCOUNT
            </Text>
          )}
          <Text style={styles.city}><Ionicons name="location" size={16} /> {instructor.city}</Text>
        </View>

        {/* Stats */}
        <View style={styles.statsRow}>
          <View style={styles.stat}>
            <Text style={styles.statVal}>${instructor.hourly_rate}</Text>
            <Text style={styles.statLabel}>Per Hour</Text>
          </View>
          <View style={[styles.stat, styles.statBorder]}>
            <Text style={styles.statVal}>⭐ {instructor.rating || 'New'}</Text>
            <Text style={styles.statLabel}>Rating</Text>
          </View>
          <View style={styles.stat}>
            <Text style={styles.statVal}>{instructor.car_model || 'N/A'}</Text>
            <Text style={styles.statLabel}>Vehicle</Text>
          </View>
        </View>

        {/* Bio */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>About Me</Text>
          <Text style={styles.bio}>{instructor.bio || "No bio provided."}</Text>
        </View>

        {/* Reviews Placeholder */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Reviews</Text>
          <Text style={styles.reviewsText}>No reviews yet.</Text>
        </View>

      </ScrollView>

      {/* Bottom Action */}
      <View style={styles.footer}>
        <View>
          <Text style={styles.price}>${instructor.hourly_rate}/hr</Text>
          <Text style={styles.subtext}>Total for 2 hours: ${instructor.hourly_rate * 2}</Text>
        </View>
        <TouchableOpacity 
          style={styles.bookButton}
          onPress={() => navigation.navigate('BookingFlow', { instructor, forDiagnosticRide })}
        >
          <Text style={styles.bookButtonText}>Book Session</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0B1326' },
  header: { alignItems: 'center', padding: 30, backgroundColor: '#0B1326' },
  avatar: { width: 80, height: 80, borderRadius: 40, backgroundColor: '#3B82F6', justifyContent: 'center', alignItems: 'center', marginBottom: 15 },
  avatarText: { color: 'white', fontSize: 32, fontWeight: 'bold' },
  name: { fontSize: 24, fontWeight: 'bold', color: '#FFFFFF' },
  city: { fontSize: 16, color: '#94A3B8', marginTop: 5 },
  
  statsRow: { flexDirection: 'row', padding: 20, borderBottomWidth: 1, borderBottomColor: '#1E293B' },
  stat: { flex: 1, alignItems: 'center' },
  statBorder: { borderLeftWidth: 1, borderRightWidth: 1, borderColor: '#1E293B' },
  statVal: { fontSize: 18, fontWeight: 'bold', color: '#FFFFFF' },
  statLabel: { fontSize: 12, color: '#94A3B8', marginTop: 2 },

  section: { padding: 20 },
  sectionTitle: { fontSize: 18, fontWeight: 'bold', marginBottom: 10, color: '#FFFFFF' },
  bio: { fontSize: 16, color: '#CBD5E1', lineHeight: 24 },
  reviewsText: { fontStyle: 'italic', color: '#64748B' },

  footer: { 
    padding: 20, borderTopWidth: 1, borderTopColor: '#1E293B', 
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    backgroundColor: '#131B2E'
  },
  price: { fontSize: 22, fontWeight: 'bold', color: '#FFFFFF' },
  subtext: { fontSize: 12, color: '#94A3B8' },
  bookButton: { backgroundColor: '#15803D', paddingVertical: 12, paddingHorizontal: 25, borderRadius: 10 },
  bookButtonText: { color: 'white', fontWeight: 'bold', fontSize: 16 }
});

export default InstructorProfileScreen;
