import React, { useState, useEffect, useContext } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Ionicons from '@expo/vector-icons/Ionicons';
import { AuthContext } from '../context/AuthContext';
import client from '../api/client';

const ModulesScreen = ({ navigation }) => {
  const { userToken } = useContext(AuthContext);
  const [modules, setModules] = useState([]);
  const [progress, setProgress] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchModulesAndProgress();
  }, []);

  const fetchModulesAndProgress = async () => {
    try {
      const [modulesRes, progressRes] = await Promise.all([
        client.get('/modules/'),
        client.get('/modules/student-progress'),
      ]);

      setModules(modulesRes.data);
      setProgress(progressRes.data);
    } catch (error) {
      console.error('Error fetching modules:', error);
      Alert.alert('Error', 'Failed to load modules');
    } finally {
      setLoading(false);
    }
  };

  const getModuleProgress = (moduleId) => {
    return progress.find((p) => p.module_id === moduleId);
  };

  const handleEnroll = async (module, enrollmentType) => {
    try {
      await client.post(`/modules/${module.id}/enroll`, null, {
        params: { enrollment_type: enrollmentType },
      });

      Alert.alert('Success', `Enrolled in ${module.name} as ${enrollmentType}`);
      fetchModulesAndProgress();
    } catch (error) {
      console.error('Error enrolling:', error);
      Alert.alert(
        'Error',
        error.response?.data?.detail || 'Failed to enroll in module'
      );
    }
  };

  if (loading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#007bff" />
      </View>
    );
  }

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>Learning Modules</Text>
          <Text style={styles.headerSubtitle}>
            MISSION PARAMETERS & TRAINING
          </Text>
        </View>

        {modules.map((module) => {
          const moduleProgress = getModuleProgress(module.id);
          const isLocked = moduleProgress?.status === 'locked';
          const isEnrolled =
            moduleProgress?.enrollment_type !== 'none' &&
            moduleProgress?.enrollment_type !== null;

          return (
            <View key={module.id} style={[styles.moduleCard, isLocked && styles.lockedCard]}>
              <View style={styles.moduleHeader}>
                <Text style={styles.moduleName}>{module.name}</Text>
                {isLocked && <Ionicons name="lock-closed" size={20} color="#94A3B8" />}
                {moduleProgress?.status === 'completed' && (
                  <View style={styles.completedBadgeContainer}>
                    <Ionicons name="checkmark-circle" size={16} color="#15803D" />
                    <Text style={styles.completedBadge}>Completed</Text>
                  </View>
                )}
              </View>

              <Text style={styles.moduleDescription}>
                {module.description}
              </Text>

              <View style={styles.moduleInfo}>
                <View style={styles.infoRow}>
                  <Ionicons name="pricetag-outline" size={14} color="#64748B" />
                  <Text style={styles.infoText}>
                    Package: <Text style={styles.infoValue}>${module.package_price}</Text> ({module.min_hours} hours)
                  </Text>
                </View>
                <View style={styles.infoRow}>
                  <Ionicons name="time-outline" size={14} color="#64748B" />
                  <Text style={styles.infoText}>
                    Hourly Rate: <Text style={styles.infoValue}>${module.hourly_rate}/hr</Text>
                  </Text>
                </View>
              </View>

              {isEnrolled && (
                <View style={styles.progressSection}>
                  <View style={styles.progressHeader}>
                    <Text style={styles.progressLabel}>MODULE COMPLETION</Text>
                    <Text style={styles.progressValue}>
                      {moduleProgress.hours_completed.toFixed(1)} / {module.min_hours}h
                    </Text>
                  </View>
                  <View style={styles.progressBar}>
                    <View
                      style={[
                        styles.progressFill,
                        {
                          width: `${Math.min((moduleProgress.hours_completed / module.min_hours) * 100, 100)}%`,
                        },
                      ]}
                    />
                  </View>
                </View>
              )}

              {!isLocked && !isEnrolled && (
                <View style={styles.buttonContainer}>
                  <TouchableOpacity
                    style={[styles.button, styles.packageButton]}
                    onPress={() => handleEnroll(module, 'package')}
                  >
                    <Text style={styles.buttonText}>
                      Enroll Package (${module.package_price})
                    </Text>
                  </TouchableOpacity>

                  <TouchableOpacity
                    style={[styles.button, styles.hourlyButton]}
                    onPress={() => handleEnroll(module, 'hourly')}
                  >
                    <Text style={styles.buttonText}>
                      Book Individual Lesson
                    </Text>
                  </TouchableOpacity>
                </View>
              )}

              {isLocked && (
                <View style={styles.lockedNote}>
                  <Ionicons name="alert-circle-outline" size={16} color="#94A3B8" />
                  <Text style={styles.lockedText}>
                    Complete prerequisites to unlock access.
                  </Text>
                </View>
              )}
            </View>
          );
        })}
        <View style={{height: 60}} />
      </ScrollView>
    </SafeAreaView>
  );
  };

  const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F6FAFE',
  },
  scrollContent: {
    padding: 24,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F6FAFE',
  },
  header: {
    alignItems: 'flex-start',
    marginBottom: 32,
    marginTop: 10,
  },
  headerTitle: {
    fontSize: 32,
    fontWeight: '800',
    color: '#1E293B',
    letterSpacing: -1,
  },
  headerSubtitle: {
    fontSize: 12,
    fontWeight: '800',
    color: '#15803D',
    letterSpacing: 2,
    marginTop: 4,
  },
  moduleCard: {
    backgroundColor: '#fff',
    borderRadius: 24,
    padding: 24,
    marginBottom: 16,
    shadowColor: '#1E293B',
    shadowOpacity: 0.04,
    shadowRadius: 15,
    elevation: 2,
  },
  lockedCard: {
    opacity: 0.7,
    backgroundColor: '#F1F5F9',
  },
  moduleHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  moduleName: {
    fontSize: 20,
    fontWeight: '800',
    color: '#1E293B',
    flex: 1,
    letterSpacing: -0.5,
  },
  completedBadgeContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#DCFCE7',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 8,
    gap: 4
  },
  completedBadge: {
    fontSize: 12,
    color: '#15803D',
    fontWeight: '800',
  },
  moduleDescription: {
    fontSize: 14,
    color: '#64748B',
    marginBottom: 20,
    lineHeight: 22,
    fontWeight: '500'
  },
  moduleInfo: {
    marginBottom: 24,
    gap: 8,
    padding: 16,
    backgroundColor: '#F8FAFC',
    borderRadius: 16
  },
  infoRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  infoText: {
    fontSize: 14,
    color: '#64748B',
    fontWeight: '600'
  },
  infoValue: { color: '#1E293B', fontWeight: '800' },
  progressSection: { marginBottom: 20 },
  progressHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 10 },
  progressLabel: { fontSize: 10, fontWeight: '800', color: '#94A3B8', letterSpacing: 1 },
  progressValue: { fontSize: 13, fontWeight: '800', color: '#15803D' },
  progressBar: {
    height: 10,
    backgroundColor: '#E2E8F0',
    borderRadius: 5,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    backgroundColor: '#15803D',
    borderRadius: 5
  },
  buttonContainer: {
    gap: 12,
  },
  button: {
    borderRadius: 16,
    padding: 18,
    alignItems: 'center',
  },
  packageButton: {
    backgroundColor: '#1E293B',
  },
  hourlyButton: {
    backgroundColor: 'white',
    borderWidth: 2,
    borderColor: '#F1F5F9'
  },
  buttonText: {
    fontSize: 15,
    fontWeight: '800',
    color: '#fff',
  },
  lockedNote: { flexDirection: 'row', alignItems: 'center', gap: 8, justifyContent: 'center', marginTop: 8 },
  lockedText: {
    fontSize: 13,
    color: '#94A3B8',
    fontWeight: '600'
  },
  });

export default ModulesScreen;
