import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  SafeAreaView,
} from 'react-native';

const DiagnosticRideIntroScreen = ({ navigation }) => {
  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>Diagnostic Ride</Text>
          <Text style={styles.headerSubtitle}>
            Prove your skills and save money
          </Text>
        </View>

        <View style={styles.savingsCard}>
          <Text style={styles.savingsAmount}>Save up to $800</Text>
          <Text style={styles.savingsText}>
            Pass the diagnostic ride and skip the Basics module!
          </Text>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>How It Works</Text>
          <View style={styles.step}>
            <Text style={styles.stepNumber}>1</Text>
            <View style={styles.stepContent}>
              <Text style={styles.stepTitle}>Take a Diagnostic Ride</Text>
              <Text style={styles.stepText}>
                Drive for 20+ minutes with your parent or an instructor
              </Text>
            </View>
          </View>

          <View style={styles.step}>
            <Text style={styles.stepNumber}>2</Text>
            <View style={styles.stepContent}>
              <Text style={styles.stepTitle}>Automated Evaluation</Text>
              <Text style={styles.stepText}>
                Your phone tracks braking, speed, and cornering quality
              </Text>
            </View>
          </View>

          <View style={styles.step}>
            <Text style={styles.stepNumber}>3</Text>
            <View style={styles.stepContent}>
              <Text style={styles.stepTitle}>Get Results</Text>
              <Text style={styles.stepText}>
                Instant feedback on your driving performance
              </Text>
            </View>
          </View>

          <View style={styles.step}>
            <Text style={styles.stepNumber}>4</Text>
            <View style={styles.stepContent}>
              <Text style={styles.stepTitle}>Skip Basics if You Pass</Text>
              <Text style={styles.stepText}>
                Go straight to Advanced/Test Prep and save $800+
              </Text>
            </View>
          </View>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Requirements</Text>
          <View style={styles.requirement}>
            <Text style={styles.checkmark}>✓</Text>
            <Text style={styles.requirementText}>
              Valid learner's license
            </Text>
          </View>
          <View style={styles.requirement}>
            <Text style={styles.checkmark}>✓</Text>
            <Text style={styles.requirementText}>
              20+ minute drive covering 5+ km
            </Text>
          </View>
          <View style={styles.requirement}>
            <Text style={styles.checkmark}>✓</Text>
            <Text style={styles.requirementText}>
              Phone securely mounted in vehicle
            </Text>
          </View>
          <View style={styles.requirement}>
            <Text style={styles.checkmark}>✓</Text>
            <Text style={styles.requirementText}>
              Supervised by parent or instructor
            </Text>
          </View>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Passing Criteria</Text>
          <Text style={styles.criteriaText}>
            • Overall score of 75% or higher
          </Text>
          <Text style={styles.criteriaText}>
            • Braking score of 70% or higher
          </Text>
          <Text style={styles.criteriaText}>
            • Speed control score of 70% or higher
          </Text>
          <Text style={styles.criteriaText}>
            • Cornering score of 70% or higher
          </Text>
        </View>

        <View style={styles.buttonContainer}>
          <TouchableOpacity
            style={[styles.button, styles.parentButton]}
            onPress={() =>
              navigation.navigate('DiagnosticRideSetup', { rideType: 'parent' })
            }
          >
            <Text style={styles.buttonTitle}>Ride with Parent</Text>
            <Text style={styles.buttonSubtext}>Free • Self-Supervised</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.button, styles.instructorButton]}
            onPress={() =>
              navigation.navigate('DiagnosticRideSetup', {
                rideType: 'instructor',
              })
            }
          >
            <Text style={styles.buttonTitle}>Ride with Instructor</Text>
            <Text style={styles.buttonSubtext}>~$120 • Professional Review</Text>
          </TouchableOpacity>
        </View>

        <TouchableOpacity
          style={styles.cancelButton}
          onPress={() => navigation.goBack()}
        >
          <Text style={styles.cancelButtonText}>Maybe Later</Text>
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
  scrollContent: {
    padding: 20,
    paddingBottom: 40,
  },
  header: {
    alignItems: 'center',
    marginBottom: 24,
    marginTop: 20,
  },
  headerTitle: {
    fontSize: 32,
    fontWeight: 'bold',
    color: '#1a1a1a',
    marginBottom: 8,
  },
  headerSubtitle: {
    fontSize: 16,
    color: '#6c757d',
  },
  savingsCard: {
    backgroundColor: '#28a745',
    borderRadius: 12,
    padding: 20,
    alignItems: 'center',
    marginBottom: 24,
  },
  savingsAmount: {
    fontSize: 36,
    fontWeight: 'bold',
    color: '#fff',
    marginBottom: 4,
  },
  savingsText: {
    fontSize: 16,
    color: '#fff',
    textAlign: 'center',
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#1a1a1a',
    marginBottom: 12,
  },
  step: {
    flexDirection: 'row',
    marginBottom: 16,
  },
  stepNumber: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: '#007bff',
    color: '#fff',
    fontSize: 16,
    fontWeight: 'bold',
    textAlign: 'center',
    lineHeight: 32,
    marginRight: 12,
  },
  stepContent: {
    flex: 1,
  },
  stepTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1a1a1a',
    marginBottom: 4,
  },
  stepText: {
    fontSize: 14,
    color: '#6c757d',
  },
  requirement: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  checkmark: {
    fontSize: 18,
    color: '#28a745',
    marginRight: 8,
    fontWeight: 'bold',
  },
  requirementText: {
    fontSize: 14,
    color: '#1a1a1a',
  },
  criteriaText: {
    fontSize: 14,
    color: '#1a1a1a',
    marginBottom: 6,
    marginLeft: 8,
  },
  buttonContainer: {
    marginTop: 8,
    gap: 12,
  },
  button: {
    borderRadius: 12,
    padding: 20,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  parentButton: {
    backgroundColor: '#007bff',
  },
  instructorButton: {
    backgroundColor: '#6610f2',
  },
  buttonTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#fff',
    marginBottom: 4,
  },
  buttonSubtext: {
    fontSize: 14,
    color: '#fff',
    opacity: 0.9,
  },
  cancelButton: {
    marginTop: 16,
    padding: 12,
    alignItems: 'center',
  },
  cancelButtonText: {
    fontSize: 16,
    color: '#6c757d',
  },
});

export default DiagnosticRideIntroScreen;
