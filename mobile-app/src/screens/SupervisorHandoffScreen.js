import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, Alert } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';
import { SafeAreaView } from 'react-native-safe-area-context';

const SupervisorHandoffScreen = ({ route, navigation }) => {
  const { rideParams } = route.params;
  const [agreed, setAgreed] = useState(false);

  const handleStart = () => {
    if (!agreed) {
      Alert.alert("Agreement Required", "The supervisor must agree to the safety terms before starting.");
      return;
    }
    
    // Proceed to the active ride screen with the original parameters
    navigation.replace('DiagnosticRideActive', rideParams);
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <View style={styles.iconContainer}>
          <Ionicons name="hand-right-outline" size={80} color="#3B82F6" />
        </View>
        
        <Text style={styles.title}>Supervisor Hand-off</Text>
        <Text style={styles.subtitle}>SAFETY MANDATE</Text>
        
        <View style={styles.card}>
          <Text style={styles.instruction}>
            Please hand this device to the <Text style={styles.bold}>Supervisor</Text> or <Text style={styles.bold}>Instructor</Text> in the passenger seat.
          </Text>
          
          <View style={styles.warningBox}>
            <Ionicons name="warning" size={24} color="#F59E0B" />
            <Text style={styles.warningText}>
              The student driver must NOT operate this app while the vehicle is in motion.
            </Text>
          </View>
        </View>

        <View style={styles.disclaimerBox}>
          <Text style={styles.disclaimerTitle}>Terms of Supervision:</Text>
          <Text style={styles.disclaimerText}>
            1. I am the designated supervisor/instructor for this driving session.{"\n"}
            2. I accept full responsibility for monitoring the app and providing feedback.{"\n"}
            3. I will ensure the student driver remains focused solely on the road.{"\n"}
            4. I understand that Road Ready is a technology marketplace, and all lessons are provided by independent instructors/supervisors.{"\n"}
            5. I accept full responsibility for on-road safety and insurance compliance.
          </Text>
          
          <TouchableOpacity 
            style={styles.checkboxRow} 
            onPress={() => setAgreed(!agreed)}
          >
            <Ionicons 
              name={agreed ? "checkbox" : "square-outline"} 
              size={28} 
              color={agreed ? "#15803D" : "#64748B"} 
            />
            <Text style={styles.checkboxLabel}>I am the Supervisor and I agree.</Text>
          </TouchableOpacity>
        </View>

        <TouchableOpacity 
          style={[styles.btn, !agreed && styles.btnDisabled]} 
          onPress={handleStart}
          disabled={!agreed}
        >
          <Text style={styles.btnText}>Start Diagnostic Session</Text>
        </TouchableOpacity>
        
        <TouchableOpacity 
          style={styles.cancelBtn} 
          onPress={() => navigation.goBack()}
        >
          <Text style={styles.cancelText}>Cancel</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0B1326' },
  scroll: { padding: 25, alignItems: 'center' },
  iconContainer: { marginBottom: 20 },
  title: { fontSize: 28, fontWeight: 'bold', color: '#FFFFFF' },
  subtitle: { fontSize: 14, fontWeight: 'bold', color: '#EF4444', letterSpacing: 2, marginBottom: 30 },
  
  card: { width: '100%', backgroundColor: '#131B2E', padding: 20, borderRadius: 15, marginBottom: 25, borderWidth: 1, borderColor: '#1E293B' },
  instruction: { fontSize: 18, textAlign: 'center', lineHeight: 26, color: '#E2E8F0' },
  bold: { fontWeight: 'bold', color: '#3B82F6' },
  
  warningBox: { flexDirection: 'row', alignItems: 'center', backgroundColor: 'rgba(245,158,11,0.15)', padding: 15, borderRadius: 10, marginTop: 20 },
  warningText: { flex: 1, marginLeft: 10, color: '#F59E0B', fontSize: 14, fontWeight: '600' },
  
  disclaimerBox: { width: '100%', marginBottom: 30 },
  disclaimerTitle: { fontSize: 16, fontWeight: 'bold', marginBottom: 10, color: '#FFFFFF' },
  disclaimerText: { fontSize: 14, color: '#94A3B8', lineHeight: 22, marginBottom: 20 },
  
  checkboxRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 10 },
  checkboxLabel: { fontSize: 16, marginLeft: 10, fontWeight: '600', color: '#FFFFFF' },
  
  btn: { width: '100%', backgroundColor: '#15803D', padding: 18, borderRadius: 12, alignItems: 'center' },
  btnDisabled: { backgroundColor: '#475569' },
  btnText: { color: 'white', fontSize: 18, fontWeight: 'bold' },
  
  cancelBtn: { marginTop: 20 },
  cancelText: { color: '#94A3B8', fontSize: 16 }
});

export default SupervisorHandoffScreen;
