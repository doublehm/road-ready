import React, { useState, useEffect, useContext, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  SafeAreaView,
  ActivityIndicator,
  RefreshControl,
} from 'react-native';
import Ionicons from 'react-native-vector-icons/Ionicons';
import { AuthContext } from '../context/AuthContext';
import client from '../api/client';

const FILTERS = ['All', 'Passed', 'Failed'];

const DiagnosticRideHistoryScreen = ({ navigation }) => {
  const { userInfo } = useContext(AuthContext);
  const isInstructor = userInfo?.role === 'instructor';

  const [rides, setRides] = useState([]);
  const [trends, setTrends] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [filter, setFilter] = useState('All');

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      const [ridesRes, trendsRes] = await Promise.all([
        client.get('/diagnostic-rides/'),
        client.get('/diagnostic-rides/progress-trends').catch(() => ({ data: null })),
      ]);
      setRides(ridesRes.data || []);
      if (trendsRes.data) setTrends(trendsRes.data);
    } catch (error) {
      console.error('Error fetching ride history:', error);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  const onRefresh = useCallback(() => {
    setRefreshing(true);
    fetchData();
  }, []);

  const filteredRides = rides.filter(ride => {
    if (filter === 'Passed') return ride.passed === true;
    if (filter === 'Failed') return ride.passed === false;
    return true;
  });

  const formatDate = (dateStr) => {
    if (!dateStr) return '';
    try {
      const date = new Date(dateStr);
      return date.toLocaleDateString('en-CA', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
      });
    } catch {
      return dateStr;
    }
  };

  const renderRideCard = ({ item }) => {
    const scoreColor = (item.overall_score || 0) >= 80 ? '#28a745' :
                       (item.overall_score || 0) >= 60 ? '#ffc107' : '#dc3545';

    return (
      <TouchableOpacity
        style={styles.rideCard}
        onPress={() => navigation.navigate('DiagnosticRideDetail', { rideId: item.id })}
      >
        <View style={styles.rideCardLeft}>
          <View style={[styles.scoreBadge, { backgroundColor: scoreColor }]}>
            <Text style={styles.scoreBadgeText}>
              {Math.round(item.overall_score || 0)}
            </Text>
          </View>
        </View>

        <View style={styles.rideCardCenter}>
          <View style={styles.rideCardRow}>
            <Text style={styles.rideDate}>{formatDate(item.created_at)}</Text>
            {item.passed !== null && (
              <View style={[
                styles.passBadge,
                { backgroundColor: item.passed ? '#28a745' : '#dc3545' }
              ]}>
                <Text style={styles.passBadgeText}>
                  {item.passed ? 'PASSED' : 'FAILED'}
                </Text>
              </View>
            )}
          </View>

          <View style={styles.rideStats}>
            <View style={styles.rideStatItem}>
              <Ionicons name="time-outline" size={14} color="#6c757d" />
              <Text style={styles.rideStatText}>
                {item.duration_minutes ? `${Math.round(item.duration_minutes)} min` : '--'}
              </Text>
            </View>
            <View style={styles.rideStatItem}>
              <Ionicons name="navigate-outline" size={14} color="#6c757d" />
              <Text style={styles.rideStatText}>
                {item.distance_km ? `${item.distance_km.toFixed(1)} km` : '--'}
              </Text>
            </View>
            <View style={styles.rideStatItem}>
              <Text style={styles.rideType}>
                {item.ride_type === 'parent_supervised' ? 'Parent' : 'Instructor'}
              </Text>
            </View>
          </View>

          {/* Mini score bars */}
          <View style={styles.miniScores}>
            <MiniScoreBar label="B" score={item.braking_score} />
            <MiniScoreBar label="S" score={item.speed_score} />
            <MiniScoreBar label="C" score={item.cornering_score} />
          </View>
        </View>

        <Ionicons name="chevron-forward" size={20} color="#ccc" />
      </TouchableOpacity>
    );
  };

  if (loading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#007bff" />
        <Text style={styles.loadingText}>Loading ride history...</Text>
      </View>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Ionicons name="arrow-back" size={24} color="#1a1a1a" />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Ride History</Text>
        <View style={{ width: 24 }} />
      </View>

      {/* Summary Stats */}
      {trends && trends.total_rides > 0 && (
        <View style={styles.summaryCard}>
          <View style={styles.summaryRow}>
            <View style={styles.summaryItem}>
              <Text style={styles.summaryValue}>{trends.total_rides}</Text>
              <Text style={styles.summaryLabel}>Total Rides</Text>
            </View>
            <View style={styles.summaryItem}>
              <Text style={styles.summaryValue}>{trends.best_overall}</Text>
              <Text style={styles.summaryLabel}>Best Score</Text>
            </View>
            <View style={styles.summaryItem}>
              <Text style={styles.summaryValue}>{trends.pass_rate}%</Text>
              <Text style={styles.summaryLabel}>Pass Rate</Text>
            </View>
            <View style={styles.summaryItem}>
              <Text style={styles.summaryValue}>{trends.total_distance_km} km</Text>
              <Text style={styles.summaryLabel}>Total Distance</Text>
            </View>
          </View>

          {trends.improvement_areas.length > 0 && (
            <View style={styles.improvementRow}>
              <Ionicons name="trending-up" size={16} color="#007bff" />
              <Text style={styles.improvementText}>
                Focus area: {trends.improvement_areas.join(', ').replace('_', ' ')}
              </Text>
            </View>
          )}
        </View>
      )}

      {/* Filter */}
      <View style={styles.filterRow}>
        {FILTERS.map(f => (
          <TouchableOpacity
            key={f}
            style={[styles.filterButton, filter === f && styles.filterButtonActive]}
            onPress={() => setFilter(f)}
          >
            <Text style={[styles.filterText, filter === f && styles.filterTextActive]}>
              {f}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {/* Ride List */}
      <FlatList
        data={filteredRides}
        keyExtractor={(item) => item.id.toString()}
        renderItem={renderRideCard}
        contentContainerStyle={styles.listContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Ionicons name="car-outline" size={48} color="#ccc" />
            <Text style={styles.emptyText}>
              {filter === 'All' ? 'No diagnostic rides yet' : `No ${filter.toLowerCase()} rides`}
            </Text>
          </View>
        }
      />
    </SafeAreaView>
  );
};

const MiniScoreBar = ({ label, score }) => {
  const width = score ? `${Math.min(100, score)}%` : '0%';
  const color = (score || 0) >= 70 ? '#28a745' : (score || 0) >= 50 ? '#ffc107' : '#dc3545';

  return (
    <View style={styles.miniScoreContainer}>
      <Text style={styles.miniScoreLabel}>{label}</Text>
      <View style={styles.miniScoreTrack}>
        <View style={[styles.miniScoreFill, { width, backgroundColor: color }]} />
      </View>
      <Text style={styles.miniScoreValue}>{Math.round(score || 0)}</Text>
    </View>
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
  summaryCard: {
    backgroundColor: '#fff',
    margin: 16,
    marginBottom: 8,
    borderRadius: 12,
    padding: 16,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 3,
  },
  summaryRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
  },
  summaryItem: {
    alignItems: 'center',
  },
  summaryValue: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#1a1a1a',
  },
  summaryLabel: {
    fontSize: 11,
    color: '#6c757d',
    marginTop: 2,
  },
  improvementRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: '#f0f0f0',
  },
  improvementText: {
    fontSize: 13,
    color: '#007bff',
    fontWeight: '500',
  },
  filterRow: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    paddingVertical: 8,
    gap: 8,
  },
  filterButton: {
    paddingHorizontal: 16,
    paddingVertical: 6,
    borderRadius: 20,
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#dee2e6',
  },
  filterButtonActive: {
    backgroundColor: '#007bff',
    borderColor: '#007bff',
  },
  filterText: {
    fontSize: 13,
    color: '#6c757d',
    fontWeight: '500',
  },
  filterTextActive: {
    color: '#fff',
  },
  listContent: {
    padding: 16,
    paddingTop: 8,
  },
  rideCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 14,
    marginBottom: 10,
    elevation: 1,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
  },
  rideCardLeft: {
    marginRight: 12,
  },
  scoreBadge: {
    width: 48,
    height: 48,
    borderRadius: 24,
    justifyContent: 'center',
    alignItems: 'center',
  },
  scoreBadgeText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: 'bold',
  },
  rideCardCenter: {
    flex: 1,
  },
  rideCardRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 6,
  },
  rideDate: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1a1a1a',
  },
  passBadge: {
    borderRadius: 4,
    paddingHorizontal: 8,
    paddingVertical: 2,
  },
  passBadgeText: {
    color: '#fff',
    fontSize: 10,
    fontWeight: 'bold',
  },
  rideStats: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 6,
  },
  rideStatItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  rideStatText: {
    fontSize: 12,
    color: '#6c757d',
  },
  rideType: {
    fontSize: 12,
    color: '#6c757d',
    fontStyle: 'italic',
  },
  miniScores: {
    flexDirection: 'row',
    gap: 8,
  },
  miniScoreContainer: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  miniScoreLabel: {
    fontSize: 9,
    color: '#adb5bd',
    fontWeight: 'bold',
    width: 10,
  },
  miniScoreTrack: {
    flex: 1,
    height: 4,
    backgroundColor: '#e9ecef',
    borderRadius: 2,
  },
  miniScoreFill: {
    height: 4,
    borderRadius: 2,
  },
  miniScoreValue: {
    fontSize: 9,
    color: '#6c757d',
    width: 18,
    textAlign: 'right',
  },
  emptyContainer: {
    alignItems: 'center',
    paddingVertical: 48,
  },
  emptyText: {
    fontSize: 16,
    color: '#adb5bd',
    marginTop: 12,
  },
});

export default DiagnosticRideHistoryScreen;
