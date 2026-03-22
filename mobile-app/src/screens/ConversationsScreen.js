import React, { useState, useEffect, useContext } from 'react';
import { View, Text, FlatList, TouchableOpacity, StyleSheet, ActivityIndicator } from 'react-native';
import client from '../api/client';
import { SafeAreaView } from 'react-native-safe-area-context';
import { AuthContext } from '../context/AuthContext';
import { useIsFocused } from '@react-navigation/native';

const ConversationsScreen = ({ navigation }) => {
  const { userInfo } = useContext(AuthContext);
  const [conversations, setConversations] = useState([]);
  const [loading, setLoading] = useState(true);
  const isFocused = useIsFocused();

  useEffect(() => {
    if (isFocused) {
      fetchData();
    }
  }, [isFocused]);

  const fetchData = async () => {
    try {
      // 1. Fetch Bookings to resolve names
      const resBookings = await client.get('/bookings/');
      const bookings = resBookings.data;

      // Map UserID -> Name
      const userMap = {};
      bookings.forEach(b => {
        if (b.instructor && b.instructor.user) {
            userMap[b.instructor.user.id] = b.instructor.user.full_name;
        } else if (b.instructor_id) {
             // Fallback if full user obj missing but we know it's an instructor
             if (!userMap[b.instructor_id]) userMap[b.instructor_id] = `Instructor #${b.instructor_id}`;
        }

        if (b.student) {
            userMap[b.student.id] = b.student.full_name;
        } else if (b.student_id) {
             if (!userMap[b.student_id]) userMap[b.student_id] = `Student #${b.student_id}`;
        }
      });

      // 2. Fetch Messages to find active convos
      const resMessages = await client.get('/messages/');
      const messages = resMessages.data;

      // Group by other user
      const convos = {};
      
      messages.forEach(m => {
          const otherId = m.sender_id === userInfo.id ? m.recipient_id : m.sender_id;
          
          if (!convos[otherId]) {
              convos[otherId] = {
                  id: otherId,
                  name: userMap[otherId] || `User #${otherId}`, // Use map or fallback
                  lastMessage: m.content,
                  timestamp: m.timestamp,
                  unreadCount: 0
              };
          }
          
          // Update if newer
          if (new Date(m.timestamp) > new Date(convos[otherId].timestamp)) {
              convos[otherId].lastMessage = m.content;
              convos[otherId].timestamp = m.timestamp;
          }

          // Count unread
          if (m.recipient_id === userInfo.id && !m.is_read) {
              convos[otherId].unreadCount++;
          }
      });

      // Also ensure anyone we have a pending/accepted booking with is in the list, even if no messages yet
      bookings.forEach(b => {
          // Identify the 'other' person
          // If I am student, other is instructor_id (via instructor.user_id if avail)
          // Since we don't have my role easily here (could check userInfo.role), let's just check IDs
          let otherId = null;
          let otherName = null;

          if (userInfo.id === b.student_id) {
              otherId = b.instructor?.user_id || b.instructor_id; // Schema varies slightly in different endpoints, safe check
              otherName = b.instructor?.user?.full_name || `Instructor #${b.instructor_id}`;
          } else if (userInfo.id === b.instructor?.user_id) { // Instructor Profile -> User ID
              otherId = b.student_id;
              otherName = b.student?.full_name || `Student #${b.student_id}`;
          }

          // Note: The booking object structure from /bookings/ depends on backend schema.
          // Based on previous reads: 
          // Student gets bookings with `instructor` relation.
          // Instructor gets bookings with `student` relation.
          // Instructor ID in booking is profile ID, not user ID. Instructor.user.id is needed.
          // Let's assume the map we built earlier `userMap` has the correct User IDs.
          
          // Actually, `userMap` keys were built from `b.instructor.user.id` or `b.student.id`.
          // We need to find the user ID associated with the booking's counterparty.
          
          if (otherId && !convos[otherId]) {
               convos[otherId] = {
                  id: otherId,
                  name: userMap[otherId] || otherName || `User #${otherId}`,
                  lastMessage: "Start a conversation",
                  timestamp: b.date + 'T' + b.time, // Use booking time as fallback sort key
                  unreadCount: 0
              };
          }
      });

      // Sort by timestamp desc
      const sortedConvos = Object.values(convos).sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
      setConversations(sortedConvos);

    } catch (e) {
      console.log("Error fetching conversations", e);
    } finally {
      setLoading(false);
    }
  };

  const renderItem = ({ item }) => (
    <TouchableOpacity 
      style={styles.card} 
      onPress={() => navigation.navigate('Chat', { recipientId: item.id, name: item.name })}
    >
      <View style={styles.avatar}>
        <Text style={styles.avatarText}>{item.name[0]}</Text>
      </View>
      <View style={styles.info}>
        <View style={styles.row}>
            <Text style={[styles.name, item.unreadCount > 0 && styles.bold]}>{item.name}</Text>
            {item.timestamp && <Text style={styles.time}>{new Date(item.timestamp).toLocaleDateString()}</Text>}
        </View>
        <Text style={[styles.lastMessage, item.unreadCount > 0 && styles.boldText]} numberOfLines={1}>
            {item.lastMessage}
        </Text>
      </View>
      {item.unreadCount > 0 && (
          <View style={styles.badge}>
              <Text style={styles.badgeText}>{item.unreadCount}</Text>
          </View>
      )}
    </TouchableOpacity>
  );

  if (loading) return <ActivityIndicator size="large" style={{flex:1}} color="#3B82F6" />;

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <Text style={styles.header}>Messages</Text>
      <FlatList
        data={conversations}
        keyExtractor={item => item.id.toString()}
        renderItem={renderItem}
        ListEmptyComponent={<Text style={styles.empty}>No messages yet.</Text>}
        refreshing={loading}
        onRefresh={fetchData}
      />
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0B1326' },
  header: { fontSize: 24, fontWeight: 'bold', padding: 20, color: '#FFFFFF' },
  card: { flexDirection: 'row', padding: 15, borderBottomWidth: 1, borderBottomColor: '#1E293B', alignItems: 'center' },
  avatar: { width: 50, height: 50, borderRadius: 25, backgroundColor: '#1E293B', justifyContent: 'center', alignItems: 'center', marginRight: 15 },
  avatarText: { color: '#3B82F6', fontSize: 20, fontWeight: 'bold' },
  info: { flex: 1 },
  row: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 },
  name: { fontSize: 16, fontWeight: '600', color: '#FFFFFF' },
  bold: { fontWeight: '800', color: '#FFFFFF' },
  time: { fontSize: 12, color: '#64748B' },
  lastMessage: { color: '#94A3B8', fontSize: 14 },
  boldText: { color: '#FFFFFF', fontWeight: '600' },
  empty: { padding: 20, textAlign: 'center', color: '#64748B', marginTop: 50 },
  badge: { backgroundColor: '#3B82F6', borderRadius: 10, minWidth: 20, height: 20, justifyContent: 'center', alignItems: 'center', paddingHorizontal: 5, marginLeft: 10 },
  badgeText: { color: 'white', fontSize: 10, fontWeight: 'bold' }
});

export default ConversationsScreen;