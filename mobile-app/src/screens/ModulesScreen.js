import React, { useState, useEffect, useContext } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  SafeAreaView,
  ActivityIndicator,
  Alert,
} from 'react-native';
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
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>Learning Modules</Text>
          <Text style={styles.headerSubtitle}>
            Choose your learning path
          </Text>
        </View>

        {modules.map((module) => {
          const moduleProgress = getModuleProgress(module.id);
          const isLocked = moduleProgress?.status === 'locked';
          const isEnrolled =
            moduleProgress?.enrollment_type !== 'none' &&
            moduleProgress?.enrollment_type !== null;

          return (
            <View key={module.id} style={styles.moduleCard}>
              <View style={styles.moduleHeader}>
                <Text style={styles.moduleName}>{module.name}</Text>
                {isLocked && <Text style={styles.lockIcon}>🔒</Text>}
                {moduleProgress?.status === 'completed' && (
                  <Text style={styles.completedBadge}>✓ Completed</Text>
                )}
              </View>

              <Text style={styles.moduleDescription}>
                {module.description}
              </Text>

              <View style={styles.moduleInfo}>
                <Text style={styles.infoText}>
                  Package: ${module.package_price} ({module.min_hours} hours)
                </Text>
                <Text style={styles.infoText}>
                  Hourly: ${module.hourly_rate}/hour
                </Text>
              </View>

              {isEnrolled && (
                <View style={styles.progressBar}>
                  <View
                    style={[
                      styles.progressFill,
                      {
                        width: `${(moduleProgress.hours_completed / module.min_hours) * 100}%`,
                      },
                    ]}
                  />
                  <Text style={styles.progressText}>
                    {moduleProgress.hours_completed.toFixed(1)} /{' '}
                    {module.min_hours} hours
                  </Text>
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
                <Text style={styles.lockedText}>
                  Complete prerequisites to unlock
                </Text>
              )}
            </View>
          );
        })}
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
  moduleCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 20,
    marginBottom: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  moduleHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  moduleName: {
    fontSize: 22,
    fontWeight: 'bold',
    color: '#1a1a1a',
    flex: 1,
  },
  lockIcon: {
    fontSize: 20,
  },
  completedBadge: {
    fontSize: 14,
    color: '#28a745',
    fontWeight: 'bold',
  },
  moduleDescription: {
    fontSize: 14,
    color: '#6c757d',
    marginBottom: 12,
    lineHeight: 20,
  },
  moduleInfo: {
    marginBottom: 12,
  },
  infoText: {
    fontSize: 14,
    color: '#1a1a1a',
    marginBottom: 4,
  },
  progressBar: {
    height: 24,
    backgroundColor: '#e9ecef',
    borderRadius: 12,
    overflow: 'hidden',
    marginBottom: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  progressFill: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
    backgroundColor: '#28a745',
  },
  progressText: {
    fontSize: 12,
    color: '#1a1a1a',
    fontWeight: 'bold',
    zIndex: 1,
  },
  buttonContainer: {
    gap: 8,
  },
  button: {
    borderRadius: 8,
    padding: 12,
    alignItems: 'center',
  },
  packageButton: {
    backgroundColor: '#007bff',
  },
  hourlyButton: {
    backgroundColor: '#6c757d',
  },
  buttonText: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#fff',
  },
  lockedText: {
    fontSize: 14,
    color: '#6c757d',
    textAlign: 'center',
    fontStyle: 'italic',
  },
});

export default ModulesScreen;
