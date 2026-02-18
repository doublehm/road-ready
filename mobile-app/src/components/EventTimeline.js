import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import Ionicons from 'react-native-vector-icons/Ionicons';

const EVENT_CONFIG = {
  speeding: {
    icon: 'speedometer',
    label: 'Speeding',
    color: '#dc3545',
  },
  harsh_braking: {
    icon: 'hand-left',
    label: 'Harsh Braking',
    color: '#e17055',
  },
  sharp_turn: {
    icon: 'refresh',
    label: 'Sharp Turn',
    color: '#fdcb6e',
  },
  sudden_stop: {
    icon: 'stop-circle',
    label: 'Sudden Stop',
    color: '#d63031',
  },
};

const SEVERITY_COLORS = {
  high: '#dc3545',
  medium: '#ffc107',
  low: '#28a745',
};

/**
 * EventTimeline - Displays a vertical timeline of driving events.
 *
 * @param {Array} events - Array of {type, timestamp, severity, value, description, lat, lng, limit, zone_type}
 * @param {number} startTime - Ride start timestamp (for calculating elapsed time)
 * @param {number} maxEvents - Maximum events to display (default 50)
 */
const EventTimeline = ({ events = [], startTime = 0, maxEvents = 50 }) => {
  if (events.length === 0) {
    return (
      <View style={styles.emptyContainer}>
        <Ionicons name="checkmark-circle" size={32} color="#28a745" />
        <Text style={styles.emptyText}>No driving events detected!</Text>
        <Text style={styles.emptySubtext}>Great job maintaining safe driving habits.</Text>
      </View>
    );
  }

  const displayEvents = events.slice(0, maxEvents);

  const formatElapsedTime = (timestamp) => {
    if (!startTime || !timestamp) return '';
    const elapsed = Math.round((timestamp - startTime) / 1000);
    const mins = Math.floor(elapsed / 60);
    const secs = elapsed % 60;
    return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
  };

  const getEventDetail = (event) => {
    if (event.description) return event.description;

    switch (event.type) {
      case 'speeding':
        const zoneLabel = event.zone_type === 'school' ? ' (SCHOOL ZONE)' : '';
        return `${Math.round(event.value || 0)} km/h in a ${event.limit || '?'} km/h zone${zoneLabel}`;
      case 'harsh_braking':
        return `Braking force: ${event.value || '?'}g`;
      case 'sharp_turn':
        return `Lateral force: ${event.value || '?'}g`;
      case 'sudden_stop':
        return `Speed drop: ${event.value || '?'} km/h`;
      default:
        return event.type.replace(/_/g, ' ');
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.header}>
        {events.length} Event{events.length !== 1 ? 's' : ''} Detected
      </Text>

      {displayEvents.map((event, index) => {
        const config = EVENT_CONFIG[event.type] || {
          icon: 'alert-circle',
          label: event.type.replace(/_/g, ' '),
          color: '#6c757d',
        };
        const sevColor = SEVERITY_COLORS[event.severity] || SEVERITY_COLORS.medium;
        const isLast = index === displayEvents.length - 1;

        return (
          <View key={index} style={styles.timelineItem}>
            {/* Timeline connector */}
            <View style={styles.timelineLeft}>
              <View style={[styles.dot, { backgroundColor: sevColor }]}>
                <Ionicons name={config.icon} size={12} color="white" />
              </View>
              {!isLast && <View style={styles.connector} />}
            </View>

            {/* Event content */}
            <View style={[styles.timelineContent, isLast && styles.timelineContentLast]}>
              <View style={styles.eventHeader}>
                <Text style={[styles.eventType, { color: config.color }]}>
                  {config.label}
                </Text>
                {event.severity === 'high' && (
                  <View style={styles.highBadge}>
                    <Text style={styles.highBadgeText}>HIGH</Text>
                  </View>
                )}
                {event.zone_type === 'school' && (
                  <View style={styles.schoolBadge}>
                    <Text style={styles.schoolBadgeText}>SCHOOL ZONE</Text>
                  </View>
                )}
              </View>

              <Text style={styles.eventDetail}>{getEventDetail(event)}</Text>

              {event.timestamp && startTime > 0 && (
                <Text style={styles.eventTime}>
                  at {formatElapsedTime(event.timestamp)} into ride
                </Text>
              )}
            </View>
          </View>
        );
      })}

      {events.length > maxEvents && (
        <Text style={styles.moreText}>
          + {events.length - maxEvents} more events
        </Text>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
  },
  header: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#1a1a1a',
    marginBottom: 16,
  },
  emptyContainer: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 24,
    alignItems: 'center',
  },
  emptyText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#28a745',
    marginTop: 8,
  },
  emptySubtext: {
    fontSize: 13,
    color: '#6c757d',
    marginTop: 4,
  },
  timelineItem: {
    flexDirection: 'row',
  },
  timelineLeft: {
    width: 32,
    alignItems: 'center',
  },
  dot: {
    width: 24,
    height: 24,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  connector: {
    width: 2,
    flex: 1,
    backgroundColor: '#e9ecef',
    marginVertical: 2,
  },
  timelineContent: {
    flex: 1,
    paddingLeft: 12,
    paddingBottom: 16,
  },
  timelineContentLast: {
    paddingBottom: 0,
  },
  eventHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 4,
  },
  eventType: {
    fontSize: 14,
    fontWeight: '600',
  },
  highBadge: {
    backgroundColor: '#dc3545',
    borderRadius: 4,
    paddingHorizontal: 6,
    paddingVertical: 1,
  },
  highBadgeText: {
    color: 'white',
    fontSize: 9,
    fontWeight: 'bold',
  },
  schoolBadge: {
    backgroundColor: '#ff9800',
    borderRadius: 4,
    paddingHorizontal: 6,
    paddingVertical: 1,
  },
  schoolBadgeText: {
    color: 'white',
    fontSize: 9,
    fontWeight: 'bold',
  },
  eventDetail: {
    fontSize: 13,
    color: '#495057',
    lineHeight: 18,
  },
  eventTime: {
    fontSize: 11,
    color: '#adb5bd',
    marginTop: 2,
  },
  moreText: {
    textAlign: 'center',
    color: '#6c757d',
    fontSize: 13,
    marginTop: 8,
  },
});

export default EventTimeline;
