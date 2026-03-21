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
      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        <View style={styles.headerContainer}>
          <Text style={styles.header}>Knowledge Base</Text>
          <Text style={styles.subtitle}>TACTICAL STUDY GUIDES</Text>
        </View>

        {provinces.map((p) => (
          <TouchableOpacity 
            key={p.id} 
            style={[styles.card, p.disabled && styles.disabledCard]}
            onPress={() => !p.disabled && navigation.navigate('EducationBook', { province: p.id })}
            disabled={p.disabled}
          >
            <View style={styles.iconContainer}>
              <Text style={styles.flag}>{p.flag}</Text>
            </View>
            <View style={styles.info}>
              <Text style={styles.name}>{p.name}</Text>
              <Text style={styles.desc}>{p.description}</Text>
            </View>
            {!p.disabled && (
              <View style={styles.arrowContainer}>
                <Ionicons name="chevron-forward" size={20} color="#15803D" />
              </View>
            )}
          </TouchableOpacity>
        ))}
        
        <View style={styles.footer}>
          <Ionicons name="information-circle-outline" size={20} color="#94A3B8" />
          <Text style={styles.footerText}>More regions being mapped soon.</Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F6FAFE' },
  scroll: { padding: 24 },
  headerContainer: { marginBottom: 32, marginTop: 10 },
  header: { fontSize: 32, fontWeight: '800', color: '#1E293B', letterSpacing: -1 },
  subtitle: { fontSize: 12, fontWeight: '800', color: '#15803D', letterSpacing: 2, marginTop: 4 },
  card: {
    backgroundColor: 'white',
    borderRadius: 24,
    padding: 24,
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 16,
    shadowColor: '#1E293B',
    shadowOpacity: 0.04,
    shadowRadius: 15,
    elevation: 2,
  },
  disabledCard: { opacity: 0.5, backgroundColor: '#F1F5F9' },
  iconContainer: { 
    width: 64, 
    height: 64, 
    borderRadius: 20, 
    backgroundColor: '#F8FAFC', 
    justifyContent: 'center', 
    alignItems: 'center',
    marginRight: 20 
  },
  flag: { fontSize: 32 },
  info: { flex: 1 },
  name: { fontSize: 18, fontWeight: '800', color: '#1E293B', letterSpacing: -0.5 },
  desc: { color: '#64748B', marginTop: 4, fontWeight: '500', fontSize: 14 },
  arrowContainer: { 
    width: 36, 
    height: 36, 
    borderRadius: 12, 
    backgroundColor: '#DCFCE7', 
    justifyContent: 'center', 
    alignItems: 'center' 
  },
  footer: { 
    flexDirection: 'row', 
    alignItems: 'center', 
    justifyContent: 'center', 
    marginTop: 40,
    gap: 8 
  },
  footerText: { color: '#94A3B8', fontWeight: '600', fontSize: 13 }
});

export default EducationHomeScreen;
