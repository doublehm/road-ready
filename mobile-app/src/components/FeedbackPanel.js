import React, { useState, useMemo, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Modal,
  TextInput,
  FlatList,
  TouchableOpacity,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';
import { ALL_CRITERIA } from '../data/faults';

/**
 * FeedbackPanel - Bottom-sheet modal for quickly flagging driving criteria.
 *
 * Features:
 * - Search bar with autocomplete (filters on code, label, category name)
 * - One-tap to increment a criterion's count
 * - Recently used items shown first when not searching
 * - Flagged section at top with +/- counter controls
 *
 * @param {boolean} visible - Whether the panel is shown
 * @param {Function} onClose - Close handler
 * @param {Object} feedbackCounts - Ref value: {code: {code, label, category, count, timestamps}}
 * @param {Function} onUpdateCount - (code, delta, metadata) => void
 * @param {number} elapsedSeconds - Current ride elapsed seconds
 */
const FeedbackPanel = ({ visible, onClose, feedbackCounts, onUpdateCount, elapsedSeconds }) => {
  const [searchText, setSearchText] = useState('');
  const [recentCodes, setRecentCodes] = useState([]);

  const filteredCriteria = useMemo(() => {
    if (!searchText.trim()) {
      // Show recent items first, then all
      const recentItems = recentCodes
        .map(code => ALL_CRITERIA.find(c => c.code === code))
        .filter(Boolean);
      const recentSet = new Set(recentCodes);
      const rest = ALL_CRITERIA.filter(c => !recentSet.has(c.code));
      return [...recentItems, ...rest];
    }
    const query = searchText.toLowerCase();
    return ALL_CRITERIA.filter(item =>
      item.code.toLowerCase().includes(query) ||
      item.label.toLowerCase().includes(query) ||
      item.categoryTitle.toLowerCase().includes(query)
    );
  }, [searchText, recentCodes]);

  const handleTap = useCallback((item) => {
    onUpdateCount(item.code, 1, {
      code: item.code,
      label: item.label,
      category: item.categoryId || item.category,
      timestamp: Date.now(),
      elapsed_seconds: elapsedSeconds,
    });
    setRecentCodes(prev => {
      const filtered = prev.filter(c => c !== item.code);
      return [item.code, ...filtered].slice(0, 8);
    });
  }, [onUpdateCount, elapsedSeconds]);

  const handleDecrement = useCallback((code) => {
    onUpdateCount(code, -1);
  }, [onUpdateCount]);

  const totalFlags = Object.values(feedbackCounts).reduce(
    (sum, entry) => sum + (entry.count || 0), 0
  );

  const flaggedItems = Object.entries(feedbackCounts)
    .filter(([_, entry]) => entry.count > 0)
    .map(([code, entry]) => ({ code, ...entry }));

  const renderCriterionRow = ({ item }) => {
    const count = feedbackCounts[item.code]?.count || 0;
    return (
      <TouchableOpacity
        style={styles.criterionRow}
        onPress={() => handleTap(item)}
        activeOpacity={0.6}
      >
        <View style={[styles.categoryDot, { backgroundColor: item.categoryColor }]} />
        <View style={styles.criterionInfo}>
          <Text style={styles.criterionLabel}>{item.label}</Text>
          <Text style={styles.criterionCode}>{item.code} - {item.categoryTitle}</Text>
        </View>
        {count > 0 && (
          <View style={styles.countBadge}>
            <Text style={styles.countBadgeText}>{count}</Text>
          </View>
        )}
        <Ionicons name="add-circle" size={28} color="#28a745" />
      </TouchableOpacity>
    );
  };

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.modalOverlay}
      >
        <TouchableOpacity style={styles.backdrop} activeOpacity={1} onPress={onClose} />
        <View style={styles.panelContainer}>
          {/* Header */}
          <View style={styles.panelHeader}>
            <View style={styles.panelTitleRow}>
              <Ionicons name="flag" size={20} color="#e17055" />
              <Text style={styles.panelTitle}>Flag Criteria</Text>
              {totalFlags > 0 && (
                <View style={styles.totalBadge}>
                  <Text style={styles.totalBadgeText}>{totalFlags}</Text>
                </View>
              )}
            </View>
            <TouchableOpacity onPress={onClose}>
              <Ionicons name="close-circle" size={28} color="#6c757d" />
            </TouchableOpacity>
          </View>

          {/* Search */}
          <View style={styles.searchContainer}>
            <Ionicons name="search" size={20} color="#adb5bd" />
            <TextInput
              style={styles.searchInput}
              placeholder="Search code or name (e.g. A1, Mirror, Speed)..."
              placeholderTextColor="#adb5bd"
              value={searchText}
              onChangeText={setSearchText}
              autoCorrect={false}
              returnKeyType="search"
            />
            {searchText.length > 0 && (
              <TouchableOpacity onPress={() => setSearchText('')}>
                <Ionicons name="close" size={20} color="#adb5bd" />
              </TouchableOpacity>
            )}
          </View>

          {/* Flagged items summary */}
          {flaggedItems.length > 0 && !searchText && (
            <View style={styles.flaggedSection}>
              <Text style={styles.flaggedTitle}>Flagged ({totalFlags})</Text>
              {flaggedItems.map(item => (
                <View key={item.code} style={styles.flaggedRow}>
                  <View style={styles.flaggedInfo}>
                    <Text style={styles.flaggedCode}>{item.code}</Text>
                    <Text style={styles.flaggedLabel} numberOfLines={1}>{item.label}</Text>
                  </View>
                  <View style={styles.flaggedCounter}>
                    <TouchableOpacity onPress={() => handleDecrement(item.code)}>
                      <Ionicons name="remove-circle-outline" size={24} color="#dc3545" />
                    </TouchableOpacity>
                    <Text style={styles.flaggedCount}>{item.count}</Text>
                    <TouchableOpacity onPress={() => handleTap({
                      code: item.code,
                      label: item.label,
                      categoryId: item.category,
                      categoryColor: '#ccc',
                    })}>
                      <Ionicons name="add-circle" size={24} color="#28a745" />
                    </TouchableOpacity>
                  </View>
                </View>
              ))}
            </View>
          )}

          {/* Criteria list */}
          <FlatList
            data={filteredCriteria}
            keyExtractor={item => item.code}
            renderItem={renderCriterionRow}
            keyboardShouldPersistTaps="handled"
            style={styles.criteriaList}
            ListEmptyComponent={
              <View style={styles.emptyState}>
                <Text style={styles.emptyText}>No matching criteria</Text>
              </View>
            }
          />
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
};

