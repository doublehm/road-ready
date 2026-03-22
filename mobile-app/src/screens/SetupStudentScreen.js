import React, { useState, useContext } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, ActivityIndicator, Alert, ScrollView, Image, Platform } from 'react-native';
import client from '../api/client';
import { AuthContext } from '../context/AuthContext';
import { SafeAreaView } from 'react-native-safe-area-context';
import * as ImagePicker from 'expo-image-picker';
import Ionicons from '@expo/vector-icons/Ionicons';
import DateTimePicker from '@react-native-community/datetimepicker';

const SetupStudentScreen = ({ navigation }) => {
  const { userToken, logout, fetchUser } = useContext(AuthContext);
  const [age, setAge] = useState('');
  const [licenseNumber, setLicenseNumber] = useState('');
  
  // Date Picker State
  const [expiry, setExpiry] = useState(new Date());
  const [showPicker, setShowPicker] = useState(false);

  const [image, setImage] = useState(null);
  const [loading, setLoading] = useState(false);

  const onDateChange = (event, selectedDate) => {
    setShowPicker(false);
    if (selectedDate) setExpiry(selectedDate);
  };

  const pickImage = async () => {
    Alert.alert(
      "Upload Photo",
      "Choose an option",
      [
        {
          text: "Camera",
          onPress: async () => {
            try {
              let result = await ImagePicker.launchCameraAsync({
                mediaTypes: ImagePicker.MediaTypeOptions.Images,
                allowsEditing: true,
                quality: 0.5,
              });
              if (!result.canceled) setImage(result.assets[0].uri);
            } catch (e) {
              Alert.alert("Error", "Could not open camera.");
            }
          }
        },
        {
          text: "Gallery",
          onPress: async () => {
            try {
              let result = await ImagePicker.launchImageLibraryAsync({
                mediaTypes: ImagePicker.MediaTypeOptions.Images,
                allowsEditing: true,
                quality: 0.5,
              });
              if (!result.canceled) setImage(result.assets[0].uri);
            } catch (e) {
              Alert.alert("Error", "Could not open gallery.");
            }
          }
        },
        { text: "Cancel", style: "cancel" }
      ]
    );
  };

  const handleSetup = async () => {
    if (!age || !licenseNumber || !image) {
      Alert.alert('Error', 'Please fill in all fields.');
      return;
    }

    setLoading(true);
    try {
      const formData = new FormData();
      formData.append('file', { uri: image, name: 'license.jpg', type: 'image/jpeg' });
      const uploadRes = await client.post('/users/me/upload-license', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });

      const dateStr = expiry.toISOString().split('T')[0];

      await client.put('/users/me/student-profile', {
        age: parseInt(age),
        l_license_number: licenseNumber,
        license_expiry: dateStr,
        license_image: uploadRes.data.filename
      });
      
      Alert.alert('Success', 'Profile setup complete!', [
        { text: 'OK', onPress: () => fetchUser(userToken) }
      ]);
    } catch (e) {
      Alert.alert('Error', 'Setup failed.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text style={styles.title}>Student Verification</Text>

        <Text style={styles.label}>Age</Text>
        <TextInput style={styles.input} placeholder="18" placeholderTextColor="#64748B" value={age} onChangeText={setAge} keyboardType="numeric" />

        <Text style={styles.label}>License #</Text>
        <TextInput style={styles.input} placeholder="L-1234567" placeholderTextColor="#64748B" value={licenseNumber} onChangeText={setLicenseNumber} />

        <Text style={styles.label}>Expiry Date</Text>
        <TouchableOpacity style={styles.dateBtn} onPress={() => setShowPicker(true)}>
            <Text style={styles.dateText}>{expiry.toISOString().split('T')[0]}</Text>
            <Ionicons name="calendar" size={20} color="#94A3B8" />
        </TouchableOpacity>
        
        {showPicker && (
            <DateTimePicker
                value={expiry}
                mode="date"
                display="default"
                onChange={onDateChange}
            />
        )}

        <Text style={styles.label}>License Photo</Text>
        <TouchableOpacity style={styles.cameraBtn} onPress={pickImage}>
          {image ? (
            <Image source={{ uri: image }} style={styles.preview} />
          ) : (
            <View style={styles.cameraPlaceholder}>
              <Ionicons name="camera" size={40} color="#94A3B8" />
              <Text style={styles.cameraText}>Take Photo</Text>
            </View>
          )}
        </TouchableOpacity>

        <TouchableOpacity style={styles.button} onPress={handleSetup} disabled={loading}>
          {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.buttonText}>Complete Setup</Text>}
        </TouchableOpacity>

        <TouchableOpacity onPress={logout} style={{alignItems: 'center', marginVertical: 20}}>
            <Text style={{color: '#EF4444'}}>Logout</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0B1326' },
  scroll: { padding: 20 },
  title: { fontSize: 24, fontWeight: 'bold', marginBottom: 20, textAlign: 'center', color: '#FFFFFF' },
  label: { fontWeight: 'bold', marginBottom: 5, color: '#FFFFFF' },
  input: { backgroundColor: '#131B2E', padding: 12, borderRadius: 10, marginBottom: 15, borderWidth: 1, borderColor: '#1E293B', color: '#E2E8F0' },
  
  dateBtn: { 
      flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
      backgroundColor: '#131B2E', padding: 12, borderRadius: 10, marginBottom: 15, borderWidth: 1, borderColor: '#1E293B'
  },
  dateText: { fontSize: 16, color: '#E2E8F0' },

  cameraBtn: { marginBottom: 20 },
  cameraPlaceholder: { width: '100%', height: 150, backgroundColor: '#1E293B', borderRadius: 10, justifyContent: 'center', alignItems: 'center', borderWidth: 2, borderColor: '#475569', borderStyle: 'dashed' },
  cameraText: { marginTop: 10, color: '#94A3B8' },
  preview: { width: '100%', height: 150, borderRadius: 10 },
  
  button: { backgroundColor: '#15803D', padding: 15, borderRadius: 10, alignItems: 'center' },
  buttonText: { color: '#fff', fontSize: 18, fontWeight: 'bold' },
});

export default SetupStudentScreen;
