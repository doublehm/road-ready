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

const ScoreCircle = ({ score, label, size = 100 }) => {
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

const CategoryCard = ({ title, icon, score, notes = [], tips = [], events = [], extraInfo }) => {
  const color = score >= 80 ? '#28a745' : score >= 60 ? '#ffc107' : '#dc3545';
  const eventCount = events.length;

  return (
    <View style={styles.categoryCard}>
      <View style={styles.categoryHeader}>
        <View style={styles.categoryTitleRow}>
          <Ionicons name={icon} size={20} color={color} />
          <Text style={styles.categoryTitle}>{title}</Text>
        </View>
        <View style={[styles.categoryScoreBadge, { backgroundColor: color }]}>
          <Text style={styles.categoryScoreText}>{Math.round(score)}</Text>
        </View>
      </View>

      {eventCount > 0 && (
        <View style={styles.eventCountRow}>
          <Ionicons name="alert-circle" size={14} color="#6c757d" />
          <Text style={styles.eventCountText}>
            {eventCount} event{eventCount !== 1 ? 's' : ''} detected
          </Text>
        </View>
      )}

      {extraInfo && (
        <View style={styles.extraInfoRow}>
          {Object.entries(extraInfo).map(([key, value]) => (
            <View key={key} style={styles.extraInfoItem}>
              <Text style={styles.extraInfoLabel}>{key}</Text>
              <Text style={styles.extraInfoValue}>{value}</Text>
            </View>
          ))}
        </View>
      )}

      {notes.map((note, index) => (
        <Text key={index} style={styles.feedbackText}>
          {note}
        </Text>
      ))}

      {tips.length > 0 && (
        <View style={styles.tipsContainer}>
          <Text style={styles.tipsHeader}>Tips to Improve</Text>
          {tips.map((tip, index) => (
            <View key={index} style={styles.tipItem}>
              <Ionicons name="bulb" size={14} color="#ffc107" />
              <Text style={styles.tipText}>{tip}</Text>
            </View>
          ))}
        </View>
      )}
    </View>
  );
};

const DiagnosticRideResultsScreen = ({ route, navigation }) => {
  const { rideId } = route.params;
  const { userToken } = useContext(AuthContext);

  const [ride, setRide] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchRideResults();
  }, []);

  const fetchRideResults = async () => {
    try {
      const response = await client.get(`/diagnostic-rides/${rideId}`);
      setRide(response.data);
    } catch (error) {
      console.error('Error fetching ride results:', error);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#007bff" />
        <Text style={styles.loadingText}>Loading results...</Text>
      </View>
    );
  }

  if (!ride) {
    return (
      <View style={styles.loadingContainer}>
        <Text style={styles.errorText}>Failed to load results</Text>
        <TouchableOpacity
          style={styles.button}
          onPress={() => navigation.goBack()}
        >
          <Text style={styles.buttonText}>Go Back</Text>
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

  // Parse route coords for fallback
  let routeCoords = [];
  try {
    if (ride.route_coords) {
      routeCoords = JSON.parse(ride.route_coords);
    }
  } catch (e) {}

  // Parse speed data for graph
  let speedData = [];
  try {
    if (ride.speed_data) {
      speedData = JSON.parse(ride.speed_data);
    }
  } catch (e) {}

  let speedLimitData = [];
  try {
    if (ride.speed_limit_data) {
      speedLimitData = JSON.parse(ride.speed_limit_data);
    }
  } catch (e) {}

  const startTimestamp = speedData.length > 0 ? speedData[0].timestamp : 0;

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.headerTitle}>
            {ride.passed ? 'You Passed!' : 'Not Yet'}
          </Text>
          <Text style={styles.headerSubtitle}>
            {ride.passed
              ? 'Congratulations on your excellent driving!'
              : 'Keep practicing and try again'}
          </Text>
        </View>

        {ride.passed && (
          <View style={styles.savingsCard}>
            <Ionicons name="trophy" size={24} color="#fff" />
            <Text style={styles.savingsText}>
              You saved $800 by skipping Basics!
            </Text>
            <Text style={styles.savingsSubtext}>
              Advanced Module is now unlocked
            </Text>
          </View>
        )}

        {/* Scores */}
        <View style={styles.scoresContainer}>
          <ScoreCircle
            score={Math.round(ride.overall_score)}
            label="Overall"
            size={140}
          />

          <View style={styles.smallScoresRow}>
            <ScoreCircle
              score={Math.round(ride.braking_score)}
              label="Braking"
              size={90}
            />
            <ScoreCircle
              score={Math.round(ride.speed_score)}
              label="Speed"
              size={90}
            />
            <ScoreCircle
              score={Math.round(ride.cornering_score)}
              label="Cornering"
              size={90}
            />
          </View>
        </View>

        {/* Ride Stats */}
        <View style={styles.rideStatsRow}>
          <View style={styles.rideStat}>
            <Ionicons name="time" size={18} color="#6c757d" />
            <Text style={styles.rideStatValue}>
              {ride.duration_minutes ? `${Math.round(ride.duration_minutes)} min` : '--'}
            </Text>
          </View>
          <View style={styles.rideStat}>
            <Ionicons name="navigate" size={18} color="#6c757d" />
            <Text style={styles.rideStatValue}>
              {ride.distance_km ? `${ride.distance_km.toFixed(1)} km` : '--'}
            </Text>
          </View>
          <View style={styles.rideStat}>
            <Ionicons name="alert-circle" size={18} color="#6c757d" />
            <Text style={styles.rideStatValue}>
              {allEvents.length} events
            </Text>
          </View>
        </View>

        {/* Speed Violation Summary */}
        {speedFeedback.school_zone_violations > 0 && (
          <View style={styles.violationCard}>
            <Ionicons name="warning" size={20} color="#ff9800" />
            <View style={styles.violationContent}>
              <Text style={styles.violationTitle}>School Zone Violations</Text>
              <Text style={styles.violationText}>
                Speed limit exceeded in a school zone for {speedFeedback.school_zone_violations} seconds.
                School zone violations carry a 2x penalty.
              </Text>
            </View>
          </View>
        )}

        {speedFeedback.max_excess_kmh > 0 && (
          <View style={styles.speedSummaryCard}>
            <Text style={styles.speedSummaryTitle}>Speed Analysis</Text>
            <View style={styles.speedSummaryRow}>
              <View style={styles.speedSummaryItem}>
                <Text style={styles.speedSummaryValue}>
                  {speedFeedback.speeding_percentage}%
                </Text>
                <Text style={styles.speedSummaryLabel}>Time over limit</Text>
              </View>
              <View style={styles.speedSummaryItem}>
                <Text style={[styles.speedSummaryValue, { color: '#dc3545' }]}>
                  +{Math.round(speedFeedback.max_excess_kmh)} km/h
                </Text>
                <Text style={styles.speedSummaryLabel}>Max excess</Text>
              </View>
              <View style={styles.speedSummaryItem}>
                <Text style={styles.speedSummaryValue}>
                  {speedFeedback.speed_violations?.length || 0}
                </Text>
                <Text style={styles.speedSummaryLabel}>Violations</Text>
              </View>
            </View>
          </View>
        )}

        {/* Route Replay Map */}
        <View style={styles.sectionContainer}>
          <Text style={styles.sectionTitle}>Route Map</Text>
          <RouteReplayMap
            routeSegments={routeSegments}
            events={allEvents}
            routeCoords={routeCoords}
            height={280}
          />
        </View>

        {/* Speed Graph */}
        <View style={styles.sectionContainer}>
          <Text style={styles.sectionTitle}>Speed vs. Limit</Text>
          <SpeedGraph
            speedData={speedData}
            speedLimitData={speedLimitData}
            routeSegments={routeSegments}
            height={200}
          />
        </View>

        {/* Category Breakdown */}
        <View style={styles.sectionContainer}>
          <Text style={styles.sectionTitle}>Detailed Breakdown</Text>

          <CategoryCard
            title="Braking"
            icon="hand-left"
            score={ride.braking_score}
            notes={brakingFeedback.notes || []}
            tips={brakingFeedback.tips || []}
            events={brakingFeedback.events || []}
            extraInfo={{
              'Harsh braking': `${brakingFeedback.harsh_braking_events || 0} events`,
              'Sudden stops': `${brakingFeedback.sudden_stops || 0}`,
              'Smooth braking': `${brakingFeedback.smooth_braking_events || 0} events`,
            }}
          />

          <CategoryCard
            title="Speed Control"
            icon="speedometer"
            score={ride.speed_score}
            notes={speedFeedback.notes || []}
            tips={speedFeedback.tips || []}
            events={speedFeedback.events || []}
            extraInfo={{
              'Over limit': `${speedFeedback.speeding_percentage || 0}% of time`,
              'Under speed': `${speedFeedback.under_speed_percentage || 0}% of time`,
              ...(speedFeedback.school_zone_violations > 0 ? {
                'School zone': `${speedFeedback.school_zone_violations}s over limit`
              } : {}),
            }}
          />

          <CategoryCard
            title="Cornering"
            icon="git-compare"
            score={ride.cornering_score}
            notes={corneringFeedback.notes || []}
            tips={corneringFeedback.tips || []}
            events={corneringFeedback.events || []}
            extraInfo={{
              'Sharp turns': `${corneringFeedback.sharp_turns || 0}`,
              'Smooth turns': `${corneringFeedback.smooth_turns || 0}`,
              'Lane discipline': corneringFeedback.lane_discipline || 'N/A',
            }}
          />
        </View>

        {/* Event Timeline */}
        <View style={styles.sectionContainer}>
          <Text style={styles.sectionTitle}>Event Timeline</Text>
          <EventTimeline
            events={allEvents}
            startTime={startTimestamp}
          />
        </View>

        {/* Summary */}
        {evaluationResult?.summary && (
          <View style={styles.summaryCard}>
            <Text style={styles.summaryText}>{evaluationResult.summary}</Text>
          </View>
        )}

        {/* Action Buttons */}
        <View style={styles.buttonContainer}>
          {ride.passed ? (
            <TouchableOpacity
              style={[styles.button, styles.primaryButton]}
              onPress={() => navigation.navigate('Modules')}
            >
              <Text style={styles.buttonText}>Explore Advanced Module</Text>
            </TouchableOpacity>
          ) : (
            <>
              <TouchableOpacity
                style={[styles.button, styles.primaryButton]}
                onPress={() =>
                  navigation.navigate('DiagnosticRideIntro')
                }
              >
                <Text style={styles.buttonText}>Retry with Instructor</Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={[styles.button, styles.secondaryButton]}
                onPress={() => navigation.navigate('Modules')}
              >
                <Text style={[styles.buttonText, styles.secondaryButtonText]}>
                  Start Basics Lessons
                </Text>
              </TouchableOpacity>
            </>
          )}

          <TouchableOpacity
            style={styles.cancelButton}
            onPress={() => navigation.navigate('Dashboard')}
          >
            <Text style={styles.cancelButtonText}>Return to Home</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8f9fa',
  },
  scrollContent: {
    padding: 16,
    paddingBottom: 40,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f8f9fa',
  },
  loadingText: {
    marginTop: 16,
    fontSize: 16,
    color: '#6c757d',
  },
  errorText: {
    fontSize: 16,
    color: '#dc3545',
    marginBottom: 16,
  },
  header: {
    alignItems: 'center',
    marginBottom: 20,
    marginTop: 16,
  },
  headerTitle: {
    fontSize: 36,
    fontWeight: 'bold',
    color: '#1a1a1a',
    marginBottom: 8,
  },
  headerSubtitle: {
    fontSize: 16,
    color: '#6c757d',
    textAlign: 'center',
  },
  savingsCard: {
    backgroundColor: '#28a745',
    borderRadius: 12,
    padding: 20,
    alignItems: 'center',
    marginBottom: 20,
    gap: 4,
  },
  savingsText: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#fff',
  },
  savingsSubtext: {
    fontSize: 14,
    color: '#fff',
    opacity: 0.9,
  },
  scoresContainer: {
    alignItems: 'center',
    marginBottom: 16,
  },
  scoreCircle: {
    borderRadius: 1000,
    borderWidth: 8,
    borderColor: '#e9ecef',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 16,
  },
  scoreValue: {
    fontWeight: 'bold',
  },
  scoreLabel: {
    color: '#6c757d',
    marginTop: 4,
  },
  smallScoresRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 12,
  },
  rideStatsRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 24,
    marginBottom: 20,
    paddingVertical: 12,
    backgroundColor: '#fff',
    borderRadius: 12,
  },
  rideStat: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  rideStatValue: {
    fontSize: 14,
    color: '#495057',
    fontWeight: '600',
  },
  violationCard: {
    flexDirection: 'row',
    backgroundColor: '#fff3cd',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
    gap: 12,
    borderLeftWidth: 4,
    borderLeftColor: '#ff9800',
  },
  violationContent: {
    flex: 1,
  },
  violationTitle: {
    fontSize: 15,
    fontWeight: 'bold',
    color: '#856404',
    marginBottom: 4,
  },
  violationText: {
    fontSize: 13,
    color: '#856404',
    lineHeight: 18,
  },
  speedSummaryCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
  },
  speedSummaryTitle: {
    fontSize: 15,
    fontWeight: 'bold',
    color: '#1a1a1a',
    marginBottom: 12,
  },
  speedSummaryRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
  },
  speedSummaryItem: {
    alignItems: 'center',
  },
  speedSummaryValue: {
    fontSize: 22,
    fontWeight: 'bold',
    color: '#1a1a1a',
  },
  speedSummaryLabel: {
    fontSize: 11,
    color: '#6c757d',
    marginTop: 2,
  },
  sectionContainer: {
    marginBottom: 20,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#1a1a1a',
    marginBottom: 12,
  },
  categoryCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
  },
  categoryHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  categoryTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  categoryTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#1a1a1a',
  },
  categoryScoreBadge: {
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  categoryScoreText: {
    color: '#fff',
    fontWeight: 'bold',
    fontSize: 16,
  },
  eventCountRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginBottom: 8,
  },
  eventCountText: {
    fontSize: 12,
    color: '#6c757d',
  },
  extraInfoRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
    marginBottom: 12,
    paddingVertical: 8,
    borderTopWidth: 1,
    borderBottomWidth: 1,
    borderColor: '#f0f0f0',
  },
  extraInfoItem: {
    alignItems: 'center',
  },
  extraInfoLabel: {
    fontSize: 10,
    color: '#adb5bd',
    textTransform: 'uppercase',
  },
  extraInfoValue: {
    fontSize: 13,
    fontWeight: '600',
    color: '#495057',
    marginTop: 2,
  },
  feedbackText: {
    fontSize: 13,
    color: '#6c757d',
    marginBottom: 4,
    lineHeight: 18,
  },
  tipsContainer: {
    marginTop: 8,
    backgroundColor: '#fff9e6',
    borderRadius: 8,
    padding: 12,
  },
  tipsHeader: {
    fontSize: 12,
    fontWeight: 'bold',
    color: '#856404',
    marginBottom: 6,
    textTransform: 'uppercase',
  },
  tipItem: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 4,
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
  buttonContainer: {
    marginTop: 8,
    gap: 12,
  },
  button: {
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
  },
  primaryButton: {
    backgroundColor: '#007bff',
  },
  secondaryButton: {
    backgroundColor: '#fff',
    borderWidth: 2,
    borderColor: '#007bff',
  },
  buttonText: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#fff',
  },
  secondaryButtonText: {
    color: '#007bff',
  },
  cancelButton: {
    marginTop: 8,
    padding: 12,
    alignItems: 'center',
  },
  cancelButtonText: {
    fontSize: 16,
    color: '#6c757d',
  },
});

export default DiagnosticRideResultsScreen;
