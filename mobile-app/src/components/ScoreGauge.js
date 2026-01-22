import React from 'react';
import { View, Text, StyleSheet } from 'react-native';

/**
 * Circular score gauge component for displaying scores 0-100
 *
 * @param {number} score - Score value (0-100)
 * @param {number} size - Diameter of the gauge (default: 120)
 * @param {string} label - Label to display below score
 */
const ScoreGauge = ({ score, size = 120, label = '' }) => {
  // Determine color based on score
  const getColor = () => {
    if (score >= 80) return '#28a745'; // Green
    if (score >= 60) return '#ffc107'; // Yellow
    return '#dc3545'; // Red
  };

  const color = getColor();
  const borderWidth = size * 0.08;

  return (
    <View style={[styles.container, { width: size, height: size }]}>
      <View
        style={[
          styles.gauge,
          {
            width: size,
            height: size,
            borderRadius: size / 2,
            borderWidth: borderWidth,
            borderColor: color,
            backgroundColor: `${color}10`, // 10% opacity
          },
        ]}
      >
        <Text style={[styles.score, { fontSize: size * 0.3, color }]}>
          {Math.round(score)}
        </Text>
      </View>
      {label && (
        <Text style={[styles.label, { fontSize: size * 0.12 }]}>{label}</Text>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  gauge: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  score: {
    fontWeight: 'bold',
  },
  label: {
    marginTop: 8,
    color: '#6c757d',
    textAlign: 'center',
  },
});

export default ScoreGauge;
