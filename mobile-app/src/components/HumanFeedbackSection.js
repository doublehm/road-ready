import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';
import { FAULT_CATEGORIES } from '../data/faults';

/**
 * HumanFeedbackSection - Displays human-flagged and device-detected feedback
 * grouped by category with count badges.
 *
 * Reuses the category card pattern from SessionDetailScreen.
 *
 * @param {Array} humanFeedback - Array of {code, label, category, count, timestamps}
 */
const HumanFeedbackSection = ({ humanFeedback = [] }) => {
  if (!humanFeedback || humanFeedback.length === 0) return null;

  // Group by category
  const grouped = {};
  humanFeedback.forEach(item => {
    const catId = item.category || (item.code ? item.code[0] : '?');
    if (!grouped[catId]) grouped[catId] = [];
    grouped[catId].push(item);
  });

  const totalFlags = humanFeedback.reduce((sum, item) => sum + (item.count || 0), 0);

  // Separate device-detected (F) from human-flagged
  const deviceItems = grouped['F'] || [];
  const humanItems = Object.entries(grouped).filter(([catId]) => catId !== 'F');
  const hasDevice = deviceItems.length > 0;
  const hasHuman = humanItems.length > 0;

  const renderCategory = (catId, items) => {
    const catDef = FAULT_CATEGORIES.find(c => c.id === catId);
    const catTitle = catDef?.title || `Category ${catId}`;
    const catColor = catDef?.color || '#1E293B';

    return (
      <View key={catId} style={styles.categoryCard}>
        <View style={[styles.catHeader, { backgroundColor: catColor }]}>
          <Text style={styles.catTitle}>{catTitle}</Text>
          <Text style={styles.catCount}>
            {items.reduce((s, i) => s + (i.count || 0), 0)}
          </Text>
        </View>
        <View style={styles.catBody}>
          {items.map(item => (
            <View key={item.code} style={styles.faultWrapper}>
              <View style={item.timestamps?.length > 0 ? styles.faultRowWithTime : styles.faultRow}>
                <View style={styles.badge}>
                  <Text style={styles.badgeText}>{item.count}</Text>
                </View>
                <View style={styles.faultInfo}>
                  <Text style={styles.faultLabel}>{item.label}</Text>
                  <Text style={styles.faultCode}>{item.code}</Text>
                </View>
              </View>
              {item.timestamps?.length > 0 && (
                <View style={styles.timeRow}>
                  {item.timestamps.map((t, idx) => {
                    const mins = Math.floor(t.elapsed / 60);
                    const secs = Math.floor(t.elapsed % 60);
                    return (
                      <View key={idx} style={styles.timeTag}>
                        <Text style={styles.timeTagText}>
                          {mins}:{secs < 10 ? '0' : ''}{secs}
                        </Text>
                      </View>
                    );
                  })}
                </View>
              )}
            </View>
          ))}
        </View>
      </View>
    );
  };

  return (
    <View style={styles.container}>
      <View style={styles.headerRow}>
        <Ionicons name="flag" size={18} color="#F59E0B" />
        <Text style={styles.sectionTitle}>Supervisor Feedback</Text>
        <View style={styles.totalBadge}>
          <Text style={styles.totalBadgeText}>{totalFlags} flags</Text>
        </View>
      </View>

      {/* Source indicators */}
      <View style={styles.sourceRow}>
        {hasHuman && (
          <View style={styles.sourceTag}>
            <Ionicons name="person" size={12} color="#3B82F6" />
            <Text style={styles.sourceText}>Human Observed</Text>
          </View>
        )}
        {hasDevice && (
          <View style={styles.sourceTag}>
            <Ionicons name="phone-portrait" size={12} color="#94A3B8" />
            <Text style={styles.sourceText}>Device Detected</Text>
          </View>
        )}
      </View>

      {/* Human-observed categories first */}
      {humanItems.map(([catId, items]) => renderCategory(catId, items))}

      {/* Device-detected last */}
      {hasDevice && renderCategory('F', deviceItems)}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    marginBottom: 8,
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 8,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#FFFFFF',
    flex: 1,
  },
  totalBadge: {
    backgroundColor: '#F59E0B',
    borderRadius: 12,
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  totalBadgeText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: 'bold',
  },
  sourceRow: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 12,
  },
  sourceTag: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    backgroundColor: '#1E293B',
    borderRadius: 12,
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  sourceText: {
    fontSize: 11,
    color: '#94A3B8',
  },
  categoryCard: {
    backgroundColor: '#131B2E',
    borderRadius: 10,
    marginBottom: 10,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: '#1E293B',
  },
  catHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 10,
    paddingHorizontal: 15,
  },
  catTitle: {
    fontWeight: 'bold',
    fontSize: 15,
    color: '#FFFFFF',
  },
  catCount: {
    fontWeight: 'bold',
    fontSize: 14,
    color: '#94A3B8',
  },
  catBody: {
    padding: 12,
  },
  faultWrapper: {
    marginBottom: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#1E293B',
    paddingBottom: 10,
  },
  faultRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  faultRowWithTime: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 6,
  },
  timeRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
    marginLeft: 38,
  },
  timeTag: {
    backgroundColor: '#1E293B',
    borderRadius: 4,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderWidth: 1,
    borderColor: '#1E293B',
  },
  timeTagText: {
    fontSize: 10,
    color: '#CBD5E1',
    fontWeight: '600',
  },
  badge: {
    backgroundColor: '#EF4444',
    width: 28,
    height: 28,
    borderRadius: 14,
    justifyContent: 'center',
    alignItems: 'center',
  },
  badgeText: {
    color: '#fff',
    fontWeight: 'bold',
    fontSize: 13,
  },
  faultInfo: {
    flex: 1,
    marginLeft: 10,
  },
  faultLabel: {
    fontSize: 15,
    color: '#FFFFFF',
    fontWeight: '500',
  },
  faultCode: {
    fontSize: 12,
    color: '#64748B',
  },
});

export default HumanFeedbackSection;
