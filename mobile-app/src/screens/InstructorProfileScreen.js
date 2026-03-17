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
              <Ionicons name="checkmark-circle" size={24} color="#007bff" style={{marginLeft: 8}} />
            )}
          </View>
          {instructor.is_verified && (
            <Text style={{color: '#007bff', fontWeight: 'bold', fontSize: 12, marginTop: 4}}>
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
  container: { flex: 1, backgroundColor: '#fff' },
  header: { alignItems: 'center', padding: 30, backgroundColor: '#f8f9fa' },
  avatar: { width: 80, height: 80, borderRadius: 40, backgroundColor: '#007bff', justifyContent: 'center', alignItems: 'center', marginBottom: 15 },
  avatarText: { color: 'white', fontSize: 32, fontWeight: 'bold' },
  name: { fontSize: 24, fontWeight: 'bold', color: '#333' },
  city: { fontSize: 16, color: '#666', marginTop: 5 },
  
  statsRow: { flexDirection: 'row', padding: 20, borderBottomWidth: 1, borderBottomColor: '#eee' },
  stat: { flex: 1, alignItems: 'center' },
  statBorder: { borderLeftWidth: 1, borderRightWidth: 1, borderColor: '#eee' },
  statVal: { fontSize: 18, fontWeight: 'bold', color: '#333' },
  statLabel: { fontSize: 12, color: '#888', marginTop: 2 },

  section: { padding: 20 },
  sectionTitle: { fontSize: 18, fontWeight: 'bold', marginBottom: 10, color: '#333' },
  bio: { fontSize: 16, color: '#555', lineHeight: 24 },
  reviewsText: { fontStyle: 'italic', color: '#999' },

  footer: { 
    padding: 20, borderTopWidth: 1, borderTopColor: '#eee', 
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    backgroundColor: 'white'
  },
  price: { fontSize: 22, fontWeight: 'bold', color: '#333' },
  subtext: { fontSize: 12, color: '#888' },
  bookButton: { backgroundColor: '#28a745', paddingVertical: 12, paddingHorizontal: 25, borderRadius: 10 },
  bookButtonText: { color: 'white', fontWeight: 'bold', fontSize: 16 }
});

export default InstructorProfileScreen;
