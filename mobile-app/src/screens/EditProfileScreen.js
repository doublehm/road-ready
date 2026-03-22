import React, { useState, useContext } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, ActivityIndicator, Alert, ScrollView, Image } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Ionicons from '@expo/vector-icons/Ionicons';
import * as ImagePicker from 'expo-image-picker';
import client, { SERVER_URL } from '../api/client';
import { AuthContext } from '../context/AuthContext';

const EditProfileScreen = ({ navigation }) => {
  const { userInfo, fetchUser, userToken } = useContext(AuthContext);
  const profile = userInfo?.instructor_profile || {};

  // Basic Info
  const [fullName, setFullName] = useState(userInfo?.full_name || '');
  const [phone, setPhone] = useState(userInfo?.phone_number || '');
  const [email, setEmail] = useState(userInfo?.email || '');

  // Instructor Info
  const [city, setCity] = useState(profile.city || '');
  const [hourlyRate, setHourlyRate] = useState(profile.hourly_rate?.toString() || '');
  const [bio, setBio] = useState(profile.bio || '');
  const [carModel, setCarModel] = useState(profile.car_model || '');
  const [insurancePolicy, setInsurancePolicy] = useState(profile.insurance_policy || '');
  const [certificationId, setCertificationId] = useState(profile.certification_id || '');

  // Images
  const [licenseImage, setLicenseImage] = useState(null);
  const [insuranceImage, setInsuranceImage] = useState(null);
  const [certificationImage, setCertificationImage] = useState(null);
  const [loading, setLoading] = useState(false);

  const currentLicenseImage = profile.license_image ? `${SERVER_URL}/static/uploads/${profile.license_image}` : null;
  const currentInsuranceImage = profile.insurance_image ? `${SERVER_URL}/static/uploads/${profile.insurance_image}` : null;
  const currentCertificationImage = profile.certification_image ? `${SERVER_URL}/static/uploads/${profile.certification_image}` : null;

  const pickImage = async (setter) => {
    Alert.alert("Upload Photo", "Choose an option", [
        {
          text: "Camera",
          onPress: async () => {
            try {
              let result = await ImagePicker.launchCameraAsync({ mediaTypes: ImagePicker.MediaTypeOptions.Images, allowsEditing: true, quality: 0.5 });
              if (!result.canceled) setter(result.assets[0].uri);
            } catch (e) { Alert.alert("Error", e.message); }
          }
        },
        {
          text: "Gallery",
          onPress: async () => {
            try {
              let result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ImagePicker.MediaTypeOptions.Images, allowsEditing: true, quality: 0.5 });
              if (!result.canceled) setter(result.assets[0].uri);
            } catch (e) { Alert.alert("Error", e.message); }
          }
        },
        { text: "Cancel", style: "cancel" }
    ]);
  };

  const uploadFile = async (uri) => {
    const formData = new FormData();
    formData.append('file', { uri: uri, name: 'upload.jpg', type: 'image/jpeg' });
    const res = await client.post('/users/me/upload-license', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
    });
    return res.data.filename;
  };

  const handleSave = async () => {
    setLoading(true);
    try {
      // 1. Update Basic Info
      await client.put('/users/me', {
        full_name: fullName,
        phone_number: phone,
        email: email
      });

      // 2. Upload Images
      let newLicenseImg = profile.license_image;
      let newInsuranceImg = profile.insurance_image;
      let newCertImg = profile.certification_image;

      if (licenseImage) newLicenseImg = await uploadFile(licenseImage);
      if (insuranceImage) newInsuranceImg = await uploadFile(insuranceImage);
      if (certificationImage) newCertImg = await uploadFile(certificationImage);

      // 3. Update Instructor Profile
      await client.put('/users/me/instructor-profile', {
        city,
        hourly_rate: parseFloat(hourlyRate),
        bio,
        car_model: carModel,
        insurance_policy: insurancePolicy,
        certification_id: certificationId,
        license_image: newLicenseImg,
        insurance_image: newInsuranceImg,
        certification_image: newCertImg
      });
      
      await fetchUser(userToken);
      Alert.alert("Success", "Profile updated! Re-verification may be required.");
      navigation.goBack();
    } catch (e) {
      console.log(e);
      Alert.alert("Error", "Failed to update profile. " + (e.response?.data?.detail || e.message));
    } finally {
      setLoading(false);
    }
  };

  const renderImagePicker = (label, imageUri, setter, currentUri) => (
    <View style={styles.imageSection}>
        <Text style={styles.label}>{label}</Text>
        <TouchableOpacity style={styles.cameraBtn} onPress={() => pickImage(setter)}>
          {imageUri ? (
            <Image source={{ uri: imageUri }} style={styles.preview} />
          ) : currentUri ? (
            <Image source={{ uri: currentUri }} style={styles.preview} />
          ) : (
            <View style={styles.cameraPlaceholder}>
              <Ionicons name="camera" size={30} color="#94A3B8" />
              <Text style={styles.cameraText}>Upload Photo</Text>
            </View>
          )}
        </TouchableOpacity>
    </View>
  );

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text style={styles.header}>Edit Profile</Text>

        <Text style={styles.sectionHeader}>Personal Info</Text>
        <TextInput style={styles.input} placeholder="Full Name" placeholderTextColor="#64748B" value={fullName} onChangeText={setFullName} />
        <TextInput style={styles.input} placeholder="Email" placeholderTextColor="#64748B" value={email} onChangeText={setEmail} keyboardType="email-address" autoCapitalize="none"/>
        <TextInput style={styles.input} placeholder="Phone" placeholderTextColor="#64748B" value={phone} onChangeText={setPhone} keyboardType="phone-pad" />

        <Text style={styles.sectionHeader}>Instructor Details</Text>
        <TextInput style={styles.input} placeholder="City" placeholderTextColor="#64748B" value={city} onChangeText={setCity} />
        <TextInput style={styles.input} placeholder="Hourly Rate ($)" placeholderTextColor="#64748B" value={hourlyRate} onChangeText={setHourlyRate} keyboardType="numeric" />
        <TextInput style={styles.input} placeholder="Car Model" placeholderTextColor="#64748B" value={carModel} onChangeText={setCarModel} />
        <TextInput 
          style={[styles.input, {height: 80}]} 
          placeholder="Bio" 
          placeholderTextColor="#64748B"
          value={bio} 
          onChangeText={setBio} 
          multiline 
          textAlignVertical="top"
        />

        <Text style={styles.sectionHeader}>Documents (Updates Require Approval)</Text>
        <TextInput style={styles.input} placeholder="Insurance Policy #" placeholderTextColor="#64748B" value={insurancePolicy} onChangeText={setInsurancePolicy} />
        <TextInput style={styles.input} placeholder="Certification ID" placeholderTextColor="#64748B" value={certificationId} onChangeText={setCertificationId} />

        <View style={styles.row}>
            <View style={{flex: 1, marginRight: 5}}>
                {renderImagePicker("License", licenseImage, setLicenseImage, currentLicenseImage)}
            </View>
            <View style={{flex: 1, marginRight: 5}}>
                {renderImagePicker("Insurance", insuranceImage, setInsuranceImage, currentInsuranceImage)}
            </View>
            <View style={{flex: 1}}>
                {renderImagePicker("ICBC Cert", certificationImage, setCertificationImage, currentCertificationImage)}
            </View>
        </View>

        <Text style={styles.warningText}>
          Note: Updating documents will temporarily hide your profile until admin approval.
        </Text>

        <Text style={styles.sectionHeader}>Legal</Text>
        <TouchableOpacity 
          style={styles.legalBtn} 
          onPress={() => navigation.navigate('Legal', { type: 'terms' })}
        >
          <Text style={styles.legalBtnText}>Terms of Service</Text>
          <Ionicons name="chevron-forward" size={18} color="#94A3B8" />
        </TouchableOpacity>

        <TouchableOpacity 
          style={styles.legalBtn} 
          onPress={() => navigation.navigate('Legal', { type: 'privacy' })}
        >
          <Text style={styles.legalBtnText}>Privacy Policy</Text>
          <Ionicons name="chevron-forward" size={18} color="#94A3B8" />
        </TouchableOpacity>

        <TouchableOpacity style={styles.saveBtn} onPress={handleSave} disabled={loading}>
          {loading ? <ActivityIndicator color="white" /> : <Text style={styles.saveText}>Save Changes</Text>}
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0B1326' },
  scroll: { padding: 20 },
  header: { fontSize: 24, fontWeight: 'bold', marginBottom: 20, color: '#FFFFFF' },
  sectionHeader: { fontSize: 18, fontWeight: 'bold', marginTop: 10, marginBottom: 15, color: '#3B82F6' },
  
  label: { fontWeight: '600', marginBottom: 5, color: '#CBD5E1', fontSize: 14 },
  input: { 
    borderWidth: 1, borderColor: '#1E293B', borderRadius: 8, 
    padding: 12, fontSize: 16, backgroundColor: '#131B2E', marginBottom: 15, color: '#E2E8F0'
  },
  
  row: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 15 },
  imageSection: { marginBottom: 10 },
  cameraBtn: { alignItems: 'center' },
  cameraPlaceholder: {
    width: '100%', height: 100, backgroundColor: '#1E293B', borderRadius: 10,
    justifyContent: 'center', alignItems: 'center', borderWidth: 1, borderColor: '#1E293B', borderStyle: 'dashed'
  },
  preview: { width: '100%', height: 100, borderRadius: 10, resizeMode: 'cover' },
  cameraText: { marginTop: 5, color: '#94A3B8', fontSize: 12 },

  warningText: { color: '#F59E0B', backgroundColor: 'rgba(245,158,11,0.15)', padding: 10, borderRadius: 5, marginBottom: 20, fontSize: 12 },
  
  legalBtn: { 
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: '#1E293B', marginBottom: 8
  },
  legalBtnText: { fontSize: 15, color: '#E2E8F0' },

  saveBtn: { backgroundColor: '#3B82F6', padding: 15, borderRadius: 10, alignItems: 'center', marginBottom: 40 },
  saveText: { color: 'white', fontWeight: 'bold', fontSize: 16 }
});

export default EditProfileScreen;