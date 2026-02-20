import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

const EducationHomeScreen = ({ navigation }) => {
  const provinces = [
    { id: 'bc', name: 'British Columbia', flag: '🇨🇦', description: 'Based on Learn to Drive Smart.' },
    { id: 'on', name: 'Ontario', flag: '🍁', description: 'Coming Soon', disabled: true },
  ];

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text style={styles.header}>Study Guides 📚</Text>
        <Text style={styles.subtitle}>Choose your province to start reading.</Text>

        {provinces.map((p) => (
          <TouchableOpacity 
            key={p.id} 
            style={[styles.card, p.disabled && styles.disabledCard]}
            onPress={() => !p.disabled && navigation.navigate('EducationBook', { province: p.id })}
            disabled={p.disabled}
          >
            <Text style={styles.flag}>{p.flag}</Text>
            <View style={styles.info}>
              <Text style={styles.name}>{p.name}</Text>
              <Text style={styles.desc}>{p.description}</Text>
            </View>
            {!p.disabled && <Text style={styles.arrow}>→</Text>}
          </TouchableOpacity>
        ))}
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f5f5f5' },
  scroll: { padding: 20 },
  header: { fontSize: 28, fontWeight: 'bold', marginBottom: 10 },
  subtitle: { fontSize: 16, color: '#666', marginBottom: 30 },
  card: {
    backgroundColor: 'white',
    borderRadius: 15,
    padding: 20,
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 15,
    elevation: 3,
    shadowColor: '#000',
    shadowOpacity: 0.1,
    shadowRadius: 5,
  },
  disabledCard: { opacity: 0.6 },
  flag: { fontSize: 40, marginRight: 20 },
  info: { flex: 1 },
  name: { fontSize: 18, fontWeight: 'bold', color: '#333' },
  desc: { color: '#888', marginTop: 4 },
  arrow: { fontSize: 24, color: '#007bff' }
});

export default EducationHomeScreen;
