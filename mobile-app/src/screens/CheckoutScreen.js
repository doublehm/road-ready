import React, { useContext, useState, useRef } from 'react';
import { View, Text, StyleSheet, Alert, ActivityIndicator, TouchableOpacity } from 'react-native';
import { WebView } from 'react-native-webview';
import { SafeAreaView } from 'react-native-safe-area-context';
import Ionicons from '@expo/vector-icons/Ionicons';
import { AuthContext } from '../context/AuthContext';
import { SERVER_URL } from '../api/client';

const BASE_URL = SERVER_URL;

const CheckoutScreen = ({ route, navigation }) => {
  const { bookingId } = route.params;
  const { userInfo } = useContext(AuthContext);
  const [loading, setLoading] = useState(true);
  const [paymentDone, setPaymentDone] = useState(false);
  const webViewRef = useRef(null);

  const checkoutUrl = `${BASE_URL}/checkout/${bookingId}`;

  // Inject user_id cookie so the web checkout page recognizes the user
  const injectedJS = `
    document.cookie = "user_id=${userInfo?.id}; path=/";
    true;
  `;

  const handleNavigationChange = (navState) => {
    if (paymentDone) return;

    // Detect payment success redirect
    if (navState.url.includes('/payment/success') || navState.url.includes('booking_success')) {
      setPaymentDone(true);
      Alert.alert(
        'Payment Successful',
        'Your diagnostic ride has been booked! The instructor will confirm the session shortly.',
        [{ text: 'OK', onPress: () => navigation.navigate('StudentMain', { screen: 'Dashboard' }) }]
      );
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Ionicons name="arrow-back" size={24} color="#333" />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Payment</Text>
        <View style={{ width: 24 }} />
      </View>

      {loading && (
        <View style={styles.loadingOverlay}>
          <ActivityIndicator size="large" color="#007bff" />
          <Text style={styles.loadingText}>Loading payment form...</Text>
        </View>
      )}

      <WebView
        ref={webViewRef}
        source={{ uri: checkoutUrl }}
        injectedJavaScriptBeforeContentLoaded={injectedJS}
        onNavigationStateChange={handleNavigationChange}
        onLoadEnd={() => setLoading(false)}
        style={styles.webview}
        javaScriptEnabled={true}
        domStorageEnabled={true}
      />
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 15,
    backgroundColor: 'white',
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },
  headerTitle: { fontSize: 18, fontWeight: 'bold', color: '#333' },
  webview: { flex: 1 },
  loadingOverlay: {
    position: 'absolute',
    top: 80,
    left: 0,
    right: 0,
    alignItems: 'center',
    zIndex: 10,
  },
  loadingText: { marginTop: 10, color: '#666', fontSize: 14 },
});

export default CheckoutScreen;
