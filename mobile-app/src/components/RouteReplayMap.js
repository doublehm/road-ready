import React, { useMemo } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import OSMMap from './OSMMap';
import Ionicons from 'react-native-vector-icons/Ionicons';

const SEGMENT_COLORS = {
  red: '#dc3545',
  yellow: '#ffc107',
  green: '#28a745',
};

const EVENT_ICONS = {
  speeding: { name: 'speedometer', color: '#dc3545' },
  harsh_braking: { name: 'hand-left', color: '#e17055' },
  sharp_turn: { name: 'refresh', color: '#fdcb6e' },
  sudden_stop: { name: 'stop-circle', color: '#d63031' },
};

/**
 * RouteReplayMap - Displays a color-coded route map with event markers.
 *
 * @param {Array} routeSegments - Array of {start, end, color, speed, limit, zone_type}
 * @param {Array} events - Array of {type, lat, lng, severity, description}
 * @param {Array} routeCoords - Fallback: simple array of {latitude, longitude}
 * @param {number} height - Map height (default 300)
 */
const RouteReplayMap = ({ routeSegments = [], events = [], routeCoords = [], height = 300 }) => {
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

  // Filter events with valid coordinates (limit to avoid marker overload)
  const eventMarkers = useMemo(() => {
    return events
      .filter(e => e.lat && e.lng)
      .slice(0, 30); // Max 30 markers
  }, [events]);

  if (routeSegments.length === 0 && routeCoords.length === 0) {
    return (
      <View style={[styles.placeholder, { height }]}>
        <Ionicons name="map-outline" size={40} color="#ccc" />
        <Text style={styles.placeholderText}>No route data available</Text>
      </View>
    );
  }

  return (
    <View style={[styles.container, { height }]}>
      <OSMMap 
        style={styles.map} 
        region={region}
        markers={[
          ...eventMarkers.map(event => ({
            latitude: event.lat,
            longitude: event.lng,
            title: event.type.replace('_', ' ').toUpperCase(),
            description: event.description || ''
          })),
          ...(routeSegments.length > 0 && routeSegments[0].start?.lat ? [{
            latitude: routeSegments[0].start.lat,
            longitude: routeSegments[0].start.lng,
            title: "Start"
          }] : []),
          ...(routeSegments.length > 0 && routeSegments[routeSegments.length - 1].end?.lat ? [{
            latitude: routeSegments[routeSegments.length - 1].end.lat,
            longitude: routeSegments[routeSegments.length - 1].end.lng,
            title: "End"
          }] : [])
        ]}
        polylines={[
          ...polylines.map((line, i) => ({
            coordinates: line.coords,
            strokeWidth: 5,
            strokeColor: SEGMENT_COLORS[line.color] || SEGMENT_COLORS.green
          })),
          ...(polylines.length === 0 && routeCoords.length > 0 ? [{
            coordinates: routeCoords,
            strokeWidth: 4,
            strokeColor: "#007bff"
          }] : [])
        ]}
      />

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
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    borderRadius: 12,
    overflow: 'hidden',
    backgroundColor: '#f0f0f0',
  },
  map: {
    flex: 1,
  },
  placeholder: {
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f0f0f0',
    borderRadius: 12,
  },
  placeholderText: {
    color: '#999',
    marginTop: 8,
    fontSize: 14,
  },
  legend: {
    position: 'absolute',
    bottom: 8,
    left: 8,
    flexDirection: 'row',
    backgroundColor: 'rgba(255,255,255,0.9)',
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
    color: '#333',
  },
});

export default RouteReplayMap;
