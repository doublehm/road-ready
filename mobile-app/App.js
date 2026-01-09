import React, { useContext } from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createStackNavigator } from '@react-navigation/stack';
import { AuthProvider, AuthContext } from './src/context/AuthContext';
import LoginScreen from './src/screens/LoginScreen';
import StudentHomeScreen from './src/screens/StudentHomeScreen';
import InstructorHomeScreen from './src/screens/InstructorHomeScreen';
import DriveLogScreen from './src/screens/DriveLogScreen';
import FindInstructorScreen from './src/screens/FindInstructorScreen';
import GradeStudentScreen from './src/screens/GradeStudentScreen';
import QuizScreen from './src/screens/QuizScreen';
import { ActivityIndicator, View } from 'react-native';

const Stack = createStackNavigator();

const AppNav = () => {
  const { isLoading, userToken, userRole } = useContext(AuthContext);

  if (isLoading) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  return (
    <NavigationContainer>
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        {userToken === null ? (
          <Stack.Screen name="Login" component={LoginScreen} />
        ) : (
          // Role-based navigation
          userRole === 'instructor' ? (
            <>
              <Stack.Screen name="InstructorHome" component={InstructorHomeScreen} />
              <Stack.Screen name="GradeStudent" component={GradeStudentScreen} />
            </>
          ) : (
            <>
              <Stack.Screen name="StudentHome" component={StudentHomeScreen} />
              <Stack.Screen name="DriveLog" component={DriveLogScreen} />
              <Stack.Screen name="FindInstructor" component={FindInstructorScreen} />
              <Stack.Screen name="Quiz" component={QuizScreen} />
            </>
          )
        )}
      </Stack.Navigator>
    </NavigationContainer>
  );
};

export default function App() {
  return (
    <AuthProvider>
      <AppNav />
    </AuthProvider>
  );
}
