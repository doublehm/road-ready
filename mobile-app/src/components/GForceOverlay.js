import React, { useMemo } from 'react';
import { View, Text, StyleSheet } from 'react-native';

const G = 9.81; // m/s² per G

// ── Force thresholds (in G) ──
// Below DEAD_ZONE the edge glow is invisible; only engine vibration and
// road noise should fall in this range.
const DEAD_ZONE_G = 0.05;
// Yellow/amber ceiling — forces above this start transitioning to red.
const YELLOW_CEIL_G = 0.20;
// Fully red at or above this value.
const RED_FLOOR_G = 0.40;
// Used to cap the opacity ramp so it doesn't exceed ~0.8.
const MAX_DISPLAY_G = 0.80;

// ── Glow geometry ──
const GLOW_LAYERS = 5;
const LAYER_PX = 12; // thickness of each gradient band

const LAYER_INDICES = Array.from({ length: GLOW_LAYERS }, (_, i) => i);

// ── Colour helpers ──

// Returns [r, g, b] interpolated from amber → red based on G-force.
function forceRGB(g) {
  if (g <= YELLOW_CEIL_G) return [245, 158, 11]; // amber  #F59E0B
  if (g >= RED_FLOOR_G) return [239, 68, 68];     // red    #EF4444
  const t = (g - YELLOW_CEIL_G) / (RED_FLOOR_G - YELLOW_CEIL_G);
  return [
    Math.round(245 + (239 - 245) * t),
    Math.round(158 + (68 - 158) * t),
    Math.round(11 + (68 - 11) * t),
  ];
}

// Base opacity for the innermost (brightest) glow layer.
function baseOpacity(g) {
  if (g <= DEAD_ZONE_G) return 0;
  const t = Math.min((g - DEAD_ZONE_G) / (MAX_DISPLAY_G - DEAD_ZONE_G), 1);
  return 0.2 + t * 0.6; // 0.2 → 0.8
}

/**
 * Full-screen overlay that lights up the edges of the screen based on
 * real-time accelerometer data.
 *
 * • Cornering  → left or right edge glows (yellow → red).
 * • Braking    → top edge glows (yellow → red).
 *
 * Uses `pointerEvents="none"` so touches pass through to the UI below.
 *
 * @param {Object}  props.acceleration  {x, y, z} in m/s² (gravity removed)
 * @param {boolean} props.isActive      only render when a ride is in progress
 */
export default function GForceOverlay({ acceleration, isActive }) {
  const { lateralG, brakingG, turnDir } = useMemo(() => {
    if (!isActive || !acceleration) {
      return { lateralG: 0, brakingG: 0, turnDir: null };
    }

    // Lateral: phone x-axis = left/right in both flat and mounted orientations.
    const latG = Math.abs(acceleration.x) / G;

    // Longitudinal: orientation-agnostic — take the larger of |y| and |z|.
    // After gravity removal both are ≈0 at rest; whichever spikes during
    // braking is the axis aligned with the road.
    const lonG = Math.max(
      Math.abs(acceleration.y),
      Math.abs(acceleration.z),
    ) / G;

    // Centrifugal force direction:
    //   +x → pushed right → car is turning LEFT  → light up LEFT edge
    //   −x → pushed left  → car is turning RIGHT → light up RIGHT edge
    const dir = latG > DEAD_ZONE_G
      ? (acceleration.x > 0 ? 'left' : 'right')
      : null;

    return { lateralG: latG, brakingG: lonG, turnDir: dir };
  }, [acceleration?.x, acceleration?.y, acceleration?.z, isActive]);

  if (!isActive) return null;

  const latOp = baseOpacity(lateralG);
  const brkOp = baseOpacity(brakingG);

  const [lr, lg, lb] = forceRGB(lateralG);
  const [br, bg, bb] = forceRGB(brakingG);

  const peakG = Math.max(lateralG, brakingG);
  const [pr, pg, pb] = peakG > 0 && peakG === lateralG
    ? forceRGB(lateralG)
    : forceRGB(brakingG);

  // Badge colour: use force colour when active, muted when idle
  const badgeColor = peakG > DEAD_ZONE_G
    ? `rgb(${pr},${pg},${pb})`
    : '#94A3B8';

  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="none">

      {/* ── Left edge glow (turning left) ── */}
      {turnDir === 'left' && LAYER_INDICES.map(i => (
        <View
          key={`l${i}`}
          style={{
            position: 'absolute',
            top: 0,
            bottom: 0,
            left: i * LAYER_PX,
            width: LAYER_PX,
            backgroundColor: `rgba(${lr},${lg},${lb},${(
              latOp * (1 - i / GLOW_LAYERS)
            ).toFixed(3)})`,
          }}
        />
      ))}

      {/* ── Right edge glow (turning right) ── */}
      {turnDir === 'right' && LAYER_INDICES.map(i => (
        <View
          key={`r${i}`}
          style={{
            position: 'absolute',
            top: 0,
            bottom: 0,
            right: i * LAYER_PX,
            width: LAYER_PX,
            backgroundColor: `rgba(${lr},${lg},${lb},${(
              latOp * (1 - i / GLOW_LAYERS)
            ).toFixed(3)})`,
          }}
        />
      ))}

      {/* ── Top edge glow (braking / longitudinal force) ── */}
      {brkOp > 0 && LAYER_INDICES.map(i => (
        <View
          key={`t${i}`}
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            top: i * LAYER_PX,
            height: LAYER_PX,
            backgroundColor: `rgba(${br},${bg},${bb},${(
              brkOp * (1 - i / GLOW_LAYERS)
            ).toFixed(3)})`,
          }}
        />
      ))}

      {/* ── G-force badge (always visible during ride) ── */}
      <View style={styles.badge}>
        <Text style={[styles.badgeLabel, { color: badgeColor }]}>
          {peakG.toFixed(1)}
        </Text>
        <Text style={styles.badgeUnit}>G</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    position: 'absolute',
    bottom: 104,
    alignSelf: 'center',
    flexDirection: 'row',
    alignItems: 'baseline',
    backgroundColor: 'rgba(0, 0, 0, 0.55)',
    paddingHorizontal: 14,
    paddingVertical: 5,
    borderRadius: 14,
  },
  badgeLabel: {
    fontSize: 22,
    fontWeight: '900',
    letterSpacing: 0.5,
  },
  badgeUnit: {
    fontSize: 13,
    fontWeight: '800',
    color: '#94A3B8',
    marginLeft: 2,
  },
});
