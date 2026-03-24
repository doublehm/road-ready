import React, { useMemo, useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Modal, Dimensions } from 'react-native';
import OSMMap from './OSMMap';
import Ionicons from '@expo/vector-icons/Ionicons';

const SEGMENT_COLORS = {
  red: '#EF4444',
  yellow: '#F59E0B',
  green: '#15803D',
};

const EVENT_META = {
  speeding:                  { icon: 'speedometer',      color: '#EF4444', label: 'Speeding' },
  harsh_braking:             { icon: 'hand-left',        color: '#F59E0B', label: 'Harsh Braking' },
  sharp_turn:                { icon: 'refresh',          color: '#F59E0B', label: 'Sharp Turn' },
  sudden_stop:               { icon: 'stop-circle',      color: '#EF4444', label: 'Sudden Stop' },
  harsh_acceleration:        { icon: 'rocket',           color: '#F97316', label: 'Harsh Accel' },
  erratic_speed:             { icon: 'pulse',            color: '#A855F7', label: 'Erratic Speed' },
  lane_weaving:              { icon: 'swap-horizontal',  color: '#A855F7', label: 'Lane Weaving' },
  friction_circle_violation: { icon: 'warning',          color: '#EF4444', label: 'Grip Limit' },
  human_flag:                { icon: 'flag',             color: '#3B82F6', label: 'Supervisor Flag' },
};

/**
 * RouteReplayMap - Displays a color-coded route map with event markers.
 *
 * @param {Array} routeSegments - Array of {start, end, color, speed, limit, zone_type}
 * @param {Array} events - Array of {type, lat, lng, severity, description}
 * @param {Array} routeCoords - Fallback: simple array of {latitude, longitude}
 * @param {number} height - Map height (default 300)
 */
const { height: SCREEN_HEIGHT } = Dimensions.get('window');

