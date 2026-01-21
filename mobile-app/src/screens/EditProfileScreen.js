import React, { useState, useContext } from 'react';
import { View, Text, StyleSheet, TextInput, TouchableOpacity, ScrollView, Alert, ActivityIndicator } from 'react-native';
import client from '../api/client';
import { AuthContext } from '../context/AuthContext';
import { SafeAreaView } from 'react-native-safe-area-context';

const EditProfileScreen = ({ navigation }) => {
  const { userInfo, fetchUser, userToken } = useContext(AuthContext);
  const profile = userInfo?.instructor_profile || {};

  const [city, setCity] = useState(profile.city || '');
  const [hourlyRate, setHourlyRate] = useState(profile.hourly_rate?.toString() || '');
  const [bio, setBio] = useState(profile.bio || '');
  const [carModel, setCarModel] = useState(profile.car_model || '');
  const [loading, setLoading] = useState(false);

  const handleSave = async () => {
    setLoading(true);
    try {
      await client.put('/users/me/instructor-profile', {
        city,
        hourly_rate: parseFloat(hourlyRate),
        bio,
        car_model: carModel,
        insurance_policy: profile.insurance_policy, // Keep existing
        certification_id: profile.certification_id // Keep existing
      });
      
      await fetchUser(userToken); // Refresh context
      Alert.alert("Success", "Profile updated!");
      navigation.goBack();
    } catch (e) {
      console.log(e);
      Alert.alert("Error", "Failed to update profile.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text style={styles.header}>Edit Profile</Text>

        <Text style={styles.label}>City</Text>
        <TextInput style={styles.input} value={city} onChangeText={setCity} />

        <Text style={styles.label}>Hourly Rate ($)</Text>
        <TextInput 
          style={styles.input} 
          value={hourlyRate} 
          onChangeText={setHourlyRate} 
          keyboardType="numeric" 
        />

        <Text style={styles.label}>Car Model</Text>
        <TextInput style={styles.input} value={carModel} onChangeText={setCarModel} />

        <Text style={styles.label}>Bio</Text>
        <TextInput 
          style={[styles.input, {height: 100}]} 
          value={bio} 
          onChangeText={setBio} 
          multiline 
          textAlignVertical="top"
        />

        <TouchableOpacity 
          style={styles.saveBtn} 
          onPress={handleSave}
          disabled={loading}
        >
          {loading ? <ActivityIndicator color="white" /> : <Text style={styles.saveText}>Save Changes</Text>}
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  scroll: { padding: 20 },
  header: { fontSize: 24, fontWeight: 'bold', marginBottom: 20, color: '#333' },
  
  label: { fontWeight: '600', marginBottom: 5, color: '#555' },
  input: { 
    borderWidth: 1, borderColor: '#ddd', borderRadius: 8, 
    padding: 12, fontSize: 16, backgroundColor: '#f9f9f9', marginBottom: 20 
  },
  
  saveBtn: { backgroundColor: '#007bff', padding: 15, borderRadius: 10, alignItems: 'center' },
  saveText: { color: 'white', fontWeight: 'bold', fontSize: 16 }
});

export default EditProfileScreen;
