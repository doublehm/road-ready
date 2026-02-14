import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, Alert } from 'react-native';
import Ionicons from 'react-native-vector-icons/Ionicons';
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
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <View style={styles.iconContainer}>
          <Ionicons name="hand-right-outline" size={80} color="#007bff" />
        </View>
        
        <Text style={styles.title}>Supervisor Hand-off</Text>
        <Text style={styles.subtitle}>SAFETY MANDATE</Text>
        
        <View style={styles.card}>
          <Text style={styles.instruction}>
            Please hand this device to the <Text style={styles.bold}>Supervisor</Text> or <Text style={styles.bold}>Instructor</Text> in the passenger seat.
          </Text>
          
          <View style={styles.warningBox}>
            <Ionicons name="warning" size={24} color="#856404" />
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
            4. I understand that real-time feedback is intended for my coaching use only.
          </Text>
          
          <TouchableOpacity 
            style={styles.checkboxRow} 
            onPress={() => setAgreed(!agreed)}
          >
            <Ionicons 
              name={agreed ? "checkbox" : "square-outline"} 
              size={28} 
              color={agreed ? "#28a745" : "#666"} 
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
  container: { flex: 1, backgroundColor: '#fff' },
  scroll: { padding: 25, alignItems: 'center' },
  iconContainer: { marginBottom: 20 },
  title: { fontSize: 28, fontWeight: 'bold', color: '#333' },
  subtitle: { fontSize: 14, fontWeight: 'bold', color: '#dc3545', letterSpacing: 2, marginBottom: 30 },
  
  card: { width: '100%', backgroundColor: '#f8f9fa', padding: 20, borderRadius: 15, marginBottom: 25, borderWidth: 1, borderColor: '#eee' },
  instruction: { fontSize: 18, textAlign: 'center', lineHeight: 26, color: '#444' },
  bold: { fontWeight: 'bold', color: '#007bff' },
  
  warningBox: { flexDirection: 'row', alignItems: 'center', backgroundColor: '#fff3cd', padding: 15, borderRadius: 10, marginTop: 20 },
  warningText: { flex: 1, marginLeft: 10, color: '#856404', fontSize: 14, fontWeight: '600' },
  
  disclaimerBox: { width: '100%', marginBottom: 30 },
  disclaimerTitle: { fontSize: 16, fontWeight: 'bold', marginBottom: 10, color: '#333' },
  disclaimerText: { fontSize: 14, color: '#666', lineHeight: 22, marginBottom: 20 },
  
  checkboxRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 10 },
  checkboxLabel: { fontSize: 16, marginLeft: 10, fontWeight: '600', color: '#333' },
  
  btn: { width: '100%', backgroundColor: '#28a745', padding: 18, borderRadius: 12, alignItems: 'center', elevation: 3 },
  btnDisabled: { backgroundColor: '#ccc' },
  btnText: { color: 'white', fontSize: 18, fontWeight: 'bold' },
  
  cancelBtn: { marginTop: 20 },
  cancelText: { color: '#666', fontSize: 16 }
});

export default SupervisorHandoffScreen;