const RouteReplayMap = ({ routeSegments = [], events = [], routeCoords = [], height = 300 }) => {
  const [fullscreen, setFullscreen] = useState(false);
  // Calculate map bounds
  const region = useMemo(() => {
    const coords = [];

    if (routeSegments.length > 0) {
      routeSegments.forEach(seg => {
        if (seg.start?.lat && seg.start?.lng) {
          coords.push({ latitude: seg.start.lat, longitude: seg.start.lng });
        }
        if (seg.end?.lat && seg.end?.lng) {
          coords.push({ latitude: seg.end.lat, longitude: seg.end.lng });
        }
      });
    } else if (routeCoords.length > 0) {
      coords.push(...routeCoords.filter(c => c.latitude && c.longitude));
    }

    if (coords.length === 0) {
      return { latitude: 49.2827, longitude: -123.1207, latitudeDelta: 0.05, longitudeDelta: 0.05 };
    }

    const lats = coords.map(c => c.latitude);
    const lngs = coords.map(c => c.longitude);
    const minLat = Math.min(...lats);
    const maxLat = Math.max(...lats);
    const minLng = Math.min(...lngs);
    const maxLng = Math.max(...lngs);

    return {
      latitude: (minLat + maxLat) / 2,
      longitude: (minLng + maxLng) / 2,
      latitudeDelta: Math.max(0.005, (maxLat - minLat) * 1.3),
      longitudeDelta: Math.max(0.005, (maxLng - minLng) * 1.3),
    };
  }, [routeSegments, routeCoords]);

  // Group consecutive segments by color for fewer polylines
  const polylines = useMemo(() => {
    if (routeSegments.length === 0) return [];

    const lines = [];
    let current = {
      coords: [],
      color: routeSegments[0]?.color || 'green',
    };

    routeSegments.forEach((seg, i) => {
      if (seg.color !== current.color && current.coords.length > 0) {
        // Add the last point of previous group as first of new (for continuity)
        const lastCoord = current.coords[current.coords.length - 1];
        lines.push({ ...current });
        current = { coords: [lastCoord], color: seg.color };
      }

      if (seg.start?.lat && seg.start?.lng) {
        current.coords.push({ latitude: seg.start.lat, longitude: seg.start.lng });
      }
      if (seg.end?.lat && seg.end?.lng) {
        current.coords.push({ latitude: seg.end.lat, longitude: seg.end.lng });
      }
    });

    if (current.coords.length > 0) {
      lines.push(current);
    }

    return lines;
  }, [routeSegments]);

  // Filter events with valid coordinates
  const eventMarkers = useMemo(() => {
    return events
      .filter(e => e.lat && e.lng)
      .slice(0, 50);
  }, [events]);

  const formatEventTime = (timestamp) => {
    if (!timestamp) return '';
    const d = new Date(timestamp * 1000);
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  };

  const buildMarkers = () => {
    const markers = eventMarkers.map(event => {
      const meta = EVENT_META[event.type] || EVENT_META.speeding;
      return {
        latitude: event.lat,
        longitude: event.lng,
        title: meta.label,
        description: event.description || '',
        color: meta.color,
        radius: event.severity === 'high' ? 10 : 8,
        time: formatEventTime(event.timestamp),
        severity: event.severity || 'medium',
      };
    });

    if (routeSegments.length > 0 && routeSegments[0].start?.lat) {
      markers.push({
        latitude: routeSegments[0].start.lat,
        longitude: routeSegments[0].start.lng,
        title: "Start",
        color: '#15803D',
        radius: 12,
      });
    }
    if (routeSegments.length > 0 && routeSegments[routeSegments.length - 1].end?.lat) {
      markers.push({
        latitude: routeSegments[routeSegments.length - 1].end.lat,
        longitude: routeSegments[routeSegments.length - 1].end.lng,
        title: "End",
        color: '#3B82F6',
        radius: 12,
      });
    }

    return markers;
  };

  // Count events by type for the summary
  const eventCounts = useMemo(() => {
    const counts = {};
    events.forEach(e => {
      counts[e.type] = (counts[e.type] || 0) + 1;
    });
    return counts;
  }, [events]);

  if (routeSegments.length === 0 && routeCoords.length === 0) {
    return (
      <View style={[styles.placeholder, { height }]}>
        <Ionicons name="map-outline" size={40} color="#475569" />
        <Text style={styles.placeholderText}>No route data available</Text>
      </View>
    );
  }

  const allMarkers = useMemo(() => buildMarkers(), [eventMarkers, routeSegments]);

  const allPolylines = useMemo(() => [
    ...polylines.map((line) => ({
      coordinates: line.coords,
      strokeWidth: 5,
      strokeColor: SEGMENT_COLORS[line.color] || SEGMENT_COLORS.green
    })),
    ...(polylines.length === 0 && routeCoords.length > 0 ? [{
      coordinates: routeCoords,
      strokeWidth: 4,
      strokeColor: "#3B82F6"
    }] : [])
  ], [polylines, routeCoords]);

  return (
    <View style={[styles.container, { height }]}>
      <OSMMap 
        style={styles.map} 
        region={region}
        markers={allMarkers}
        polylines={allPolylines}
      />

      {/* Expand to fullscreen button */}
      <TouchableOpacity
        style={styles.expandButton}
        onPress={() => setFullscreen(true)}
        activeOpacity={0.7}
      >
        <Ionicons name="expand" size={18} color="#FFFFFF" />
      </TouchableOpacity>

      {/* Legend */}
      <View style={styles.legend}>
        <View style={styles.legendItem}>
          <View style={[styles.legendDot, { backgroundColor: SEGMENT_COLORS.green }]} />
          <Text style={styles.legendText}>Within limit</Text>
        </View>
        <View style={styles.legendItem}>
          <View style={[styles.legendDot, { backgroundColor: SEGMENT_COLORS.yellow }]} />
          <Text style={styles.legendText}>Slightly over</Text>
        </View>
        <View style={styles.legendItem}>
          <View style={[styles.legendDot, { backgroundColor: SEGMENT_COLORS.red }]} />
          <Text style={styles.legendText}>Over limit</Text>
        </View>
      </View>

      {/* Event Summary Overlay */}
      {Object.keys(eventCounts).length > 0 && (
        <View style={styles.eventSummary}>
          {Object.entries(eventCounts).map(([type, count]) => {
            const meta = EVENT_META[type];
            if (!meta) return null;
            return (
              <View key={type} style={styles.eventSummaryItem}>
                <Ionicons name={meta.icon} size={12} color={meta.color} />
                <Text style={[styles.eventSummaryCount, { color: meta.color }]}>{count}</Text>
              </View>
            );
          })}
        </View>
      )}

      {/* Fullscreen Map Modal */}
      <Modal visible={fullscreen} animationType="slide" statusBarTranslucent>
        <View style={styles.fullscreenContainer}>
          <OSMMap 
            style={styles.fullscreenMap}
            region={region}
            markers={allMarkers}
            polylines={allPolylines}
          />

          {/* Close button */}
          <TouchableOpacity
            style={styles.closeButton}
            onPress={() => setFullscreen(false)}
            activeOpacity={0.7}
          >
            <Ionicons name="close" size={24} color="#FFFFFF" />
          </TouchableOpacity>

          {/* Legend in fullscreen */}
          <View style={[styles.legend, styles.fullscreenLegend]}>
            <View style={styles.legendItem}>
              <View style={[styles.legendDot, { backgroundColor: SEGMENT_COLORS.green }]} />
              <Text style={styles.legendText}>Within limit</Text>
            </View>
            <View style={styles.legendItem}>
              <View style={[styles.legendDot, { backgroundColor: SEGMENT_COLORS.yellow }]} />
              <Text style={styles.legendText}>Slightly over</Text>
            </View>
            <View style={styles.legendItem}>
              <View style={[styles.legendDot, { backgroundColor: SEGMENT_COLORS.red }]} />
              <Text style={styles.legendText}>Over limit</Text>
            </View>
          </View>

          {/* Event list panel in fullscreen */}
          {eventMarkers.length > 0 && (
            <View style={styles.fullscreenEventPanel}>
              <Text style={styles.fullscreenEventTitle}>
                {eventMarkers.length} EVENT{eventMarkers.length !== 1 ? 'S' : ''} ON ROUTE
              </Text>
              {eventMarkers.slice(0, 8).map((event, idx) => {
                const meta = EVENT_META[event.type] || EVENT_META.speeding;
                return (
                  <View key={idx} style={styles.fullscreenEventItem}>
                    <View style={[styles.fullscreenEventDot, { backgroundColor: meta.color }]} />
                    <Text style={styles.fullscreenEventLabel}>{meta.label}</Text>
                    <Text style={styles.fullscreenEventTime}>
                      {formatEventTime(event.timestamp)}
                    </Text>
                  </View>
                );
              })}
              {eventMarkers.length > 8 && (
                <Text style={styles.fullscreenEventMore}>
                  +{eventMarkers.length - 8} more — tap markers on map
                </Text>
              )}
            </View>
          )}
        </View>
      </Modal>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    borderRadius: 12,
    overflow: 'hidden',
    backgroundColor: '#1E293B',
  },
  map: {
    flex: 1,
  },
  placeholder: {
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#1E293B',
    borderRadius: 12,
  },
  placeholderText: {
    color: '#64748B',
    marginTop: 8,
    fontSize: 14,
  },
  legend: {
    position: 'absolute',
    bottom: 8,
    left: 8,
    flexDirection: 'row',
    backgroundColor: 'rgba(19,27,46,0.9)',
    borderRadius: 8,
    padding: 6,
    gap: 12,
  },
  legendItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  legendDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  legendText: {
    fontSize: 10,
    color: '#FFFFFF',
  },
  eventSummary: {
    position: 'absolute',
    top: 8,
    right: 8,
    backgroundColor: 'rgba(19,27,46,0.9)',
    borderRadius: 8,
    padding: 6,
    gap: 4,
  },
  eventSummaryItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  eventSummaryCount: {
    fontSize: 11,
    fontWeight: '800',
  },
  expandButton: {
    position: 'absolute',
    top: 8,
    left: 8,
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: 'rgba(19, 27, 46, 0.9)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  fullscreenContainer: {
    flex: 1,
    backgroundColor: '#0B1326',
  },
  fullscreenMap: {
    flex: 1,
  },
  closeButton: {
    position: 'absolute',
    top: 50,
    right: 16,
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(19, 27, 46, 0.9)',
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: 10,
  },
  fullscreenLegend: {
    bottom: 120,
    left: 16,
  },
  fullscreenEventPanel: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: 'rgba(11, 19, 38, 0.95)',
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: 30,
    maxHeight: SCREEN_HEIGHT * 0.3,
  },
  fullscreenEventTitle: {
    fontSize: 11,
    fontWeight: '900',
    color: '#64748B',
    letterSpacing: 1.5,
    marginBottom: 12,
  },
  fullscreenEventItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 6,
    gap: 10,
  },
  fullscreenEventDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  fullscreenEventLabel: {
    flex: 1,
    fontSize: 13,
    fontWeight: '700',
    color: '#E2E8F0',
  },
  fullscreenEventTime: {
    fontSize: 12,
    fontWeight: '600',
    color: '#64748B',
    fontVariant: ['tabular-nums'],
  },
  fullscreenEventMore: {
    fontSize: 11,
    color: '#3B82F6',
    marginTop: 8,
    fontWeight: '600',
  },
});

export default RouteReplayMap;
