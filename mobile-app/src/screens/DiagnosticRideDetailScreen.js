import React, { useState, useEffect, useContext } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  SafeAreaView,
  ActivityIndicator,
} from 'react-native';
import Ionicons from 'react-native-vector-icons/Ionicons';
import { AuthContext } from '../context/AuthContext';
import client from '../api/client';
import RouteReplayMap from '../components/RouteReplayMap';
import SpeedGraph from '../components/SpeedGraph';
import EventTimeline from '../components/EventTimeline';

const ScoreCircle = ({ score, label, size = 80 }) => {
  const color = score >= 80 ? '#28a745' : score >= 60 ? '#ffc107' : '#dc3545';

  return (
    <View style={[styles.scoreCircle, { width: size, height: size }]}>
      <Text style={[styles.scoreValue, { color, fontSize: size * 0.3 }]}>
        {score}
      </Text>
      <Text style={[styles.scoreLabel, { fontSize: size * 0.12 }]}>{label}</Text>
    </View>
  );
};

const DiagnosticRideDetailScreen = ({ route, navigation }) => {
  const { rideId } = route.params;
  const { userToken } = useContext(AuthContext);

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

  const formatDate = (dateStr) => {
    if (!dateStr) return '';
    try {
      const date = new Date(dateStr);
      return date.toLocaleDateString('en-CA', {
        weekday: 'long',
        month: 'long',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return dateStr;
    }
  };

  if (loading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#007bff" />
        <Text style={styles.loadingText}>Loading ride details...</Text>
      </View>
    );
  }

  if (!ride) {
    return (
      <View style={styles.loadingContainer}>
        <Text style={styles.errorText}>Failed to load ride details</Text>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => navigation.goBack()}
        >
          <Text style={styles.backButtonText}>Go Back</Text>
        </TouchableOpacity>
      </View>
    );
  }

  const evaluationResult = ride.evaluation_result
    ? JSON.parse(ride.evaluation_result)
    : null;

  const routeSegments = evaluationResult?.route_segments || [];
  const allEvents = evaluationResult?.events || [];
  const speedFeedback = evaluationResult?.speed || {};
  const brakingFeedback = evaluationResult?.braking || {};
  const corneringFeedback = evaluationResult?.cornering || {};

  let routeCoords = [];
  try {
    if (ride.route_coords) routeCoords = JSON.parse(ride.route_coords);
  } catch (e) {}

  let speedData = [];
  try {
    if (ride.speed_data) speedData = JSON.parse(ride.speed_data);
  } catch (e) {}

  let speedLimitData = [];
  try {
    if (ride.speed_limit_data) speedLimitData = JSON.parse(ride.speed_limit_data);
  } catch (e) {}

  const startTimestamp = speedData.length > 0 ? speedData[0].timestamp : 0;

  return (
    <SafeAreaView style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Ionicons name="arrow-back" size={24} color="#1a1a1a" />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Ride Detail</Text>
        <View style={{ width: 24 }} />
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent}>
        {/* Date & Pass/Fail */}
        <View style={styles.dateRow}>
          <Text style={styles.dateText}>{formatDate(ride.created_at)}</Text>
          {ride.passed !== null && (
            <View style={[
              styles.passBadge,
              { backgroundColor: ride.passed ? '#28a745' : '#dc3545' }
            ]}>
              <Text style={styles.passBadgeText}>
                {ride.passed ? 'PASSED' : 'FAILED'}
              </Text>
            </View>
          )}
        </View>

        {/* Scores */}
        <View style={styles.scoresRow}>
          <ScoreCircle score={Math.round(ride.overall_score || 0)} label="Overall" size={100} />
          <View style={styles.smallScoresColumn}>
            <ScoreCircle score={Math.round(ride.braking_score || 0)} label="Braking" size={70} />
            <ScoreCircle score={Math.round(ride.speed_score || 0)} label="Speed" size={70} />
            <ScoreCircle score={Math.round(ride.cornering_score || 0)} label="Cornering" size={70} />
          </View>
        </View>

        {/* Ride Stats */}
        <View style={styles.statsCard}>
          <View style={styles.statItem}>
            <Ionicons name="time-outline" size={20} color="#007bff" />
            <Text style={styles.statValue}>
              {ride.duration_minutes ? `${Math.round(ride.duration_minutes)} min` : '--'}
            </Text>
            <Text style={styles.statLabel}>Duration</Text>
          </View>
          <View style={styles.statItem}>
            <Ionicons name="navigate-outline" size={20} color="#007bff" />
            <Text style={styles.statValue}>
              {ride.distance_km ? `${ride.distance_km.toFixed(1)} km` : '--'}
            </Text>
            <Text style={styles.statLabel}>Distance</Text>
          </View>
          <View style={styles.statItem}>
            <Ionicons name="alert-circle-outline" size={20} color="#007bff" />
            <Text style={styles.statValue}>{allEvents.length}</Text>
            <Text style={styles.statLabel}>Events</Text>
          </View>
          <View style={styles.statItem}>
            <Ionicons name="person-outline" size={20} color="#007bff" />
            <Text style={styles.statValue}>
              {ride.ride_type === 'parent_supervised' ? 'Parent' : 'Instructor'}
            </Text>
            <Text style={styles.statLabel}>Supervisor</Text>
          </View>
        </View>

        {/* Speed Violation Summary */}
        {speedFeedback.max_excess_kmh > 0 && (
          <View style={styles.violationSummary}>
            <Text style={styles.sectionTitle}>Speed Violations</Text>
            <View style={styles.violationRow}>
              <View style={styles.violationItem}>
                <Text style={styles.violationValue}>{speedFeedback.speeding_percentage}%</Text>
                <Text style={styles.violationLabel}>Time over limit</Text>
              </View>
              <View style={styles.violationItem}>
                <Text style={[styles.violationValue, { color: '#dc3545' }]}>
                  +{Math.round(speedFeedback.max_excess_kmh)} km/h
                </Text>
                <Text style={styles.violationLabel}>Max excess</Text>
              </View>
              {speedFeedback.school_zone_violations > 0 && (
                <View style={styles.violationItem}>
                  <Text style={[styles.violationValue, { color: '#ff9800' }]}>
                    {speedFeedback.school_zone_violations}s
                  </Text>
                  <Text style={styles.violationLabel}>School zone</Text>
                </View>
              )}
            </View>
          </View>
        )}

        {/* Route Map */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Route Map</Text>
          <RouteReplayMap
            routeSegments={routeSegments}
            events={allEvents}
            routeCoords={routeCoords}
            height={250}
          />
        </View>

        {/* Speed Graph */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Speed vs. Limit</Text>
          <SpeedGraph
            speedData={speedData}
            speedLimitData={speedLimitData}
            routeSegments={routeSegments}
            height={180}
          />
        </View>

        {/* Feedback Sections */}
        {evaluationResult && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Feedback</Text>

            {/* Braking */}
            <View style={styles.feedbackCard}>
              <View style={styles.feedbackHeader}>
                <Ionicons name="hand-left" size={16} color="#495057" />
                <Text style={styles.feedbackTitle}>Braking</Text>
              </View>
              {brakingFeedback.notes?.map((note, i) => (
                <Text key={i} style={styles.feedbackText}>{note}</Text>
              ))}
              {brakingFeedback.tips?.map((tip, i) => (
                <View key={`tip-${i}`} style={styles.tipRow}>
                  <Ionicons name="bulb" size={12} color="#ffc107" />
                  <Text style={styles.tipText}>{tip}</Text>
                </View>
              ))}
            </View>

            {/* Speed */}
            <View style={styles.feedbackCard}>
              <View style={styles.feedbackHeader}>
                <Ionicons name="speedometer" size={16} color="#495057" />
                <Text style={styles.feedbackTitle}>Speed Control</Text>
              </View>
              {speedFeedback.notes?.map((note, i) => (
                <Text key={i} style={styles.feedbackText}>{note}</Text>
              ))}
              {speedFeedback.tips?.map((tip, i) => (
                <View key={`tip-${i}`} style={styles.tipRow}>
                  <Ionicons name="bulb" size={12} color="#ffc107" />
                  <Text style={styles.tipText}>{tip}</Text>
                </View>
              ))}
            </View>

            {/* Cornering */}
            <View style={styles.feedbackCard}>
              <View style={styles.feedbackHeader}>
                <Ionicons name="git-compare" size={16} color="#495057" />
                <Text style={styles.feedbackTitle}>Cornering</Text>
              </View>
              {corneringFeedback.notes?.map((note, i) => (
                <Text key={i} style={styles.feedbackText}>{note}</Text>
              ))}
              {corneringFeedback.tips?.map((tip, i) => (
                <View key={`tip-${i}`} style={styles.tipRow}>
                  <Ionicons name="bulb" size={12} color="#ffc107" />
                  <Text style={styles.tipText}>{tip}</Text>
                </View>
              ))}
            </View>
          </View>
        )}

        {/* Event Timeline */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Event Timeline</Text>
          <EventTimeline events={allEvents} startTime={startTimestamp} />
        </View>

        {/* Summary */}
        {evaluationResult?.summary && (
          <View style={styles.summaryCard}>
            <Text style={styles.summaryText}>{evaluationResult.summary}</Text>
          </View>
        )}

        {/* Back Button */}
        <TouchableOpacity
          style={styles.backButtonLarge}
          onPress={() => navigation.goBack()}
        >
          <Ionicons name="arrow-back" size={20} color="#007bff" />
          <Text style={styles.backButtonLargeText}>Back to History</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8f9fa',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f8f9fa',
  },
  loadingText: {
    marginTop: 12,
    fontSize: 16,
    color: '#6c757d',
  },
  errorText: {
    fontSize: 16,
    color: '#dc3545',
    marginBottom: 16,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 16,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#1a1a1a',
  },
  scrollContent: {
    padding: 16,
    paddingBottom: 40,
  },
  dateRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  dateText: {
    fontSize: 14,
    color: '#6c757d',
    flex: 1,
  },
  passBadge: {
    borderRadius: 6,
    paddingHorizontal: 12,
    paddingVertical: 4,
  },
  passBadgeText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: 'bold',
  },
  scoresRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 20,
    marginBottom: 16,
  },
  smallScoresColumn: {
    flexDirection: 'row',
    gap: 8,
  },
  scoreCircle: {
    borderRadius: 1000,
    borderWidth: 6,
    borderColor: '#e9ecef',
    justifyContent: 'center',
    alignItems: 'center',
  },
  scoreValue: {
    fontWeight: 'bold',
  },
  scoreLabel: {
    color: '#6c757d',
    marginTop: 2,
  },
  statsCard: {
    flexDirection: 'row',
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
    justifyContent: 'space-around',
  },
  statItem: {
    alignItems: 'center',
    gap: 4,
  },
  statValue: {
    fontSize: 15,
    fontWeight: 'bold',
    color: '#1a1a1a',
  },
  statLabel: {
    fontSize: 10,
    color: '#6c757d',
  },
  violationSummary: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
  },
  violationRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginTop: 8,
  },
  violationItem: {
    alignItems: 'center',
  },
  violationValue: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#1a1a1a',
  },
  violationLabel: {
    fontSize: 11,
    color: '#6c757d',
    marginTop: 2,
  },
  section: {
    marginBottom: 20,
  },
  sectionTitle: {
    fontSize: 17,
    fontWeight: 'bold',
    color: '#1a1a1a',
    marginBottom: 10,
  },
  feedbackCard: {
    backgroundColor: '#fff',
    borderRadius: 10,
    padding: 14,
    marginBottom: 10,
  },
  feedbackHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 8,
  },
  feedbackTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: '#1a1a1a',
  },
  feedbackText: {
    fontSize: 13,
    color: '#6c757d',
    marginBottom: 4,
    lineHeight: 18,
  },
  tipRow: {
    flexDirection: 'row',
    gap: 6,
    marginTop: 4,
  },
  tipText: {
    flex: 1,
    fontSize: 12,
    color: '#856404',
    lineHeight: 17,
  },
  summaryCard: {
    backgroundColor: '#e7f3ff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 20,
  },
  summaryText: {
    fontSize: 14,
    color: '#1a1a1a',
    lineHeight: 20,
  },
  backButton: {
    padding: 12,
    alignItems: 'center',
  },
  backButtonText: {
    color: '#007bff',
    fontSize: 16,
  },
  backButtonLarge: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    padding: 16,
    backgroundColor: '#fff',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#007bff',
  },
  backButtonLargeText: {
    color: '#007bff',
    fontSize: 16,
    fontWeight: '600',
  },
});

export default DiagnosticRideDetailScreen;
