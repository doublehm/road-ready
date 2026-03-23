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
 * @param {boolean} isActive     only render during active ride
 */
export default function TelemetryPanel({
  acceleration,
  rotation,
  prevAcceleration,
  sampleIntervalMs = 100,
  heading,
  isActive,
}) {
  const gauges = useMemo(() => {
    if (!isActive || !acceleration) return [];

    const ax = acceleration.x || 0;
    const ay = acceleration.y || 0;
    const az = acceleration.z || 0;

    // Lateral G — left/right
    const lateralG = Math.abs(ax) / G;
    const lateralDir = ax > 0.1 ? '◀' : ax < -0.1 ? '▶' : '–';

    // Braking G — negative forward = deceleration
    // Orientation-agnostic: use larger of |y|, |z|
    const lonG = Math.max(Math.abs(ay), Math.abs(az)) / G;
    const isBraking = ay < -0.5 || az < -0.5;
    const brakingG = isBraking ? lonG : 0;

    // Throttle G — positive forward = acceleration
    const throttleG = !isBraking ? lonG : 0;

    // Vertical G — deviation from 1g on Z axis (road bumps)
    // Note: after gravity removal, az ≈ 0 at rest; spikes indicate impacts
    const verticalG = Math.abs(az) / G;

    // Grip (Friction Circle) — combined lateral + longitudinal magnitude
    const gripG = Math.sqrt(lateralG * lateralG + lonG * lonG);

    // Steering rate — gyroscope magnitude (rad/s)
    const rx = rotation?.x || 0;
    const ry = rotation?.y || 0;
    const rz = rotation?.z || 0;
    const steeringRate = Math.sqrt(rx * rx + ry * ry + rz * rz);

    // Jerk — rate of acceleration change (m/s³)
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
        id: 'lateral', icon: 'swap-horizontal', label: 'LATERAL',
        value: lateralG, unit: 'G', suffix: lateralDir,
        thresh: THRESHOLDS.lateral,
      },
      {
        id: 'braking', icon: 'hand-left', label: 'BRAKING',
        value: brakingG, unit: 'G',
        thresh: THRESHOLDS.braking,
      },
      {
        id: 'throttle', icon: 'rocket', label: 'THROTTLE',
        value: throttleG, unit: 'G',
        thresh: THRESHOLDS.throttle,
      },
      {
        id: 'vertical', icon: 'trending-up', label: 'VERTICAL',
        value: verticalG, unit: 'G',
        thresh: THRESHOLDS.vertical,
      },
      {
        id: 'grip', icon: 'radio-button-on', label: 'GRIP',
        value: gripG, unit: 'G',
        thresh: THRESHOLDS.grip,
      },
      {
        id: 'steering', icon: 'sync', label: 'STEERING',
        value: steeringRate, unit: 'rad/s',
        thresh: THRESHOLDS.steering,
      },
      {
        id: 'jerk', icon: 'flash', label: 'JERK',
        value: jerk, unit: 'm/s³',
        thresh: THRESHOLDS.jerk,
      },
    ];
  }, [
    acceleration?.x, acceleration?.y, acceleration?.z,
    rotation?.x, rotation?.y, rotation?.z,
    prevAcceleration?.x, prevAcceleration?.y, prevAcceleration?.z,
    sampleIntervalMs, isActive,
  ]);

  if (!isActive || gauges.length === 0) return null;

  return (
    <View style={styles.container}>
      <View style={styles.headerRow}>
        <Ionicons name="analytics" size={12} color="#64748B" />
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
            <View key={g.id} style={styles.gauge}>
              <View style={styles.gaugeLabelRow}>
                <Ionicons name={g.icon} size={10} color={color} />
                <Text style={styles.gaugeLabel}>{g.label}</Text>
                {g.suffix && (
                  <Text style={[styles.gaugeSuffix, { color }]}>{g.suffix}</Text>
                )}
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
    padding: 10,
    borderWidth: 1,
    borderColor: 'rgba(100, 116, 139, 0.2)',
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginBottom: 8,
  },
  headerText: {
    fontSize: 10,
    fontWeight: '900',
    color: '#64748B',
    letterSpacing: 1,
    flex: 1,
  },
  headingText: {
    fontSize: 10,
    fontWeight: '800',
    color: '#64748B',
    fontVariant: ['tabular-nums'],
  },
  gaugeGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
  },
  gauge: {
    width: '31%',
    backgroundColor: 'rgba(30, 41, 59, 0.6)',
    borderRadius: 10,
    padding: 6,
  },
  gaugeLabelRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
    marginBottom: 4,
  },
  gaugeLabel: {
    fontSize: 8,
    fontWeight: '900',
    color: '#94A3B8',
    letterSpacing: 0.5,
    flex: 1,
  },
  gaugeSuffix: {
    fontSize: 9,
    fontWeight: '900',
  },
  barTrack: {
    height: 3,
    backgroundColor: 'rgba(51, 65, 85, 0.6)',
    borderRadius: 2,
    marginBottom: 3,
    overflow: 'hidden',
  },
  barFill: {
    height: '100%',
    borderRadius: 2,
  },
  gaugeValue: {
    fontSize: 13,
    fontWeight: '900',
    fontVariant: ['tabular-nums'],
  },
  gaugeUnit: {
    fontSize: 8,
    fontWeight: '700',
    color: '#64748B',
  },
});
