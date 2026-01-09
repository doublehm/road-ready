import React, { useContext } from 'react';
import { View, Text, StyleSheet, Button } from 'react-native';
import { AuthContext } from '../context/AuthContext';
import { SafeAreaView } from 'react-native-safe-area-context';

const StudentHomeScreen = ({ navigation }) => {
  const { logout } = useContext(AuthContext);

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.text}>Welcome, Student! 🚗</Text>
      <View style={styles.buttonContainer}>
        <Button title="Find Instructor" onPress={() => navigation.navigate('FindInstructor')} />
      </View>
      <View style={styles.buttonContainer}>
        <Button title="Start Drive Log" onPress={() => navigation.navigate('DriveLog')} />
      </View>
      <View style={styles.buttonContainer}>
        <Button title="Practice Quiz" onPress={() => navigation.navigate('Quiz')} />
      </View>
      <View style={{ marginTop: 20 }}>
        <Button title="Logout" color="red" onPress={logout} />
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#fff',
  },
  text: {
    fontSize: 24,
    marginBottom: 20,
  },
  buttonContainer: {
    marginVertical: 10,
    width: '80%',
  },
});

export default StudentHomeScreen;
