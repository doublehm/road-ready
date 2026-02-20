import React, { useState, useContext } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  TextInput,
  ScrollView,
  Alert,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { AuthContext } from '../context/AuthContext';

const DiagnosticRideSetupScreen = ({ route, navigation }) => {
  const { rideType } = route.params; // 'parent' or 'instructor'
  const { userToken } = useContext(AuthContext);

  const [parentName, setParentName] = useState('');
  const [agreedToTerms, setAgreedToTerms] = useState(false);

  const isReady =
    (rideType === 'parent' && parentName.trim() !== '' && agreedToTerms) ||
    (rideType === 'instructor' && agreedToTerms);

  const handleStartRide = () => {
    if (!isReady) {
      Alert.alert('Incomplete', 'Please complete all requirements');
      return;
    }

    // Redirect to Supervisor Hand-off instead of active ride directly
    navigation.navigate('SupervisorHandoff', {
      rideParams: {
        rideType,
        parentName: rideType === 'parent' ? parentName : null,
        instructorId: null, // TODO: Select instructor in future
      }
    });
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>
            {rideType === 'parent'
              ? 'Ride with Parent'
              : 'Ride with Instructor'}
          </Text>
          <Text style={styles.headerSubtitle}>Complete setup to begin</Text>
        </View>

        {rideType === 'parent' && (
          <View style={styles.section}>
            <Text style={styles.label}>Supervisor Name</Text>
            <TextInput
              style={styles.input}
              placeholder="Parent/Guardian Name"
              value={parentName}
              onChangeText={setParentName}
            />
          </View>
        )}

        {rideType === 'instructor' && (
          <View style={styles.section}>
            <Text style={styles.infoText}>
              You'll book an instructor-supervised diagnostic ride. The
              instructor will review your automated evaluation and can provide
              override if needed.
            </Text>
            <Text style={styles.costText}>Cost: ~$120</Text>
          </View>
        )}

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Pre-Ride Checklist</Text>

          <View style={styles.checklistItem}>
            <Text style={styles.checkbox}>☐</Text>
            <Text style={styles.checklistText}>
              Phone is securely mounted (dashboard or windshield)
            </Text>
          </View>

          <View style={styles.checklistItem}>
            <Text style={styles.checkbox}>☐</Text>
            <Text style={styles.checklistText}>
              Location and motion sensors enabled
            </Text>
          </View>

          <View style={styles.checklistItem}>
            <Text style={styles.checkbox}>☐</Text>
            <Text style={styles.checklistText}>
              Planned route is 5+ km (20+ minutes)
            </Text>
          </View>

          <View style={styles.checklistItem}>
            <Text style={styles.checkbox}>☐</Text>
            <Text style={styles.checklistText}>Vehicle is ready to drive</Text>
          </View>
        </View>

        <View style={styles.section}>
          <TouchableOpacity
            style={styles.termsContainer}
            onPress={() => setAgreedToTerms(!agreedToTerms)}
          >
            <Text style={styles.checkbox}>{agreedToTerms ? '☑' : '☐'}</Text>
            <Text style={styles.termsText}>
              I understand that this is an automated evaluation and results are
              based on sensor data. {rideType === 'instructor' ? 'The instructor can override the automated result.' : 'I may retry with an instructor if needed.'}
            </Text>
          </TouchableOpacity>
        </View>

        <TouchableOpacity
          style={[styles.button, !isReady && styles.buttonDisabled]}
          onPress={handleStartRide}
          disabled={!isReady}
        >
          <Text style={styles.buttonText}>Start Diagnostic Ride</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.cancelButton}
          onPress={() => navigation.goBack()}
        >
          <Text style={styles.cancelButtonText}>Cancel</Text>
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
    fontSize: 28,
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
  label: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1a1a1a',
    marginBottom: 8,
  },
  input: {
    backgroundColor: '#fff',
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
    borderWidth: 1,
    borderColor: '#dee2e6',
  },
  infoText: {
    fontSize: 14,
    color: '#6c757d',
    marginBottom: 12,
    lineHeight: 20,
  },
  costText: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#6610f2',
  },
  checklistItem: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginBottom: 12,
  },
  checkbox: {
    fontSize: 20,
    marginRight: 8,
    color: '#007bff',
  },
  checklistText: {
    flex: 1,
    fontSize: 14,
    color: '#1a1a1a',
    lineHeight: 20,
  },
  termsContainer: {
    flexDirection: 'row',
    alignItems: 'flex-start',
  },
  termsText: {
    flex: 1,
    fontSize: 14,
    color: '#1a1a1a',
    lineHeight: 20,
  },
  button: {
    backgroundColor: '#28a745',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
    marginTop: 8,
  },
  buttonDisabled: {
    backgroundColor: '#adb5bd',
  },
  buttonText: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#fff',
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

export default DiagnosticRideSetupScreen;
