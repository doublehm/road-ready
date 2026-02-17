import React, { useContext } from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createStackNavigator } from '@react-navigation/stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { AuthProvider, AuthContext } from './src/context/AuthContext';
import { ActivityIndicator, View } from 'react-native';
import Ionicons from 'react-native-vector-icons/Ionicons';

// Screens
import LoginScreen from './src/screens/LoginScreen';
import RegisterScreen from './src/screens/RegisterScreen';
import SetupStudentScreen from './src/screens/SetupStudentScreen';
import SetupInstructorScreen from './src/screens/SetupInstructorScreen';
import StudentHomeScreen from './src/screens/StudentHomeScreen';
import InstructorHomeScreen from './src/screens/InstructorHomeScreen';
import InstructorScheduleScreen from './src/screens/InstructorScheduleScreen';
import InstructorEarningsScreen from './src/screens/InstructorEarningsScreen';
import DriveLogScreen from './src/screens/DriveLogScreen';
import FindInstructorScreen from './src/screens/FindInstructorScreen';
import GradeStudentScreen from './src/screens/GradeStudentScreen';
import GradeDiagnosticRideScreen from './src/screens/GradeDiagnosticRideScreen';
import QuizScreen from './src/screens/QuizScreen';
import ConversationsScreen from './src/screens/ConversationsScreen';
import ChatScreen from './src/screens/ChatScreen';
import StudentProgressScreen from './src/screens/StudentProgressScreen';
import EducationHomeScreen from './src/screens/EducationHomeScreen';
import EducationBookScreen from './src/screens/EducationBookScreen';
import InstructorProfileScreen from './src/screens/InstructorProfileScreen';
import BookingFlowScreen from './src/screens/BookingFlowScreen';
import BookingRequestsScreen from './src/screens/BookingRequestsScreen';
import EditProfileScreen from './src/screens/EditProfileScreen';
import StudentEditProfileScreen from './src/screens/StudentEditProfileScreen';
import SessionDetailScreen from './src/screens/SessionDetailScreen';
import StudentDetailStatsScreen from './src/screens/StudentDetailStatsScreen';
import NotificationsScreen from './src/screens/NotificationsScreen';
import SupervisorHandoffScreen from './src/screens/SupervisorHandoffScreen';
import DiagnosticRideIntroScreen from './src/screens/DiagnosticRideIntroScreen';
import DiagnosticRideSetupScreen from './src/screens/DiagnosticRideSetupScreen';
import DiagnosticRideActiveScreen from './src/screens/DiagnosticRideActiveScreen';
import DiagnosticRideResultsScreen from './src/screens/DiagnosticRideResultsScreen';
import ModulesScreen from './src/screens/ModulesScreen';
import LegalScreen from './src/screens/LegalScreen';

const Stack = createStackNavigator();
const Tab = createBottomTabNavigator();

// --- Student Tab Navigator ---
const StudentTabs = () => {
  const { unreadMessageCount } = useContext(AuthContext);
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarIcon: ({ focused, color, size }) => {
          let iconName;
          if (route.name === 'Dashboard') iconName = focused ? 'home' : 'home-outline';
          else if (route.name === 'Find Instructor') iconName = focused ? 'search' : 'search-outline';
          else if (route.name === 'Learn') iconName = focused ? 'book' : 'book-outline';
          else if (route.name === 'Messages') iconName = focused ? 'chatbubble' : 'chatbubble-outline';
          return <Ionicons name={iconName} size={size} color={color} />;
        },
        tabBarActiveTintColor: '#007bff',
        tabBarInactiveTintColor: 'gray',
      })}
    >
      <Tab.Screen name="Dashboard" component={StudentHomeScreen} />
      <Tab.Screen name="Find Instructor" component={FindInstructorScreen} />
      <Tab.Screen name="Learn" component={EducationHomeScreen} />
      <Tab.Screen 
        name="Messages" 
        component={ConversationsScreen} 
        options={{ tabBarBadge: unreadMessageCount > 0 ? unreadMessageCount : null }}
      />
    </Tab.Navigator>
  );
};

