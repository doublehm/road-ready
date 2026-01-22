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
import { AuthContext } from '../context/AuthContext';
import client from '../api/client';

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

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>
            {ride.passed ? '✓ You Passed!' : 'Not Yet'}
          </Text>
          <Text style={styles.headerSubtitle}>
            {ride.passed
              ? 'Congratulations on your excellent driving!'
              : 'Keep practicing and try again'}
          </Text>
        </View>

        {ride.passed && (
          <View style={styles.savingsCard}>
            <Text style={styles.savingsText}>
              You saved $800 by skipping Basics!
            </Text>
            <Text style={styles.savingsSubtext}>
              Advanced Module is now unlocked
            </Text>
          </View>
        )}

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

        {evaluationResult && (
          <>
            {evaluationResult.braking && (
              <View style={styles.feedbackSection}>
                <Text style={styles.feedbackTitle}>Braking Feedback</Text>
                {evaluationResult.braking.notes?.map((note, index) => (
                  <Text key={index} style={styles.feedbackText}>
                    • {note}
                  </Text>
                ))}
              </View>
            )}

            {evaluationResult.speed && (
              <View style={styles.feedbackSection}>
                <Text style={styles.feedbackTitle}>Speed Control Feedback</Text>
                {evaluationResult.speed.notes?.map((note, index) => (
                  <Text key={index} style={styles.feedbackText}>
                    • {note}
                  </Text>
                ))}
              </View>
            )}

            {evaluationResult.cornering && (
              <View style={styles.feedbackSection}>
                <Text style={styles.feedbackTitle}>Cornering Feedback</Text>
                {evaluationResult.cornering.notes?.map((note, index) => (
                  <Text key={index} style={styles.feedbackText}>
                    • {note}
                  </Text>
                ))}
              </View>
            )}

            {evaluationResult.summary && (
              <View style={styles.summaryCard}>
                <Text style={styles.summaryText}>{evaluationResult.summary}</Text>
              </View>
            )}
          </>
        )}

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
            onPress={() => navigation.navigate('StudentHome')}
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
    padding: 20,
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
    marginBottom: 24,
    marginTop: 20,
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
    marginBottom: 24,
  },
  savingsText: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#fff',
    marginBottom: 4,
  },
  savingsSubtext: {
    fontSize: 14,
    color: '#fff',
    opacity: 0.9,
  },
  scoresContainer: {
    alignItems: 'center',
    marginBottom: 24,
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
  feedbackSection: {
    marginBottom: 16,
    backgroundColor: '#fff',
    borderRadius: 8,
    padding: 16,
  },
  feedbackTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#1a1a1a',
    marginBottom: 8,
  },
  feedbackText: {
    fontSize: 14,
    color: '#6c757d',
    marginBottom: 4,
    lineHeight: 20,
  },
  summaryCard: {
    backgroundColor: '#e7f3ff',
    borderRadius: 8,
    padding: 16,
    marginBottom: 24,
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
