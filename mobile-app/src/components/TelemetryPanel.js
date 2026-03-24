import React, { useMemo } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';

const G = 9.81;

// ── Physics-based thresholds (must mirror diagnostic_evaluator.py) ──
const THRESHOLDS = {
  lateral:      { green: 0.10, yellow: 0.25, red: 0.35 },
  braking:      { green: 0.08, yellow: 0.30, red: 0.40 },
  throttle:     { green: 0.05, yellow: 0.20, red: 0.30 },
  vertical:     { green: 0.05, yellow: 0.20, red: 0.30 },
  grip:         { green: 0.15, yellow: 0.35, red: 0.50 },
  steering:     { green: 0.20, yellow: 0.50, red: 0.80 },
  jerk:         { green: 1.5,  yellow: 3.0,  red: 5.0  },
};

function getColor(value, thresh) {
  if (value >= thresh.red)    return '#EF4444';
  if (value >= thresh.yellow) return '#F59E0B';
  if (value >= thresh.green)  return '#22C55E';
  return '#334155';
}

function barWidth(value, thresh) {
  const max = thresh.red * 1.5;
  return Math.min(Math.max((value / max) * 100, 0), 100);
}

function formatTime(seconds) {
  const mins = Math.floor(seconds / 60);
  const secs = seconds % 60;
  return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
}

function speedColor(spd, limit) {
  if (!limit || !spd) return '#94A3B8';
  const excess = spd - limit;
  if (excess > 10) return '#EF4444';
  if (excess > 0) return '#F59E0B';
  return '#22C55E';
}

/**
 * TelemetryPanel — Unified live dashboard: ride stats + force gauges.
 */
