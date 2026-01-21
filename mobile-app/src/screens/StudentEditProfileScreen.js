import React, { useState, useContext } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, ActivityIndicator, Alert, ScrollView, Image } from 'react-native';
import client from '../api/client';
import { AuthContext } from '../context/AuthContext';
import { SafeAreaView } from 'react-native-safe-area-context';
import * as ImagePicker from 'expo-image-picker';
import Ionicons from 'react-native-vector-icons/Ionicons';
import DateTimePicker from '@react-native-community/datetimepicker';

const StudentEditProfileScreen = ({ navigation }) => {
  const { userInfo, fetchUser, userToken } = useContext(AuthContext);
  const profile = userInfo?.student_profile || {};

  const [age, setAge] = useState(profile.age?.toString() || '');
  const [licenseNumber, setLicenseNumber] = useState(profile.l_license_number || '');
  const [expiry, setExpiry] = useState(profile.license_expiry ? new Date(profile.license_expiry) : new Date());
  const [showPicker, setShowPicker] = useState(false);
  const [image, setImage] = useState(null); // New image URI
  const [loading, setLoading] = useState(false);

  // Existing image
  const currentImage = profile.license_image ? `http://192.168.1.74:8000/static/uploads/${profile.license_image}` : null;

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
              console.log("Launching Camera...");
              let result = await ImagePicker.launchCameraAsync({
                mediaTypes: ImagePicker.MediaTypeOptions.Images,
                allowsEditing: true,
                quality: 0.5,
              });
              console.log("Camera Result:", result);
              if (!result.canceled) setImage(result.assets[0].uri);
            } catch (e) {
              console.log("Camera Error:", e);
              Alert.alert("Error", "Could not open camera: " + e.message);
            }
          }
        },
        {
          text: "Gallery",
          onPress: async () => {
            try {
              console.log("Launching Gallery...");
              let result = await ImagePicker.launchImageLibraryAsync({
                mediaTypes: ImagePicker.MediaTypeOptions.Images,
                allowsEditing: true,
                quality: 0.5,
              });
              console.log("Gallery Result:", result);
              if (!result.canceled) setImage(result.assets[0].uri);
            } catch (e) {
              console.log("Gallery Error:", e);
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
      let filename = profile.license_image;

      // 1. Upload new image if selected
      if (image) {
        const formData = new FormData();
        formData.append('file', { uri: image, name: 'license.jpg', type: 'image/jpeg' });
        const uploadRes = await client.post('/users/me/upload-license', formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        });
        filename = uploadRes.data.filename;
      }

      const dateStr = expiry.toISOString().split('T')[0];

      // 2. Update Profile
      await client.put('/users/me/student-profile', {
        age: parseInt(age),
        l_license_number: licenseNumber,
        license_expiry: dateStr,
        license_image: filename
      });
      
      await fetchUser(userToken);
      Alert.alert('Success', 'Profile updated!');
      navigation.goBack();
    } catch (e) {
      console.log(e);
      Alert.alert('Error', 'Update failed.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text style={styles.header}>Edit Profile</Text>

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
        {currentImage && !image && <Text style={styles.hint}>Tap image to retake</Text>}

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
  label: { fontWeight: '600', marginBottom: 5, color: '#555' },
  input: { 
    borderWidth: 1, borderColor: '#ddd', borderRadius: 8, 
    padding: 12, fontSize: 16, backgroundColor: '#f9f9f9', marginBottom: 20 
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
  preview: { width: '100%', height: 200, borderRadius: 10 },
  cameraText: { marginTop: 10, color: '#666' },
  hint: { textAlign: 'center', color: '#999', marginBottom: 20 },
  
  saveBtn: { backgroundColor: '#007bff', padding: 15, borderRadius: 10, alignItems: 'center', marginBottom: 40 },
  saveText: { color: 'white', fontWeight: 'bold', fontSize: 16 }
});

export default StudentEditProfileScreen;