const styles = StyleSheet.create({
  modalOverlay: {
    flex: 1,
  },
  backdrop: {
    flex: 0.15,
    backgroundColor: 'rgba(0,0,0,0.4)',
  },
  panelContainer: {
    flex: 0.85,
    backgroundColor: '#fff',
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -4 },
    shadowOpacity: 0.15,
    shadowRadius: 12,
    elevation: 20,
  },
  panelHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#f0f0f0',
  },
  panelTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  panelTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#1a1a1a',
  },
  totalBadge: {
    backgroundColor: '#e17055',
    borderRadius: 10,
    minWidth: 22,
    height: 22,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 6,
  },
  totalBadgeText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: 'bold',
  },
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#f8f9fa',
    marginHorizontal: 16,
    marginVertical: 12,
    paddingHorizontal: 12,
    borderRadius: 10,
    height: 44,
    borderWidth: 1,
    borderColor: '#dee2e6',
  },
  searchInput: {
    flex: 1,
    fontSize: 15,
    color: '#1a1a1a',
    marginLeft: 8,
  },
  flaggedSection: {
    marginHorizontal: 16,
    marginBottom: 8,
    backgroundColor: '#fff9e6',
    borderRadius: 10,
    padding: 12,
    borderWidth: 1,
    borderColor: '#ffc107',
  },
  flaggedTitle: {
    fontSize: 13,
    fontWeight: 'bold',
    color: '#856404',
    marginBottom: 8,
    textTransform: 'uppercase',
  },
  flaggedRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 6,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(0,0,0,0.05)',
  },
  flaggedInfo: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  flaggedCode: {
    fontWeight: 'bold',
    color: '#856404',
    fontSize: 13,
    width: 30,
  },
  flaggedLabel: {
    flex: 1,
    fontSize: 14,
    color: '#856404',
  },
  flaggedCounter: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  flaggedCount: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#1a1a1a',
    minWidth: 24,
    textAlign: 'center',
  },
  criteriaList: {
    flex: 1,
  },
  criterionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#f0f0f0',
    gap: 10,
  },
  categoryDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  criterionInfo: {
    flex: 1,
  },
  criterionLabel: {
    fontSize: 15,
    color: '#1a1a1a',
    fontWeight: '500',
  },
  criterionCode: {
    fontSize: 12,
    color: '#6c757d',
    marginTop: 1,
  },
  countBadge: {
    backgroundColor: '#dc3545',
    borderRadius: 10,
    minWidth: 22,
    height: 22,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 6,
    marginRight: 4,
  },
  countBadgeText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: 'bold',
  },
  emptyState: {
    padding: 40,
    alignItems: 'center',
  },
  emptyText: {
    color: '#6c757d',
    fontSize: 15,
  },
});

export default FeedbackPanel;
