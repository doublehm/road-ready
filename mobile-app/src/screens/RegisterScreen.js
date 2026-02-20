import React, { useState, useContext } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, ActivityIndicator, Alert, ScrollView } from 'react-native';
import Ionicons from 'react-native-vector-icons/Ionicons';
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
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text style={styles.title}>Create Account</Text>

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

        <TextInput
          style={styles.input}
          placeholder="Full Name"
          value={fullName}
          onChangeText={setFullName}
        />

        <TextInput
          style={styles.input}
          placeholder="Email Address"
          value={email}
          onChangeText={setEmail}
          autoCapitalize="none"
          keyboardType="email-address"
        />

        <TextInput
          style={styles.input}
          placeholder="Phone Number (e.g. 555-0199)"
          value={phone}
          onChangeText={setPhone}
          keyboardType="phone-pad"
        />

        <TextInput
          style={styles.input}
          placeholder="Password"
          value={password}
          onChangeText={setPassword}
          secureTextEntry
        />
        <Text style={styles.hint}>Must have 8+ chars, 1 uppercase, 1 special char</Text>

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
          {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.buttonText}>Sign Up</Text>}
        </TouchableOpacity>

        <TouchableOpacity onPress={() => navigation.navigate('Login')} style={styles.link}>
          <Text style={styles.linkText}>Already have an account? Login</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  scroll: { padding: 20 },
  title: { fontSize: 28, fontWeight: 'bold', marginBottom: 30, textAlign: 'center', color: '#333' },
  roleContainer: { flexDirection: 'row', marginBottom: 20, backgroundColor: '#f0f0f0', borderRadius: 10, padding: 5 },
  roleBtn: { flex: 1, padding: 10, alignItems: 'center', borderRadius: 8 },
  roleBtnActive: { backgroundColor: '#fff', shadowColor: '#000', shadowOpacity: 0.1, shadowRadius: 2, elevation: 2 },
  roleText: { fontWeight: '600', color: '#666' },
  roleTextActive: { color: '#007bff' },
  input: {
    backgroundColor: '#f9f9f9',
    padding: 15,
    borderRadius: 10,
    marginBottom: 15,
    borderWidth: 1,
    borderColor: '#eee',
  },
  hint: { fontSize: 12, color: '#666', marginBottom: 20, marginLeft: 5 },
  termsContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 25,
    paddingHorizontal: 5,
  },
  checkbox: {
    width: 24,
    height: 24,
    borderRadius: 6,
    borderWidth: 2,
    borderColor: '#007bff',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  checkboxChecked: {
    backgroundColor: '#007bff',
  },
  termsText: {
    flex: 1,
    fontSize: 14,
    color: '#333',
    lineHeight: 20,
  },
  termsLink: {
    color: '#007bff',
    fontWeight: 'bold',
  },
  button: {
    backgroundColor: '#007bff',
    padding: 15,
    borderRadius: 10,
    alignItems: 'center',
    marginBottom: 20,
  },
  buttonText: { color: '#fff', fontSize: 18, fontWeight: 'bold' },
  link: { alignItems: 'center' },
  linkText: { color: '#007bff', fontSize: 16 },
});

export default RegisterScreen;
