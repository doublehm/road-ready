import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Modal,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Ionicons from 'react-native-vector-icons/Ionicons';

const DiagnosticRideIntroScreen = ({ navigation }) => {
  const [showDisclaimer, setShowDisclaimer] = useState(false);
  const [selectedRideType, setSelectedRideType] = useState(null);

  const handleStartRide = (rideType) => {
    setSelectedRideType(rideType);
    setShowDisclaimer(true);
  };

  const confirmDisclaimer = () => {
    setShowDisclaimer(false);
    if (selectedRideType === 'instructor') {
      // Route to booking flow — student books an instructor for a diagnostic ride
      navigation.navigate('StudentMain', {
        screen: 'Find Instructor',
        params: { forDiagnosticRide: true },
      });
    } else {
      navigation.navigate('DiagnosticRideSetup', { rideType: 'parent' });
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>Diagnostic Ride</Text>
          <Text style={styles.headerSubtitle}>
            Assess your driving skills
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
              <Text style={styles.stepTitle}>Advance Faster</Text>
              <Text style={styles.stepText}>
                Demonstrate your skills to progress to advanced training
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
            onPress={() => handleStartRide('parent')}
          >
            <Text style={styles.buttonTitle}>Ride with Parent</Text>
            <Text style={styles.buttonSubtext}>Free • Self-Supervised</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.button, styles.instructorButton]}
            onPress={() => handleStartRide('instructor')}
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

      {/* Safety Disclaimer Modal */}
      <Modal
        visible={showDisclaimer}
        transparent={true}
        animationType="slide"
      >
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <View style={styles.modalHeader}>
              <Ionicons name="shield-checkmark" size={40} color="#d63031" />
              <Text style={styles.modalTitle}>Safety & Legal Disclaimer</Text>
            </View>
            
            <ScrollView style={styles.disclaimerScroll}>
              <Text style={styles.disclaimerText}>
                By starting this diagnostic ride, you acknowledge and agree to the following:{"\n\n"}
                1. <Text style={styles.bold}>Supervision:</Text> You must be supervised by a qualified supervisor (parent or instructor) as required by your B.C. Learner's License.{"\n\n"}
                2. <Text style={styles.bold}>Liability:</Text> Road Ready is a data-collection and analysis tool only. We are not responsible for any incidents, accidents, or traffic violations that occur during this session.{"\n\n"}
                3. <Text style={styles.bold}>Attention:</Text> Do not interact with the mobile application while the vehicle is in motion. The phone should be securely mounted.{"\n\n"}
                4. <Text style={styles.bold}>Compliance:</Text> You must obey all B.C. traffic laws and regulations.
              </Text>
            </ScrollView>

            <TouchableOpacity 
              style={styles.confirmButton}
              onPress={confirmDisclaimer}
            >
              <Text style={styles.confirmButtonText}>I Accept & Understand</Text>
            </TouchableOpacity>

            <TouchableOpacity 
              style={styles.modalCancelButton}
              onPress={() => setShowDisclaimer(false)}
            >
              <Text style={styles.modalCancelText}>Cancel</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
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
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  modalContent: {
    backgroundColor: 'white',
    borderRadius: 20,
    padding: 24,
    width: '100%',
    maxHeight: '80%',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.25,
    shadowRadius: 10,
    elevation: 5,
  },
  modalHeader: {
    alignItems: 'center',
    marginBottom: 20,
  },
  modalTitle: {
    fontSize: 22,
    fontWeight: 'bold',
    color: '#1a1a1a',
    marginTop: 12,
  },
  disclaimerScroll: {
    marginBottom: 20,
  },
  disclaimerText: {
    fontSize: 15,
    color: '#2d3436',
    lineHeight: 22,
  },
  bold: {
    fontWeight: 'bold',
  },
  confirmButton: {
    backgroundColor: '#28a745',
    padding: 16,
    borderRadius: 12,
    alignItems: 'center',
    marginBottom: 12,
  },
  confirmButtonText: {
    color: 'white',
    fontSize: 18,
    fontWeight: 'bold',
  },
  modalCancelButton: {
    padding: 12,
    alignItems: 'center',
  },
  modalCancelText: {
    color: '#6c757d',
    fontSize: 16,
  },
});

export default DiagnosticRideIntroScreen;