export default function TelemetryPanel({
  acceleration,
  rotation,
  prevAcceleration,
  sampleIntervalMs = 100,
  heading,
  speed,
  prevSpeed,
  isActive,
  onThresholdExceeded,
  duration,
  distance,
  speedLimit,
  zoneType,
  roadName,
  altitude,
  schoolZoneViolations,
  speedingPercent,
  maxExcessKmh,
  speedVariance,
}) {
  const lastFiredRef = React.useRef({});
  const gauges = useMemo(() => {
    if (!isActive || !acceleration) return [];

    const ax = acceleration.x || 0;
    const ay = acceleration.y || 0;
    const az = acceleration.z || 0;

    // Lateral G — left/right cornering force
    const lateralG = Math.abs(ax) / G;

    // Longitudinal G — orientation-agnostic magnitude (max of |y|, |z|)
    const lonG = Math.max(Math.abs(ay), Math.abs(az)) / G;

    // Use GPS speed delta to determine braking vs acceleration.
    // Accelerometer sign is phone-orientation-dependent and unreliable.
    // GPS speed change is always correct: decreasing = braking, increasing = accel.
    const currentSpeed = typeof speed === 'number' ? speed : 0;
    const previousSpeed = typeof prevSpeed === 'number' ? prevSpeed : currentSpeed;
    const speedDecreasing = currentSpeed < previousSpeed - 0.5;
    const speedIncreasing = currentSpeed > previousSpeed + 0.5;

    const brakingG = speedDecreasing ? lonG : 0;
    const accelG = speedIncreasing ? lonG : 0;

    // Vertical G — deviation on Z axis (road bumps, potholes)
    const verticalG = Math.abs(az) / G;

    // Grip (Friction Circle) — combined lateral + longitudinal magnitude
    const gripG = Math.sqrt(lateralG * lateralG + lonG * lonG);

    // Turn rate — gyroscope magnitude (rad/s), measures how fast the car rotates
    const rx = rotation?.x || 0;
    const ry = rotation?.y || 0;
    const rz = rotation?.z || 0;
    const turnRate = Math.sqrt(rx * rx + ry * ry + rz * rz);

    // Smoothness (jerk) — rate of acceleration change (m/s³)
    // Low = smooth driving, high = abrupt force changes
    let jerk = 0;
    if (prevAcceleration && sampleIntervalMs > 0) {
      const dt = sampleIntervalMs / 1000;
      const dx = ax - (prevAcceleration.x || 0);
      const dy = ay - (prevAcceleration.y || 0);
      const dz = az - (prevAcceleration.z || 0);
      jerk = Math.sqrt(dx * dx + dy * dy + dz * dz) / dt;
    }

    return [
      {
        id: 'left-turn', icon: 'arrow-back-circle', label: 'LEFT TURN',
        value: ax > 0.1 ? lateralG : 0, unit: 'G',
        thresh: THRESHOLDS.lateral,
        wide: false, faultType: 'sharp_turn',
      },
      {
        id: 'right-turn', icon: 'arrow-forward-circle', label: 'RIGHT TURN',
        value: ax < -0.1 ? lateralG : 0, unit: 'G',
        thresh: THRESHOLDS.lateral,
        wide: false, faultType: 'sharp_turn',
      },
      {
        id: 'stop-force', icon: 'stop-circle', label: 'STOP FORCE',
        value: brakingG, unit: 'G',
        thresh: THRESHOLDS.braking,
        wide: true, faultType: 'harsh_braking',
      },
      {
        id: 'accel', icon: 'rocket', label: 'ACCEL',
        value: accelG, unit: 'G',
        thresh: THRESHOLDS.throttle,
        wide: false, faultType: 'harsh_acceleration',
      },
      {
        id: 'grip', icon: 'radio-button-on', label: 'GRIP',
        value: gripG, unit: 'G',
        thresh: THRESHOLDS.grip,
        wide: false, faultType: null,
      },
      {
        id: 'turn-rate', icon: 'sync', label: 'TURN RATE',
        value: turnRate, unit: 'rad/s',
        thresh: THRESHOLDS.steering,
        wide: false, faultType: 'sharp_turn',
      },
      {
        id: 'smoothness', icon: 'flash', label: 'SMOOTHNESS',
        value: jerk, unit: 'm/s³',
        thresh: THRESHOLDS.jerk,
        wide: false, faultType: 'erratic_speed',
      },
      {
        id: 'vertical', icon: 'trending-up', label: 'VERTICAL',
        value: verticalG, unit: 'G',
        thresh: THRESHOLDS.vertical,
        wide: false, faultType: null,
      },
    ];
  }, [
    acceleration?.x, acceleration?.y, acceleration?.z,
    rotation?.x, rotation?.y, rotation?.z,
    prevAcceleration?.x, prevAcceleration?.y, prevAcceleration?.z,
    sampleIntervalMs, isActive, speed, prevSpeed,
  ]);

  // Fire threshold callback when any gauge hits red (with 5s cooldown per gauge)
  React.useEffect(() => {
    if (!onThresholdExceeded || gauges.length === 0) return;
    const now = Date.now();
    gauges.forEach(g => {
      if (g.value >= g.thresh.red && g.faultType) {
        const lastFired = lastFiredRef.current[g.id] || 0;
        if (now - lastFired > 5000) {
          lastFiredRef.current[g.id] = now;
          onThresholdExceeded({
            id: g.id,
            label: g.label,
            value: g.value,
            unit: g.unit,
            faultType: g.faultType,
          });
        }
      }
    });
  }, [gauges, onThresholdExceeded]);

  if (!isActive || gauges.length === 0) return null;

  const currentSpeed = typeof speed === 'number' ? speed : 0;
  const spdColor = speedColor(currentSpeed, speedLimit);
  const excess = speedLimit ? Math.round(currentSpeed - speedLimit) : null;

  return (
    <View style={styles.container}>
      {/* ── Ride Stats Row ── */}
      <View style={styles.statsRow}>
        <View style={styles.statCell}>
          <Text style={styles.statLabel}>TIME</Text>
          <Text style={styles.statValue}>{formatTime(duration || 0)}</Text>
        </View>
        <View style={styles.statDivider} />
        <View style={styles.statCell}>
          <Text style={styles.statLabel}>DIST</Text>
          <Text style={styles.statValue}>{(distance || 0).toFixed(2)} km</Text>
        </View>
        <View style={styles.statDivider} />
        <View style={styles.statCell}>
          <Text style={styles.statLabel}>SPEED</Text>
          <Text style={[styles.statValueBig, { color: spdColor }]}>{Math.round(currentSpeed)}</Text>
        </View>
        <View style={styles.statDivider} />
        <View style={styles.statCell}>
          <Text style={styles.statLabel}>LIMIT</Text>
          <Text style={styles.statValue}>{speedLimit || '--'}</Text>
          {zoneType === 'school' && (
            <View style={styles.schoolBadge}>
              <Text style={styles.schoolBadgeText}>SCHOOL</Text>
            </View>
          )}
        </View>
      </View>

      {/* ── Location & Altitude ── */}
      <View style={styles.infoRow}>
        {roadName ? (
          <View style={styles.infoChip}>
            <Ionicons name="navigate" size={13} color="#64748B" />
            <Text style={styles.infoText} numberOfLines={1}>{roadName}</Text>
          </View>
        ) : null}
        {zoneType && zoneType !== 'regular' ? (
          <View style={[styles.infoChip, styles.zoneChip]}>
            <Ionicons name="warning" size={13} color="#F59E0B" />
            <Text style={[styles.infoText, { color: '#F59E0B' }]}>{zoneType.toUpperCase()}</Text>
          </View>
        ) : null}
        {altitude != null ? (
          <View style={styles.infoChip}>
            <Ionicons name="trending-up" size={13} color="#64748B" />
            <Text style={styles.infoText}>{Math.round(altitude)}m</Text>
          </View>
        ) : null}
        {heading != null ? (
          <View style={styles.infoChip}>
            <Ionicons name="compass" size={13} color="#64748B" />
            <Text style={styles.infoText}>{Math.round(heading)}°</Text>
          </View>
        ) : null}
      </View>

      {/* ── Speed Analysis Row ── */}
      <View style={styles.analysisRow}>
        {excess != null && (
          <View style={styles.analysisStat}>
            <Text style={styles.analysisLabel}>EXCESS</Text>
            <Text style={[styles.analysisValue, {
              color: excess > 10 ? '#EF4444' : excess > 0 ? '#F59E0B' : '#22C55E'
            }]}>{excess > 0 ? '+' : ''}{excess} km/h</Text>
          </View>
        )}
        {speedingPercent != null && (
          <View style={styles.analysisStat}>
            <Text style={styles.analysisLabel}>OVER LIMIT</Text>
            <Text style={[styles.analysisValue, {
              color: speedingPercent > 10 ? '#EF4444' : speedingPercent > 0 ? '#F59E0B' : '#22C55E'
            }]}>{Math.round(speedingPercent)}%</Text>
          </View>
        )}
        {maxExcessKmh != null && maxExcessKmh > 0 && (
          <View style={styles.analysisStat}>
            <Text style={styles.analysisLabel}>MAX EXCESS</Text>
            <Text style={[styles.analysisValue, { color: '#EF4444' }]}>+{Math.round(maxExcessKmh)}</Text>
          </View>
        )}
        {speedVariance != null && (
          <View style={styles.analysisStat}>
            <Text style={styles.analysisLabel}>VARIANCE</Text>
            <Text style={[styles.analysisValue, {
              color: speedVariance > 3 ? '#F59E0B' : '#94A3B8'
            }]}>{speedVariance.toFixed(1)}</Text>
          </View>
        )}
        {schoolZoneViolations > 0 && (
          <View style={styles.analysisStat}>
            <Text style={[styles.analysisLabel, { color: '#EF4444' }]}>SCHOOL ZONE</Text>
            <Text style={[styles.analysisValue, { color: '#EF4444' }]}>{schoolZoneViolations}s</Text>
          </View>
        )}
      </View>

      {/* ── Force Gauges ── */}
      <View style={styles.gaugeGrid}>
        {gauges.map(g => {
          const color = getColor(g.value, g.thresh);
          const width = barWidth(g.value, g.thresh);
          return (
            <View key={g.id} style={[styles.gauge, g.wide && styles.gaugeWide]}>
              <View style={styles.gaugeLabelRow}>
                <Ionicons name={g.icon} size={13} color={color} />
                <Text style={styles.gaugeLabel}>{g.label}</Text>
                <Text style={[styles.gaugeValue, { color }]}>
                  {g.value < 10 ? g.value.toFixed(2) : g.value.toFixed(1)}
                  <Text style={styles.gaugeUnit}> {g.unit}</Text>
                </Text>
              </View>
              <View style={styles.barTrack}>
                <View style={[styles.barFill, { width: `${width}%`, backgroundColor: color }]} />
              </View>
            </View>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: 'rgba(11, 19, 38, 0.92)',
    marginHorizontal: 16,
    marginTop: 10,
    borderRadius: 16,
    padding: 10,
    borderWidth: 1,
    borderColor: 'rgba(100, 116, 139, 0.2)',
  },
  /* ── Ride Stats Row (Time | Dist | Speed | Limit) ── */
  statsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 6,
  },
  statCell: {
    flex: 1,
    alignItems: 'center',
  },
  statLabel: {
    fontSize: 8,
    fontWeight: '800',
    color: '#64748B',
    letterSpacing: 1,
  },
  statValue: {
    fontSize: 14,
    fontWeight: '900',
    color: '#E2E8F0',
    fontVariant: ['tabular-nums'],
  },
  statValueBig: {
    fontSize: 22,
    fontWeight: '900',
    fontVariant: ['tabular-nums'],
  },
  statDivider: {
    width: 1,
    height: 28,
    backgroundColor: 'rgba(100, 116, 139, 0.25)',
  },
  schoolBadge: {
    backgroundColor: '#F59E0B',
    borderRadius: 4,
    paddingHorizontal: 4,
    paddingVertical: 1,
    marginTop: 2,
  },
  schoolBadgeText: {
    fontSize: 7,
    fontWeight: '900',
    color: '#0F172A',
    letterSpacing: 0.5,
  },
  /* ── Info Chips (road, zone, altitude, heading) ── */
  infoRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    gap: 8,
    marginBottom: 8,
  },
  infoChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    backgroundColor: 'rgba(30, 41, 59, 0.6)',
    borderRadius: 8,
    paddingHorizontal: 8,
    paddingVertical: 5,
  },
  zoneChip: {
    borderWidth: 1,
    borderColor: 'rgba(245, 158, 11, 0.3)',
  },
  infoText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#CBD5E1',
    maxWidth: 140,
  },
  /* ── Speed Analysis Row ── */
  analysisRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    gap: 14,
    marginBottom: 10,
  },
  analysisStat: {
    alignItems: 'center',
    minWidth: 55,
  },
  analysisLabel: {
    fontSize: 9,
    fontWeight: '800',
    color: '#64748B',
    letterSpacing: 0.5,
  },
  analysisValue: {
    fontSize: 15,
    fontWeight: '900',
    fontVariant: ['tabular-nums'],
    color: '#94A3B8',
  },
  /* ── Force Gauges ── */
  gaugeGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  gauge: {
    width: '47%',
    backgroundColor: 'rgba(30, 41, 59, 0.6)',
    borderRadius: 10,
    padding: 7,
  },
  gaugeWide: {
    width: '97%',
  },
  gaugeLabelRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    marginBottom: 4,
  },
  gaugeLabel: {
    fontSize: 10,
    fontWeight: '900',
    color: '#94A3B8',
    letterSpacing: 0.5,
    flex: 1,
  },
  barTrack: {
    height: 5,
    backgroundColor: 'rgba(51, 65, 85, 0.6)',
    borderRadius: 3,
    overflow: 'hidden',
  },
  barFill: {
    height: '100%',
    borderRadius: 3,
  },
  gaugeValue: {
    fontSize: 14,
    fontWeight: '900',
    fontVariant: ['tabular-nums'],
  },
  gaugeUnit: {
    fontSize: 9,
    fontWeight: '700',
    color: '#64748B',
  },
});
