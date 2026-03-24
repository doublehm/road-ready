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
  return '#334155'; // dormant / below noise floor
}

function barWidth(value, thresh) {
  const max = thresh.red * 1.5;
  return Math.min(Math.max((value / max) * 100, 0), 100);
}

/**
 * TelemetryPanel — Real-time force & sensor indicator dashboard.
 *
 * Shows labeled gauges for every measured force so the driver/supervisor
 * can monitor exactly what the system is detecting.
 *
 * @param {Object} acceleration  {x, y, z} in m/s² (gravity-removed)
 * @param {Object} rotation      {x, y, z} in rad/s
 * @param {Object} prevAcceleration  previous frame {x, y, z} for jerk calc
 * @param {number} sampleIntervalMs  time between frames (default 100ms for 10Hz)
 * @param {number} heading       GPS heading 0–360°
 * @param {number} speed         current GPS speed in km/h
 * @param {number} prevSpeed     previous GPS speed in km/h
 * @param {boolean} isActive     only render during active ride
 * @param {Function} onThresholdExceeded  callback({id, label, value, unit}) when gauge hits red
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

  if (!isActive || gauges.length === 0) return null;

  // Fire threshold callback when any gauge hits red (with 5s cooldown per gauge)
  if (onThresholdExceeded) {
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
  }

  return (
    <View style={styles.container}>
      <View style={styles.headerRow}>
        <Ionicons name="analytics" size={14} color="#64748B" />
        <Text style={styles.headerText}>LIVE TELEMETRY</Text>
        {heading != null && (
          <Text style={styles.headingText}>HDG {Math.round(heading)}°</Text>
        )}
      </View>
      <View style={styles.gaugeGrid}>
        {gauges.map(g => {
          const color = getColor(g.value, g.thresh);
          const width = barWidth(g.value, g.thresh);
          return (
            <View key={g.id} style={[styles.gauge, g.wide && styles.gaugeWide]}>
              <View style={styles.gaugeLabelRow}>
                <Ionicons name={g.icon} size={14} color={color} />
                <Text style={styles.gaugeLabel}>{g.label}</Text>
              </View>
              <View style={styles.barTrack}>
                <View style={[styles.barFill, { width: `${width}%`, backgroundColor: color }]} />
              </View>
              <Text style={[styles.gaugeValue, { color }]}>
                {g.value < 10 ? g.value.toFixed(2) : g.value.toFixed(1)}
                <Text style={styles.gaugeUnit}> {g.unit}</Text>
              </Text>
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
    padding: 14,
    borderWidth: 1,
    borderColor: 'rgba(100, 116, 139, 0.2)',
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginBottom: 10,
  },
  headerText: {
    fontSize: 11,
    fontWeight: '900',
    color: '#64748B',
    letterSpacing: 1,
    flex: 1,
  },
  headingText: {
    fontSize: 11,
    fontWeight: '800',
    color: '#64748B',
    fontVariant: ['tabular-nums'],
  },
  gaugeGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  gauge: {
    width: '47%',
    backgroundColor: 'rgba(30, 41, 59, 0.6)',
    borderRadius: 12,
    padding: 10,
  },
  gaugeWide: {
    width: '97%',
  },
  gaugeLabelRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    marginBottom: 6,
  },
  gaugeLabel: {
    fontSize: 11,
    fontWeight: '900',
    color: '#94A3B8',
    letterSpacing: 0.5,
    flex: 1,
  },
  barTrack: {
    height: 6,
    backgroundColor: 'rgba(51, 65, 85, 0.6)',
    borderRadius: 3,
    marginBottom: 5,
    overflow: 'hidden',
  },
  barFill: {
    height: '100%',
    borderRadius: 3,
  },
  gaugeValue: {
    fontSize: 18,
    fontWeight: '900',
    fontVariant: ['tabular-nums'],
  },
  gaugeUnit: {
    fontSize: 10,
    fontWeight: '700',
    color: '#64748B',
  },
});
