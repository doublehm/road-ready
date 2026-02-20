import React, { useState, useContext } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, ActivityIndicator, Alert, ScrollView, Image } from 'react-native';
import client from '../api/client';
import { AuthContext } from '../context/AuthContext';
import { SafeAreaView } from 'react-native-safe-area-context';
import * as ImagePicker from 'expo-image-picker';
import Ionicons from 'react-native-vector-icons/Ionicons';
import DateTimePicker from '@react-native-community/datetimepicker';

const SetupInstructorScreen = ({ navigation }) => {
  const { logout, fetchUser, userToken } = useContext(AuthContext);
  const [city, setCity] = useState('');
  const [hourlyRate, setHourlyRate] = useState('');
  const [bio, setBio] = useState('');
  const [carModel, setCarModel] = useState('');
  const [insurance, setInsurance] = useState('');
  const [certId, setCertId] = useState('');
  
  // License Classes State
  const [classes, setClasses] = useState([
      { id: 'c5', name: 'Class 5', selected: false, price: '' },
      { id: 'c7', name: 'Class 7', selected: false, price: '' },
      { id: 'c4', name: 'Class 4', selected: false, price: '' },
  ]);

  const [expiry, setExpiry] = useState(new Date());
  const [showPicker, setShowPicker] = useState(false);

  const [image, setImage] = useState(null); // License
  const [insuranceImage, setInsuranceImage] = useState(null); // Insurance
  const [loading, setLoading] = useState(false);

  const onDateChange = (event, selectedDate) => {
    setShowPicker(false);
    if (selectedDate) setExpiry(selectedDate);
  };

  const toggleClass = (index) => {
      const newClasses = [...classes];
      newClasses[index].selected = !newClasses[index].selected;
      // Default price to base rate if empty
      if (newClasses[index].selected && !newClasses[index].price) {
          newClasses[index].price = hourlyRate;
      }
      setClasses(newClasses);
  };

  const updateClassPrice = (index, text) => {
      const newClasses = [...classes];
      newClasses[index].price = text;
      setClasses(newClasses);
  };

  const pickImage = async (type) => {
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
              if (!result.canceled) {
                  if (type === 'license') setImage(result.assets[0].uri);
                  else setInsuranceImage(result.assets[0].uri);
              }
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
              if (!result.canceled) {
                  if (type === 'license') setImage(result.assets[0].uri);
                  else setInsuranceImage(result.assets[0].uri);
              }
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
    if (!city || !hourlyRate || !image || !insuranceImage) {
      Alert.alert('Error', 'Please fill in all fields and upload both documents.');
      return;
    }

    setLoading(true);
    try {
      // Prepare License Classes JSON
      const selectedClasses = classes
          .filter(c => c.selected)
          .map(c => ({
              license_class: c.name,
              price: parseFloat(c.price || hourlyRate)
          }));

      const dateStr = expiry.toISOString().split('T')[0];
      
      const formData = new FormData();
      formData.append('bio', bio);
      formData.append('hourly_rate', hourlyRate);
      formData.append('city', city);
      formData.append('car_model', carModel);
      formData.append('insurance_policy', insurance);
      formData.append('certification_id', certId);
      
      formData.append('license_image', { uri: image, name: 'license.jpg', type: 'image/jpeg' });
      formData.append('insurance_image', { uri: insuranceImage, name: 'insurance.jpg', type: 'image/jpeg' });
      formData.append('license_classes', JSON.stringify(selectedClasses));

      await client.post('/setup-instructor', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      
      Alert.alert('Success', 'Profile setup complete!', [
        { text: 'OK', onPress: () => fetchUser(userToken) }
      ]);
    } catch (e) {
      console.log(e);
      Alert.alert('Error', 'Setup failed.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text style={styles.title}>Instructor Profile</Text>

        <Text style={styles.label}>City</Text>
        <TextInput style={styles.input} placeholder="e.g. Vancouver" value={city} onChangeText={setCity} />

        <Text style={styles.label}>Base Hourly Rate ($)</Text>
        <TextInput style={styles.input} placeholder="50.00" value={hourlyRate} onChangeText={setHourlyRate} keyboardType="numeric" />

        <Text style={styles.label}>License Classes Taught</Text>
        <View style={styles.classContainer}>
            {classes.map((cls, index) => (
                <View key={cls.id} style={styles.classRow}>
                    <TouchableOpacity 
                        style={[styles.checkbox, cls.selected && styles.checked]} 
                        onPress={() => toggleClass(index)}
                    >
                        {cls.selected && <Ionicons name="checkmark" size={16} color="white" />}
                    </TouchableOpacity>
                    <Text style={styles.classLabel}>{cls.name}</Text>
                    {cls.selected && (
                        <TextInput 
                            style={styles.priceInput} 
                            placeholder={hourlyRate || "Rate"} 
                            value={cls.price} 
                            onChangeText={(text) => updateClassPrice(index, text)}
                            keyboardType="numeric"
                        />
                    )}
                </View>
            ))}
        </View>

        <Text style={styles.label}>Bio</Text>
        <TextInput style={[styles.input, {height: 80}]} placeholder="About you..." value={bio} onChangeText={setBio} multiline />

        <Text style={styles.label}>Car Model</Text>
        <TextInput style={styles.input} placeholder="Toyota Prius" value={carModel} onChangeText={setCarModel} />

        <Text style={styles.label}>Insurance #</Text>
        <TextInput style={styles.input} placeholder="INS-123" value={insurance} onChangeText={setInsurance} />

        <Text style={styles.label}>Cert ID</Text>
        <TextInput style={styles.input} placeholder="CERT-999" value={certId} onChangeText={setCertId} />

        <Text style={styles.label}>Cert Expiry</Text>
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
        <TouchableOpacity style={styles.cameraBtn} onPress={() => pickImage('license')}>
          {image ? (
            <Image source={{ uri: image }} style={styles.preview} />
          ) : (
            <View style={styles.cameraPlaceholder}>
              <Ionicons name="camera" size={40} color="#666" />
              <Text style={styles.cameraText}>Upload License</Text>
            </View>
          )}
        </TouchableOpacity>

        <Text style={styles.label}>Insurance Document</Text>
        <TouchableOpacity style={styles.cameraBtn} onPress={() => pickImage('insurance')}>
          {insuranceImage ? (
            <Image source={{ uri: insuranceImage }} style={styles.preview} />
          ) : (
            <View style={styles.cameraPlaceholder}>
              <Ionicons name="document-text" size={40} color="#666" />
              <Text style={styles.cameraText}>Upload Insurance</Text>
            </View>
          )}
        </TouchableOpacity>

        <TouchableOpacity style={styles.button} onPress={handleSetup} disabled={loading}>
          {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.buttonText}>Complete Setup</Text>}
        </TouchableOpacity>

        <TouchableOpacity onPress={logout} style={{alignItems: 'center', marginBottom: 20}}>
            <Text style={{color: 'red'}}>Logout</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  scroll: { padding: 20 },
  title: { fontSize: 24, fontWeight: 'bold', marginBottom: 20, textAlign: 'center' },
  label: { fontWeight: 'bold', marginBottom: 5, color: '#333' },
  input: { backgroundColor: '#f9f9f9', padding: 12, borderRadius: 10, marginBottom: 15, borderWidth: 1, borderColor: '#eee' },
  
  classContainer: { marginTop: 5, marginBottom: 15 },
  classRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 10 },
  checkbox: { width: 24, height: 24, borderRadius: 4, borderWidth: 1, borderColor: '#ccc', marginRight: 10, justifyContent: 'center', alignItems: 'center' },
  checked: { backgroundColor: '#28a745', borderColor: '#28a745' },
  classLabel: { flex: 1, fontSize: 16 },
  priceInput: { width: 80, padding: 8, borderWidth: 1, borderColor: '#eee', borderRadius: 8, backgroundColor: '#fff' },

  dateBtn: { 
      flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
      backgroundColor: '#f9f9f9', padding: 12, borderRadius: 10, marginBottom: 15, borderWidth: 1, borderColor: '#eee'
  },
  dateText: { fontSize: 16 },

  cameraBtn: { marginBottom: 20 },
  cameraPlaceholder: { width: '100%', height: 150, backgroundColor: '#f0f0f0', borderRadius: 10, justifyContent: 'center', alignItems: 'center', borderWidth: 2, borderColor: '#ddd', borderStyle: 'dashed' },
  preview: { width: '100%', height: 150, borderRadius: 10 },
  cameraText: { marginTop: 10, color: '#666' },
  button: { backgroundColor: '#28a745', padding: 15, borderRadius: 10, alignItems: 'center', marginBottom: 20 },
  buttonText: { color: '#fff', fontSize: 18, fontWeight: 'bold' },
});

export default SetupInstructorScreen;
