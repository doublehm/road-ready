import React from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Ionicons from '@expo/vector-icons/Ionicons';
import { FAULT_CATEGORIES } from '../data/faults';

const SessionDetailScreen = ({ route, navigation }) => {
  const { session } = route.params;

  // Helper to parse fault codes (e.g. "A1,A1,B2")
  const parseFaults = (dataStr) => {
    if (!dataStr) return {};
    // Split by comma, trim whitespace, remove empty strings, uppercase
    const codes = dataStr.split(',').map(c => c.trim().toUpperCase()).filter(c => c.length > 0);
    
    // Count occurrences
    const counts = {};
    codes.forEach(c => counts[c] = (counts[c] || 0) + 1);
    return counts;
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return 'Date Unknown';
    // If it's YYYY-MM-DD (no time), append dummy time for consistent parsing if needed, 
    // but usually it's just a date string from the backend seed.
    // If it is just YYYY-MM-DD, the time will be midnight UTC/Local.
    
    // Check if it looks like a full ISO string
    const date = new Date(dateStr);
    if (isNaN(date.getTime())) return dateStr; // Fallback if invalid

    return date.toLocaleDateString('en-US', {
      weekday: 'long', year: 'numeric', month: 'long', day: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  };

  const renderCategory = (category) => {
    // Determine which field maps to this category
    // A -> observation_data, B -> space_margin_data, etc.
    let dataStr = "";
    if (category.id === 'A') dataStr = session.observation_data;
    else if (category.id === 'B') dataStr = session.space_margin_data;
    else if (category.id === 'C') dataStr = session.speed_data;
    else if (category.id === 'D') dataStr = session.steering_data;
    else if (category.id === 'E') dataStr = session.communication_data;

    const faults = parseFaults(dataStr);
    const hasFaults = Object.keys(faults).length > 0;

    if (!hasFaults) return null;

    return (
      <View key={category.id} style={styles.categoryCard}>
        <View style={[styles.catHeader, { backgroundColor: category.color }]}>
          <Text style={styles.catTitle}>{category.title}</Text>
        </View>
        <View style={styles.catBody}>
          {Object.entries(faults).map(([code, count]) => {
            // Find label in the category items
            const labelItem = category.items.find(i => i.code === code);
            const label = labelItem ? labelItem.label : `Code ${code}`;
            
            return (
              <View key={code} style={styles.faultRow}>
                <View style={styles.badge}>
                  <Text style={styles.badgeText}>{count}</Text>
                </View>
                <View style={{flex: 1, marginLeft: 10}}>
                  <Text style={styles.faultLabel}>{label}</Text>
                  <Text style={styles.faultCode}>{code}</Text>
                </View>
              </View>
            );
          })}
        </View>
      </View>
    );
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Ionicons name="arrow-back" size={24} color="#FFFFFF" />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Session Report</Text>
        <View style={{width: 24}} />
      </View>

      <ScrollView contentContainerStyle={styles.scroll}>
        
        {/* Summary Card */}
        <View style={styles.summaryCard}>
          <Text style={styles.date}>{formatDate(session.created_at)}</Text>
          <View style={styles.row}>
            <View style={styles.stat}>
              <Ionicons name="time-outline" size={20} color="#94A3B8" />
              <Text style={styles.statText}>{session.duration_minutes} min</Text>
            </View>
            <View style={styles.stat}>
              <Ionicons name="partly-sunny-outline" size={20} color="#94A3B8" />
              <Text style={styles.statText}>{session.weather_condition}</Text>
            </View>
            <View style={styles.stat}>
              <Ionicons name="car-outline" size={20} color="#94A3B8" />
              <Text style={styles.statText}>{session.road_type}</Text>
            </View>
          </View>
        </View>

        {/* Feedback */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Instructor Feedback</Text>
          <View style={styles.feedbackBox}>
            <Text style={styles.feedbackText}>
              {session.shared_feedback || "No written feedback provided."}
            </Text>
          </View>
        </View>

        {/* Faults Breakdown */}
        <Text style={styles.sectionTitle}>Areas for Improvement</Text>
        {FAULT_CATEGORIES.map(renderCategory)}
        
        {/* If no faults at all */}
        {!session.observation_data && !session.space_margin_data && !session.speed_data && 
         !session.steering_data && !session.communication_data && (
           <View style={styles.perfectCard}>
             <Ionicons name="star" size={40} color="#F59E0B" />
             <Text style={styles.perfectText}>Perfect Drive! No faults recorded.</Text>
           </View>
        )}

      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0B1326' },
  header: { 
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', 
    padding: 15, backgroundColor: '#131B2E', borderBottomWidth: 1, borderBottomColor: '#1E293B' 
  },
  headerTitle: { fontSize: 18, fontWeight: 'bold', color: '#FFFFFF' },
  scroll: { padding: 20 },

  summaryCard: {
    backgroundColor: '#131B2E', borderRadius: 12, padding: 20, marginBottom: 20,
    alignItems: 'center'
  },
  date: { fontSize: 18, fontWeight: 'bold', marginBottom: 15, color: '#FFFFFF' },
  row: { flexDirection: 'row', justifyContent: 'space-around', width: '100%' },
  stat: { alignItems: 'center' },
  statText: { marginTop: 5, color: '#94A3B8', fontSize: 14 },

  sectionTitle: { fontSize: 18, fontWeight: 'bold', marginBottom: 10, color: '#FFFFFF' },
  section: { marginBottom: 25 },
  
  feedbackBox: { 
    backgroundColor: '#131B2E', padding: 15, borderRadius: 10, 
    borderLeftWidth: 4, borderLeftColor: '#3B82F6' 
  },
  feedbackText: { fontSize: 16, color: '#E2E8F0', lineHeight: 22, fontStyle: 'italic' },

  categoryCard: { 
    backgroundColor: '#131B2E', borderRadius: 10, marginBottom: 15, overflow: 'hidden',
    borderWidth: 1, borderColor: '#1E293B'
  },
  catHeader: { padding: 10, paddingHorizontal: 15 },
  catTitle: { fontWeight: 'bold', fontSize: 16, color: '#FFFFFF' },
  catBody: { padding: 15 },
  
  faultRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 12 },
  badge: { 
    backgroundColor: '#EF4444', width: 30, height: 30, borderRadius: 15, 
    justifyContent: 'center', alignItems: 'center' 
  },
  badgeText: { color: 'white', fontWeight: 'bold' },
  faultLabel: { fontSize: 16, color: '#FFFFFF', fontWeight: '500' },
  faultCode: { fontSize: 12, color: '#64748B' },

  perfectCard: { alignItems: 'center', padding: 30, backgroundColor: '#131B2E', borderRadius: 12 },
  perfectText: { marginTop: 10, fontSize: 16, color: '#FFFFFF', fontWeight: 'bold' }
});

export default SessionDetailScreen;
