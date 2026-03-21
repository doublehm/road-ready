import React, { useState, useEffect, useContext } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  ActivityIndicator,
  Dimensions,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Ionicons from '@expo/vector-icons/Ionicons';
import { AuthContext } from '../context/AuthContext';
import client from '../api/client';
import RouteReplayMap from '../components/RouteReplayMap';
import SpeedGraph from '../components/SpeedGraph';
import EventTimeline from '../components/EventTimeline';

const { width } = Dimensions.get('window');

const TelemetryGauge = ({ score, label, size = 100, isMain = false }) => {
  const color = score >= 80 ? '#15803D' : score >= 60 ? '#F59E0B' : '#EF4444';
  
  return (
    <View style={[styles.gaugeContainer, { width: size }]}>
      <View style={[styles.gaugeOuter, { width: size, height: size, borderColor: '#1E293B' }]}>
        <View style={[styles.gaugeTrack, { width: size - 12, height: size - 12, borderColor: 'rgba(255,255,255,0.05)' }]} />
        <Text style={[styles.gaugeValue, { color, fontSize: isMain ? 32 : 20 }]}>{score}</Text>
        <Text style={styles.gaugePercent}>%</Text>
      </View>
      <Text style={styles.gaugeLabel}>{label.toUpperCase()}</Text>
    </View>
  );
};

