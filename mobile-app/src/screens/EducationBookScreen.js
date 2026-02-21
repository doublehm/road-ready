import React from 'react';
import { StyleSheet, View } from 'react-native';
import { WebView } from 'react-native-webview';
import { SafeAreaView } from 'react-native-safe-area-context';
import { SERVER_URL } from '../api/client';

const EducationBookScreen = ({ route }) => {
  const { province } = route.params;
  
  // Pointing to the mobile-optimized web viewer we built
  // Using the local IP discovered earlier
  const uri = `${SERVER_URL}/education/${province}`;

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.webContainer}>
        <WebView 
          source={{ uri }}
          style={styles.webview}
          javaScriptEnabled={true}
          domStorageEnabled={true}
          startInLoadingState={true}
          scalesPageToFit={true}
        />
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#333' },
  webContainer: { flex: 1 },
  webview: { flex: 1 }
});

export default EducationBookScreen;