// --- Instructor Tab Navigator ---
const InstructorTabs = () => {
  const { unreadMessageCount } = useContext(AuthContext);
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarIcon: ({ focused, color, size }) => {
          let iconName;
          if (route.name === 'Dashboard') iconName = focused ? 'grid' : 'grid-outline';
          else if (route.name === 'Schedule') iconName = focused ? 'calendar' : 'calendar-outline';
          else if (route.name === 'Messages') iconName = focused ? 'chatbubble' : 'chatbubble-outline';
          else if (route.name === 'Earnings') iconName = focused ? 'cash' : 'cash-outline';
          return <Ionicons name={iconName} size={size} color={color} />;
        },
        tabBarActiveTintColor: '#28a745',
        tabBarInactiveTintColor: 'gray',
      })}
    >
      <Tab.Screen name="Dashboard" component={InstructorHomeScreen} />
      <Tab.Screen name="Schedule" component={InstructorScheduleScreen} />
      <Tab.Screen 
        name="Messages" 
        component={ConversationsScreen} 
        options={{ tabBarBadge: unreadMessageCount > 0 ? unreadMessageCount : null }}
      />
      <Tab.Screen name="Earnings" component={InstructorEarningsScreen} />
    </Tab.Navigator>
  );
};

const AppNav = () => {
  const { isLoading, userToken, userRole, userInfo } = useContext(AuthContext);

  if (isLoading) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  // Setup Logic
  let needsSetup = false;
  if (userInfo) {
      if (userRole === 'student' && userInfo.student_profile?.age === 0) needsSetup = true;
      if (userRole === 'instructor' && userInfo.instructor_profile?.city === "Unknown") needsSetup = true;
  }

  return (
    <NavigationContainer>
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        {userToken === null ? (
          // Auth Stack
          <>
            <Stack.Screen name="Login" component={LoginScreen} />
            <Stack.Screen name="Register" component={RegisterScreen} />
          </>
        ) : needsSetup ? (
          // Setup Stack
          userRole === 'student' ? (
            <Stack.Screen name="SetupStudent" component={SetupStudentScreen} />
          ) : (
            <Stack.Screen name="SetupInstructor" component={SetupInstructorScreen} />
          )
        ) : (
          // Main App Stack (Tabs + Modals)
          <>
            {userRole === 'student' ? (
              <Stack.Screen name="StudentMain" component={StudentTabs} />
            ) : (
              <Stack.Screen name="InstructorMain" component={InstructorTabs} />
            )}
            
            {/* Common Modals/Detail Screens (Not in Tabs) */}
            <Stack.Screen name="Chat" component={ChatScreen} />
            <Stack.Screen name="EducationBook" component={EducationBookScreen} />
            <Stack.Screen name="Quiz" component={QuizScreen} />
            <Stack.Screen name="DriveLog" component={DriveLogScreen} />
            <Stack.Screen name="StudentProgress" component={StudentProgressScreen} />
            <Stack.Screen name="GradeStudent" component={GradeStudentScreen} />
            <Stack.Screen name="GradeDiagnosticRide" component={GradeDiagnosticRideScreen} />
            <Stack.Screen name="InstructorProfile" component={InstructorProfileScreen} />
            <Stack.Screen name="BookingFlow" component={BookingFlowScreen} />
            <Stack.Screen name="BookingRequests" component={BookingRequestsScreen} />
            <Stack.Screen name="EditProfile" component={EditProfileScreen} />
            <Stack.Screen name="StudentEditProfile" component={StudentEditProfileScreen} />
            <Stack.Screen name="SessionDetail" component={SessionDetailScreen} />
            <Stack.Screen name="StudentDetailStats" component={StudentDetailStatsScreen} />
            <Stack.Screen name="Notifications" component={NotificationsScreen} />
            <Stack.Screen name="SupervisorHandoff" component={SupervisorHandoffScreen} />
            <Stack.Screen name="DiagnosticRideIntro" component={DiagnosticRideIntroScreen} />
            <Stack.Screen name="DiagnosticRideSetup" component={DiagnosticRideSetupScreen} />
            <Stack.Screen name="DiagnosticRideActive" component={DiagnosticRideActiveScreen} />
            <Stack.Screen name="DiagnosticRideResults" component={DiagnosticRideResultsScreen} />
            <Stack.Screen name="Modules" component={ModulesScreen} />
            <Stack.Screen name="Legal" component={LegalScreen} />
          </>
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