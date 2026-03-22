import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';

const EVENT_CONFIG = {
  speeding: {
    icon: 'speedometer',
    label: 'Speeding',
    color: '#EF4444',
  },
  harsh_braking: {
    icon: 'hand-left',
    label: 'Harsh Braking',
    color: '#F59E0B',
  },
  sharp_turn: {
    icon: 'refresh',
    label: 'Sharp Turn',
    color: '#F59E0B',
  },
  sudden_stop: {
    icon: 'stop-circle',
    label: 'Sudden Stop',
    color: '#EF4444',
  },
  human_flag: {
    icon: 'flag',
    label: 'Supervisor Flag',
    color: '#F59E0B',
  },
  coach_note: {
    icon: 'chatbox-ellipses',
    label: 'Coach Note',
    color: '#3B82F6',
  },
};

const SEVERITY_COLORS = {
  high: '#EF4444',
  medium: '#F59E0B',
  low: '#15803D',
  human: '#F59E0B',
  note: '#3B82F6',
};

/**
 * EventTimeline - Displays a vertical timeline of driving events.
 *
 * @param {Array} events - Array of {type, timestamp, severity, value, description, lat, lng, limit, zone_type}
 * @param {number} startTime - Ride start timestamp (for calculating elapsed time)
 * @param {Array} humanFeedback - Optional aggregated human feedback for the header summary
 * @param {number} maxEvents - Maximum events to display (default 50)
 */
const EventTimeline = ({ events = [], startTime = 0, humanFeedback = [], maxEvents = 50 }) => {
  const totalFlags = humanFeedback?.reduce((sum, item) => sum + (item.count || 0), 0) || 0;

  if (events.length === 0 && totalFlags === 0) {
    return (
      <View style={styles.emptyContainer}>
        <Ionicons name="checkmark-circle" size={32} color="#15803D" />
        <Text style={styles.emptyText}>No driving events detected!</Text>
        <Text style={styles.emptySubtext}>Great job maintaining safe driving habits.</Text>
      </View>
    );
  }

  const displayEvents = [...events]
    .sort((a, b) => (a.timestamp || 0) - (b.timestamp || 0))
    .slice(0, maxEvents);

  const formatElapsedTime = (timestamp) => {
    if (!startTime || !timestamp) return '';
    const elapsed = Math.round((timestamp - startTime) / 1000);
    if (elapsed < 0) return '0:00';
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
      case 'human_flag':
        return event.label || 'Criterion flagged';
      case 'coach_note':
        return event.text || 'Observation recorded';
      default:
        return event.type.replace(/_/g, ' ');
    }
  };

  return (
    <View style={styles.container}>
      {/* Aggregated Feedback Header */}
      {totalFlags > 0 && (
        <View style={styles.aggregateHeader}>
          <View style={styles.aggregateTitleRow}>
            <Ionicons name="flag" size={16} color="#F59E0B" />
            <Text style={styles.aggregateTitle}>Supervisor Summary</Text>
            <View style={styles.totalFlagsBadge}>
              <Text style={styles.totalFlagsText}>{totalFlags} total flags</Text>
            </View>
          </View>
          <View style={styles.aggregateGrid}>
            {humanFeedback.map((item, i) => (
              <View key={i} style={styles.aggregateItem}>
                <Text style={styles.aggregateCount}>{item.count}x</Text>
                <Text style={styles.aggregateLabel} numberOfLines={1}>{item.label}</Text>
              </View>
            ))}
          </View>
        </View>
      )}

      <Text style={styles.header}>
        Chronological Timeline
      </Text>

      {displayEvents.map((event, index) => {
        const config = EVENT_CONFIG[event.type] || {
          icon: 'alert-circle',
          label: event.type.replace(/_/g, ' '),
          color: '#94A3B8',
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
                {event.type === 'human_flag' && (
                  <View style={styles.manualBadge}>
                    <Text style={styles.manualBadgeText}>MANUAL FLAG</Text>
                  </View>
                )}
                {event.type === 'coach_note' && (
                  <View style={styles.noteBadge}>
                    <Text style={styles.noteBadgeText}>COACH NOTE</Text>
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
    backgroundColor: '#131B2E',
    borderRadius: 12,
    padding: 16,
  },
  header: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#94A3B8',
    marginBottom: 16,
    textTransform: 'uppercase',
  },
  aggregateHeader: {
    backgroundColor: 'rgba(245,158,11,0.1)',
    borderRadius: 8,
    padding: 12,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#1E293B',
  },
  aggregateTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 12,
  },
  aggregateTitle: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#F59E0B',
    flex: 1,
  },
  totalFlagsBadge: {
    backgroundColor: '#F59E0B',
    borderRadius: 10,
    paddingHorizontal: 8,
    paddingVertical: 2,
  },
  totalFlagsText: {
    color: 'white',
    fontSize: 10,
    fontWeight: 'bold',
  },
  aggregateGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  aggregateItem: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(30,41,59,0.5)',
    borderRadius: 6,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderWidth: 1,
    borderColor: '#1E293B',
  },
  aggregateCount: {
    fontSize: 12,
    fontWeight: 'bold',
    color: '#F59E0B',
    marginRight: 6,
  },
  aggregateLabel: {
    fontSize: 11,
    color: '#CBD5E1',
    maxWidth: 100,
  },
  emptyContainer: {
    backgroundColor: '#131B2E',
    borderRadius: 12,
    padding: 24,
    alignItems: 'center',
  },
  emptyText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#15803D',
    marginTop: 8,
  },
  emptySubtext: {
    fontSize: 13,
    color: '#94A3B8',
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
    backgroundColor: '#1E293B',
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
    backgroundColor: '#EF4444',
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
    backgroundColor: '#F59E0B',
    borderRadius: 4,
    paddingHorizontal: 6,
    paddingVertical: 1,
  },
  schoolBadgeText: {
    color: 'white',
    fontSize: 9,
    fontWeight: 'bold',
  },
  manualBadge: {
    backgroundColor: '#F59E0B',
    borderRadius: 4,
    paddingHorizontal: 6,
    paddingVertical: 1,
  },
  manualBadgeText: {
    color: 'white',
    fontSize: 9,
    fontWeight: 'bold',
  },
  noteBadge: {
    backgroundColor: '#3B82F6',
    borderRadius: 4,
    paddingHorizontal: 6,
    paddingVertical: 1,
  },
  noteBadgeText: {
    color: 'white',
    fontSize: 9,
    fontWeight: 'bold',
  },
  eventDetail: {
    fontSize: 13,
    color: '#CBD5E1',
    lineHeight: 18,
  },
  eventTime: {
    fontSize: 11,
    color: '#64748B',
    marginTop: 2,
  },
  moreText: {
    textAlign: 'center',
    color: '#94A3B8',
    fontSize: 13,
    marginTop: 8,
  },
});

export default EventTimeline;
