import React, { useContext } from 'react';
import { View, Text, StyleSheet, Button } from 'react-native';
import { AuthContext } from '../context/AuthContext';
import { SafeAreaView } from 'react-native-safe-area-context';

const InstructorHomeScreen = ({ navigation }) => {
  const { logout } = useContext(AuthContext);

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.text}>Welcome, Instructor! 👨‍🏫</Text>
      <View style={styles.buttonContainer}>
        <Button title="Grade Student" onPress={() => navigation.navigate('GradeStudent')} />
      </View>
      <View style={styles.buttonContainer}>
         <Button title="My Schedule" onPress={() => {}} />
      </View>
      <View style={styles.buttonContainer}>
         <Button title="Earnings" onPress={() => {}} />
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

export default InstructorHomeScreen;
