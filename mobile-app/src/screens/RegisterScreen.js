import React, { useState, useContext } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, ActivityIndicator, Alert, ScrollView } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';
import client from '../api/client';
import { AuthContext } from '../context/AuthContext';
import { SafeAreaView } from 'react-native-safe-area-context';

const RegisterScreen = ({ navigation }) => {
  const { login } = useContext(AuthContext); // Auto-login after register
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('student'); // 'student' or 'instructor'
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleRegister = async () => {
    if (!fullName || !email || !phone || !password) {
      Alert.alert('Error', 'Please fill in all fields');
      return;
    }

    if (!termsAccepted) {
      Alert.alert('Legal Agreement', 'You must agree to the Terms of Service and Privacy Policy to continue.');
      return;
    }

    setLoading(true);
    try {
      // 1. Register User
      await client.post('/users/', {
        full_name: fullName,
        email: email,
        phone_number: phone,
        password: password,
        role: role
      });

      // 2. Auto Login
      Alert.alert('Success', 'Account created!', [
        { text: 'OK', onPress: () => login(email, password) }
      ]);
      
    } catch (e) {
      console.log("Registration Error:", e);
      if (e.response) {
          console.log("Response Data:", e.response.data);
          const msg = e.response.data.detail || "Registration failed";
          Alert.alert('Error', Array.isArray(msg) ? msg[0].msg : msg);
      } else {
          console.log("No response received");
          Alert.alert('Network Error', 'Could not connect to server. Check your internet or server status.\n' + e.message);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <Text style={styles.title}>Create Account</Text>
          <Text style={styles.subtitle}>START YOUR JOURNEY</Text>
        </View>

        <View style={styles.roleContainer}>
          <TouchableOpacity 
            style={[styles.roleBtn, role === 'student' && styles.roleBtnActive]}
            onPress={() => setRole('student')}
          >
            <Text style={[styles.roleText, role === 'student' && styles.roleTextActive]}>Student</Text>
          </TouchableOpacity>
          <TouchableOpacity 
            style={[styles.roleBtn, role === 'instructor' && styles.roleBtnActive]}
            onPress={() => setRole('instructor')}
          >
            <Text style={[styles.roleText, role === 'instructor' && styles.roleTextActive]}>Instructor</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.inputContainer}>
          <TextInput
            style={styles.input}
            placeholder="Full Name"
            placeholderTextColor="#64748B"
            value={fullName}
            onChangeText={setFullName}
          />

          <TextInput
            style={styles.input}
            placeholder="Email Address"
            placeholderTextColor="#64748B"
            value={email}
            onChangeText={setEmail}
            autoCapitalize="none"
            keyboardType="email-address"
          />

          <TextInput
            style={styles.input}
            placeholder="Phone Number"
            placeholderTextColor="#64748B"
            value={phone}
            onChangeText={setPhone}
            keyboardType="phone-pad"
          />

          <TextInput
            style={styles.input}
            placeholder="Password"
            placeholderTextColor="#64748B"
            value={password}
            onChangeText={setPassword}
            secureTextEntry
          />
          <Text style={styles.hint}>Must have 8+ chars, 1 uppercase, 1 special char</Text>
        </View>

        <View style={styles.termsContainer}>
          <TouchableOpacity 
            style={[styles.checkbox, termsAccepted && styles.checkboxChecked]}
            onPress={() => setTermsAccepted(!termsAccepted)}
          >
            {termsAccepted && <Ionicons name="checkmark" size={16} color="#fff" />}
          </TouchableOpacity>
          <Text style={styles.termsText}>
            I agree to the{' '}
            <Text style={styles.termsLink} onPress={() => navigation.navigate('Legal')}>Terms of Service</Text>
            {' '}and{' '}
            <Text style={styles.termsLink} onPress={() => navigation.navigate('Legal')}>Privacy Policy</Text>.
          </Text>
        </View>

        <TouchableOpacity 
          style={styles.button} 
          onPress={handleRegister}
          disabled={loading}
        >
          {loading ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text style={styles.buttonText}>Enlist Now</Text>
          )}
        </TouchableOpacity>

        <TouchableOpacity onPress={() => navigation.navigate('Login')} style={styles.link}>
          <Text style={styles.linkText}>
            Already have an account? <Text style={styles.linkTextBold}>Sign In</Text>
          </Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0B1326' },
  scroll: { padding: 24, paddingBottom: 60 },
  header: { alignItems: 'center', marginBottom: 32, marginTop: 20 },
  title: { fontSize: 32, fontWeight: '800', color: '#FFFFFF', letterSpacing: -1 },
  subtitle: { fontSize: 12, fontWeight: '800', color: '#15803D', letterSpacing: 2, marginTop: 4 },
  roleContainer: { 
    flexDirection: 'row', 
    marginBottom: 32, 
    backgroundColor: '#1E293B', 
    borderRadius: 16, 
    padding: 4 
  },
  roleBtn: { flex: 1, paddingVertical: 12, alignItems: 'center', borderRadius: 12 },
  roleBtnActive: { 
    backgroundColor: '#131B2E',
  },
  roleText: { fontWeight: '700', color: '#64748B', fontSize: 15 },
  roleTextActive: { color: '#FFFFFF' },
  inputContainer: { marginBottom: 12 },
  input: {
    backgroundColor: '#131B2E',
    padding: 18,
    borderRadius: 16,
    marginBottom: 16,
    fontSize: 16,
    color: '#E2E8F0',
  },
  hint: { fontSize: 12, color: '#94A3B8', marginBottom: 20, marginLeft: 4, fontWeight: '500' },
  termsContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 32,
    paddingHorizontal: 4,
  },
  checkbox: {
    width: 24,
    height: 24,
    borderRadius: 8,
    borderWidth: 2,
    borderColor: '#475569',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  checkboxChecked: {
    backgroundColor: '#15803D',
    borderColor: '#15803D',
  },
  termsText: {
    flex: 1,
    fontSize: 14,
    color: '#64748B',
    lineHeight: 20,
    fontWeight: '500',
  },
  termsLink: {
    color: '#E2E8F0',
    fontWeight: '700',
  },
  button: {
    backgroundColor: '#3B82F6',
    padding: 20,
    borderRadius: 16,
    alignItems: 'center',
    marginBottom: 24,
  },
  buttonText: { color: '#fff', fontSize: 18, fontWeight: '800', letterSpacing: -0.5 },
  link: { alignItems: 'center' },
  linkText: { color: '#64748B', fontSize: 15 },
  linkTextBold: { color: '#15803D', fontWeight: '800' },
});

export default RegisterScreen;
