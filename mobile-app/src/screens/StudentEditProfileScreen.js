import React, { useState, useContext } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, ActivityIndicator, Alert, ScrollView, Image } from 'react-native';
import client from '../api/client';
import { AuthContext } from '../context/AuthContext';
import { SafeAreaView } from 'react-native-safe-area-context';
import * as ImagePicker from 'expo-image-picker';
import Ionicons from 'react-native-vector-icons/Ionicons';
import DateTimePicker from '@react-native-community/datetimepicker';

// Update this to your machine's IP
const SERVER_URL = "http://10.32.100.57:8000";

const StudentEditProfileScreen = ({ navigation }) => {
  const { userInfo, fetchUser, userToken } = useContext(AuthContext);
  const profile = userInfo?.student_profile || {};

  // Basic Info State
  const [fullName, setFullName] = useState(userInfo?.full_name || '');
  const [phone, setPhone] = useState(userInfo?.phone_number || '');
  const [email, setEmail] = useState(userInfo?.email || '');

  // Profile Info State
  const [age, setAge] = useState(profile.age?.toString() || '');
  const [licenseNumber, setLicenseNumber] = useState(profile.l_license_number || '');
  const [expiry, setExpiry] = useState(profile.license_expiry ? new Date(profile.license_expiry) : new Date());
  
  const [showPicker, setShowPicker] = useState(false);
  const [image, setImage] = useState(null); // New image URI
  const [loading, setLoading] = useState(false);

  // Existing image
  const currentImage = profile.license_image ? `${SERVER_URL}/static/uploads/${profile.license_image}` : null;

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
              Alert.alert("Error", "Could not open camera: " + e.message);
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
              Alert.alert("Error", "Could not open gallery: " + e.message);
            }
          }
        },
        { text: "Cancel", style: "cancel" }
      ]
    );
  };

  const handleSave = async () => {
    setLoading(true);
    try {
      // 1. Update Basic User Info
      await client.put('/users/me', {
        full_name: fullName,
        phone_number: phone,
        email: email
      });

      // 2. Upload Image if new
      let filename = profile.license_image;
      if (image) {
        const formData = new FormData();
        formData.append('file', { uri: image, name: 'license.jpg', type: 'image/jpeg' });
        const uploadRes = await client.post('/users/me/upload-license', formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        });
        filename = uploadRes.data.filename;
      }

      const dateStr = expiry.toISOString().split('T')[0];

      // 3. Update Student Profile
      await client.put('/users/me/student-profile', {
        age: parseInt(age),
        l_license_number: licenseNumber,
        license_expiry: dateStr,
        license_image: filename
      });
      
      await fetchUser(userToken);
      Alert.alert('Success', 'Profile updated! Re-verification may be required.');
      navigation.goBack();
    } catch (e) {
      console.log(e);
      Alert.alert('Error', 'Update failed. ' + (e.response?.data?.detail || e.message));
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text style={styles.header}>Edit Profile</Text>

        <Text style={styles.sectionHeader}>Personal Info</Text>
        <Text style={styles.label}>Full Name</Text>
        <TextInput style={styles.input} value={fullName} onChangeText={setFullName} />
        
        <Text style={styles.label}>Email</Text>
        <TextInput style={styles.input} value={email} onChangeText={setEmail} keyboardType="email-address" autoCapitalize="none"/>

        <Text style={styles.label}>Phone</Text>
        <TextInput style={styles.input} value={phone} onChangeText={setPhone} keyboardType="phone-pad" />

        <Text style={styles.sectionHeader}>License Info</Text>
        <Text style={styles.label}>Age</Text>
        <TextInput style={styles.input} value={age} onChangeText={setAge} keyboardType="numeric" />

        <Text style={styles.label}>License #</Text>
        <TextInput style={styles.input} value={licenseNumber} onChangeText={setLicenseNumber} />

        <Text style={styles.label}>Expiry Date</Text>
        <TouchableOpacity style={styles.dateBtn} onPress={() => setShowPicker(true)}>
            <Text style={styles.dateText}>{expiry.toISOString().split('T')[0]}</Text>
            <Ionicons name="calendar" size={20} color="#666" />
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
          ) : currentImage ? (
            <Image source={{ uri: currentImage }} style={styles.preview} />
          ) : (
            <View style={styles.cameraPlaceholder}>
              <Ionicons name="camera" size={40} color="#666" />
              <Text style={styles.cameraText}>Take New Photo</Text>
            </View>
          )}
        </TouchableOpacity>
        
        <Text style={styles.warningText}>
          Note: Updating license info will require admin approval again.
        </Text>

        <Text style={styles.sectionHeader}>Legal</Text>
        <TouchableOpacity 
          style={styles.legalBtn} 
          onPress={() => navigation.navigate('Legal', { type: 'terms' })}
        >
          <Text style={styles.legalBtnText}>Terms of Service</Text>
          <Ionicons name="chevron-forward" size={20} color="#666" />
        </TouchableOpacity>

        <TouchableOpacity 
          style={styles.legalBtn} 
          onPress={() => navigation.navigate('Legal', { type: 'privacy' })}
        >
          <Text style={styles.legalBtnText}>Privacy Policy</Text>
          <Ionicons name="chevron-forward" size={20} color="#666" />
        </TouchableOpacity>

        <TouchableOpacity style={styles.saveBtn} onPress={handleSave} disabled={loading}>
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
  sectionHeader: { fontSize: 18, fontWeight: 'bold', marginTop: 10, marginBottom: 15, color: '#007bff' },
  label: { fontWeight: '600', marginBottom: 5, color: '#555' },
  input: { 
    borderWidth: 1, borderColor: '#ddd', borderRadius: 8, 
    padding: 12, fontSize: 16, backgroundColor: '#f9f9f9', marginBottom: 15 
  },
  dateBtn: { 
      flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
      backgroundColor: '#f9f9f9', padding: 12, borderRadius: 10, marginBottom: 15, borderWidth: 1, borderColor: '#eee'
  },
  dateText: { fontSize: 16 },
  cameraBtn: { marginBottom: 10, alignItems: 'center' },
  cameraPlaceholder: {
    width: '100%', height: 200, backgroundColor: '#f0f0f0', borderRadius: 10,
    justifyContent: 'center', alignItems: 'center', borderWidth: 2, borderColor: '#ddd', borderStyle: 'dashed'
  },
  preview: { width: '100%', height: 200, borderRadius: 10, resizeMode: 'cover' },
  cameraText: { marginTop: 10, color: '#666' },
  hint: { textAlign: 'center', color: '#999', marginBottom: 20 },
  warningText: { color: '#856404', backgroundColor: '#fff3cd', padding: 10, borderRadius: 5, marginBottom: 20, fontSize: 14 },
  
  legalBtn: { 
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#eee', marginBottom: 10
  },
  legalBtnText: { fontSize: 16, color: '#333' },

  saveBtn: { backgroundColor: '#007bff', padding: 15, borderRadius: 10, alignItems: 'center', marginBottom: 40 },
  saveText: { color: 'white', fontWeight: 'bold', fontSize: 16 }
});

export default StudentEditProfileScreen;