const DiagnosticRideDetailScreen = ({ route, navigation }) => {
  const { rideId } = route.params;
  const [ride, setRide] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchRideDetails();
  }, []);

  const fetchRideDetails = async () => {
    try {
      const response = await client.get(`/diagnostic-rides/${rideId}`);
      setRide(response.data);
    } catch (error) {
      console.error('Error fetching ride details:', error);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#15803D" />
        <Text style={styles.loadingText}>INITIALIZING TELEMETRY...</Text>
      </View>
    );
  }

  if (!ride) return null;

  const evaluationResult = ride.evaluation_result ? JSON.parse(ride.evaluation_result) : null;
  const routeSegments = evaluationResult?.route_segments || [];
  const allEvents = evaluationResult?.events || [];
  
  let routeCoords = [];
  try { if (ride.route_coords) routeCoords = JSON.parse(ride.route_coords); } catch (e) {}

  let speedData = [];
  try { if (ride.speed_data) speedData = JSON.parse(ride.speed_data); } catch (e) {}

  let speedLimitData = [];
  try { if (ride.speed_limit_data) speedLimitData = JSON.parse(ride.speed_limit_data); } catch (e) {}

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backCircle}>
          <Ionicons name="chevron-back" size={24} color="white" />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>DIAGNOSTIC REPORT</Text>
        <View style={{ width: 44 }} />
      </View>

      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        
        {/* Hero Score Card */}
        <View style={styles.heroCard}>
          <View style={styles.heroMain}>
            <TelemetryGauge score={Math.round(ride.overall_score || 0)} label="Safety Score" size={140} isMain />
            <View style={styles.badgeContainer}>
              <View style={[styles.passBadge, { backgroundColor: ride.passed ? '#15803D' : '#EF4444' }]}>
                <Text style={styles.passBadgeText}>{ride.passed ? 'PASS' : 'FAIL'}</Text>
              </View>
              <Text style={styles.dateText}>{new Date(ride.created_at).toLocaleDateString()}</Text>
            </View>
          </View>
          
          <View style={styles.subGauges}>
            <TelemetryGauge score={Math.round(ride.braking_score || 0)} label="Braking" size={80} />
            <TelemetryGauge score={Math.round(ride.speed_score || 0)} label="Speed" size={80} />
            <TelemetryGauge score={Math.round(ride.cornering_score || 0)} label="Cornering" size={80} />
          </View>
        </View>

        {/* Map Section */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>ROUTE ANALYSIS</Text>
          <View style={styles.mapContainer}>
            <RouteReplayMap
              routeSegments={routeSegments}
              events={allEvents}
              routeCoords={routeCoords}
              height={280}
            />
            <View style={styles.mapOverlay}>
              <View style={styles.mapStat}>
                <Text style={styles.mapStatValue}>{ride.distance_km?.toFixed(1)}</Text>
                <Text style={styles.mapStatLabel}>KM</Text>
              </View>
              <View style={styles.mapStatDivider} />
              <View style={styles.mapStat}>
                <Text style={styles.mapStatValue}>{Math.round(ride.duration_minutes)}</Text>
                <Text style={styles.mapStatLabel}>MIN</Text>
              </View>
            </View>
          </View>
        </View>

        {/* Telemetry Graph */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>VELOCITY TELEMETRY</Text>
          <View style={styles.chartCard}>
            <SpeedGraph
              speedData={speedData}
              speedLimitData={speedLimitData}
              routeSegments={routeSegments}
              height={180}
            />
          </View>
        </View>

        {/* Detailed Feedback Cards */}
        <Text style={styles.sectionTitle}>SYSTEM FEEDBACK</Text>
        
        {evaluationResult?.speed?.notes?.length > 0 && (
          <View style={styles.feedbackCard}>
            <View style={styles.feedbackHeader}>
              <Ionicons name="speedometer-outline" size={20} color="#15803D" />
              <Text style={styles.feedbackTitle}>Speed Compliance</Text>
            </View>
            <Text style={styles.feedbackText}>{evaluationResult.speed.notes[0]}</Text>
          </View>
        )}

        {evaluationResult?.braking?.notes?.length > 0 && (
          <View style={styles.feedbackCard}>
            <View style={styles.feedbackHeader}>
              <Ionicons name="disc-outline" size={20} color="#15803D" />
              <Text style={styles.feedbackTitle}>Braking Precision</Text>
            </View>
            <Text style={styles.feedbackText}>{evaluationResult.braking.notes[0]}</Text>
          </View>
        )}

        {/* Event Timeline */}
        <View style={[styles.section, { marginBottom: 60 }]}>
          <Text style={styles.sectionTitle}>EVENT LOG</Text>
          <View style={styles.timelineCard}>
            <EventTimeline
              events={allEvents.sort((a,b) => a.timestamp - b.timestamp)}
              startTime={speedData[0]?.timestamp || 0}
            />
          </View>
        </View>

      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0B1326' },
  loadingContainer: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#0B1326' },
  loadingText: { color: '#94A3B8', marginTop: 16, fontWeight: '800', letterSpacing: 1 },
  header: { 
    flexDirection: 'row', 
    justifyContent: 'space-between', 
    alignItems: 'center', 
    paddingHorizontal: 20,
    paddingVertical: 15
  },
  headerTitle: { color: 'white', fontSize: 14, fontWeight: '900', letterSpacing: 2 },
  backCircle: { 
    width: 44, 
    height: 44, 
    borderRadius: 22, 
    backgroundColor: 'rgba(255,255,255,0.05)', 
    justifyContent: 'center', 
    alignItems: 'center' 
  },
  scroll: { paddingBottom: 100 },
  heroCard: {
    backgroundColor: '#131B2E',
    margin: 20,
    borderRadius: 32,
    padding: 24,
    shadowColor: '#000',
    shadowOpacity: 0.3,
    shadowRadius: 20,
    elevation: 10
  },
  heroMain: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 32 },
  badgeContainer: { alignItems: 'flex-end' },
  passBadge: { paddingHorizontal: 16, paddingVertical: 6, borderRadius: 12, marginBottom: 8 },
  passBadgeText: { color: 'white', fontWeight: '900', fontSize: 16 },
  dateText: { color: '#64748B', fontSize: 13, fontWeight: '600' },
  subGauges: { flexDirection: 'row', justifyContent: 'space-between', paddingTop: 20, borderTopWidth: 1, borderTopColor: 'rgba(255,255,255,0.05)' },
  gaugeContainer: { alignItems: 'center' },
  gaugeOuter: { 
    borderRadius: 100, 
    borderWidth: 4, 
    justifyContent: 'center', 
    alignItems: 'center',
    backgroundColor: 'rgba(255,255,255,0.02)'
  },
  gaugeTrack: { position: 'absolute', borderRadius: 100, borderWidth: 1 },
  gaugeValue: { fontWeight: '900' },
  gaugePercent: { position: 'absolute', bottom: '20%', fontSize: 10, color: '#64748B', fontWeight: '700' },
  gaugeLabel: { color: '#94A3B8', fontSize: 10, fontWeight: '800', marginTop: 12, letterSpacing: 1 },
  section: { paddingHorizontal: 20, marginBottom: 32 },
  sectionTitle: { color: '#94A3B8', fontSize: 12, fontWeight: '900', letterSpacing: 2, marginBottom: 16, paddingLeft: 4 },
  mapContainer: { borderRadius: 24, overflow: 'hidden', backgroundColor: '#131B2E' },
  mapOverlay: {
    position: 'absolute',
    bottom: 16,
    left: 16,
    right: 16,
    backgroundColor: 'rgba(11, 19, 38, 0.85)',
    borderRadius: 16,
    flexDirection: 'row',
    padding: 16,
    backdropBlur: 20
  },
  mapStat: { flex: 1, alignItems: 'center' },
  mapStatValue: { color: 'white', fontSize: 20, fontWeight: '800' },
  mapStatLabel: { color: '#64748B', fontSize: 10, fontWeight: '700', marginTop: 2 },
  mapStatDivider: { width: 1, height: '100%', backgroundColor: 'rgba(255,255,255,0.1)' },
  chartCard: { backgroundColor: '#131B2E', padding: 16, borderRadius: 24 },
  feedbackCard: { backgroundColor: '#131B2E', marginHorizontal: 20, marginBottom: 12, padding: 20, borderRadius: 20 },
  feedbackHeader: { flexDirection: 'row', alignItems: 'center', gap: 10, marginBottom: 10 },
  feedbackTitle: { color: 'white', fontSize: 15, fontWeight: '700' },
  feedbackText: { color: '#94A3B8', fontSize: 14, lineHeight: 20 },
  timelineCard: { backgroundColor: '#131B2E', padding: 20, borderRadius: 24 }
});

export default DiagnosticRideDetailScreen;
