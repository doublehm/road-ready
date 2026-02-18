import React, { useMemo } from 'react';
import { View, Text, StyleSheet, Dimensions, ScrollView } from 'react-native';

const SCREEN_WIDTH = Dimensions.get('window').width;

/**
 * SpeedGraph - Displays speed vs. speed limit over time.
 * Uses a custom View-based bar visualization.
 *
 * @param {Array} speedData - Array of {timestamp, speed, latitude, longitude}
 * @param {Array} speedLimitData - Array of {timestamp, speed_limit, zone_type}
 * @param {Array} routeSegments - Array of {speed, limit, timestamp} from evaluation
 * @param {number} height - Chart height (default 200)
 */
const SpeedGraph = ({ speedData = [], speedLimitData = [], routeSegments = [], height = 200 }) => {
  // Downsample data to ~100 points for performance
  const { speeds, limits, labels } = useMemo(() => {
    let dataSource = [];

    if (routeSegments.length > 0) {
      dataSource = routeSegments.map(seg => ({
        speed: seg.speed || 0,
        limit: seg.limit || 0,
        timestamp: seg.timestamp || 0,
      }));
    } else if (speedData.length > 0) {
      dataSource = speedData.map(p => {
        // Find matching speed limit
        let limit = 0;
        if (speedLimitData.length > 0) {
          const closest = speedLimitData.reduce((best, sl) => {
            const diff = Math.abs((sl.timestamp || 0) - (p.timestamp || 0));
            return diff < best.diff ? { sl, diff } : best;
          }, { sl: null, diff: Infinity });
          limit = closest.sl?.speed_limit || 0;
        }
        return { speed: p.speed || 0, limit, timestamp: p.timestamp || 0 };
      });
    }

    if (dataSource.length === 0) {
      return { speeds: [], limits: [], labels: [] };
    }

    // Downsample
    const maxPoints = 80;
    const step = Math.max(1, Math.floor(dataSource.length / maxPoints));
    const sampled = dataSource.filter((_, i) => i % step === 0);

    const startTime = sampled[0]?.timestamp || 0;

    return {
      speeds: sampled.map(d => Math.round(d.speed)),
      limits: sampled.map(d => d.limit),
      labels: sampled.map((d, i) => {
        const elapsed = Math.round(((d.timestamp || 0) - startTime) / 60000);
        // Only show label every few points
        if (i % Math.max(1, Math.floor(sampled.length / 6)) === 0) {
          return `${elapsed}m`;
        }
        return '';
      }),
    };
  }, [speedData, speedLimitData, routeSegments]);

  if (speeds.length === 0) {
    return (
      <View style={[styles.placeholder, { height }]}>
        <Text style={styles.placeholderText}>No speed data available</Text>
      </View>
    );
  }

  // Custom bar visualization
  const maxSpeed = Math.max(...speeds, ...limits.filter(l => l > 0), 60);
  const barWidth = Math.max(3, Math.min(8, (SCREEN_WIDTH - 80) / speeds.length));

  return (
    <View style={styles.container}>
      <View style={styles.chartHeader}>
        <View style={styles.chartLegend}>
          <View style={styles.legendItem}>
            <View style={[styles.legendLine, { backgroundColor: '#007bff' }]} />
            <Text style={styles.legendLabel}>Your Speed</Text>
          </View>
          {limits.some(l => l > 0) && (
            <View style={styles.legendItem}>
              <View style={[styles.legendLine, { backgroundColor: '#dc3545' }]} />
              <Text style={styles.legendLabel}>Speed Limit</Text>
            </View>
          )}
        </View>
      </View>
      <ScrollView horizontal showsHorizontalScrollIndicator={true}>
        <View style={[styles.barChart, { height }]}>
          {/* Y-axis labels */}
          <View style={styles.yAxis}>
            <Text style={styles.yLabel}>{maxSpeed}</Text>
            <Text style={styles.yLabel}>{Math.round(maxSpeed / 2)}</Text>
            <Text style={styles.yLabel}>0</Text>
          </View>
          {/* Bars */}
          <View style={styles.barsContainer}>
            {speeds.map((speed, i) => {
              const barHeight = (speed / maxSpeed) * (height - 40);
              const limit = limits[i] || 0;
              const isOver = limit > 0 && speed > limit;
              const limitHeight = limit > 0 ? (limit / maxSpeed) * (height - 40) : 0;

              return (
                <View key={i} style={[styles.barWrapper, { width: barWidth, height: height - 40 }]}>
                  {/* Speed limit line */}
                  {limitHeight > 0 && (
                    <View
                      style={[
                        styles.limitLine,
                        { bottom: limitHeight, width: barWidth }
                      ]}
                    />
                  )}
                  {/* Speed bar */}
                  <View
                    style={[
                      styles.bar,
                      {
                        height: Math.max(1, barHeight),
                        width: barWidth - 1,
                        backgroundColor: isOver ? '#dc3545' : '#007bff',
                      },
                    ]}
                  />
                </View>
              );
            })}
          </View>
        </View>
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#fff',
    borderRadius: 12,
    overflow: 'hidden',
  },
  placeholder: {
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f0f0f0',
    borderRadius: 12,
  },
  placeholderText: {
    color: '#999',
    fontSize: 14,
  },
  chartHeader: {
    padding: 12,
    paddingBottom: 4,
  },
  chartLegend: {
    flexDirection: 'row',
    gap: 16,
  },
  legendItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  legendLine: {
    width: 16,
    height: 3,
    borderRadius: 2,
  },
  legendLabel: {
    fontSize: 12,
    color: '#6c757d',
  },
  barChart: {
    flexDirection: 'row',
    paddingHorizontal: 8,
    paddingBottom: 8,
  },
  yAxis: {
    width: 30,
    justifyContent: 'space-between',
    paddingRight: 4,
  },
  yLabel: {
    fontSize: 9,
    color: '#6c757d',
    textAlign: 'right',
  },
  barsContainer: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'flex-end',
  },
  barWrapper: {
    justifyContent: 'flex-end',
    position: 'relative',
  },
  bar: {
    borderRadius: 1,
  },
  limitLine: {
    position: 'absolute',
    height: 2,
    backgroundColor: '#dc3545',
    opacity: 0.6,
    zIndex: 1,
  },
});

export default SpeedGraph;
