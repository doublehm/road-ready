import React, { useState, useEffect, useRef, useContext } from 'react';
import { View, Text, FlatList, TextInput, TouchableOpacity, StyleSheet, KeyboardAvoidingView, Platform, ActivityIndicator } from 'react-native';
import client from '../api/client';
import { SafeAreaView } from 'react-native-safe-area-context';
import { AuthContext } from '../context/AuthContext';

const ChatScreen = ({ route, navigation }) => {
  const { recipientId, name } = route.params;
  const { fetchUpdates } = useContext(AuthContext);
  const [messages, setMessages] = useState([]);
  const [inputText, setInputText] = useState('');
  const [loading, setLoading] = useState(true);
  const flatListRef = useRef();

  useEffect(() => {
    markAsRead();
    fetchMessages();
    const interval = setInterval(() => {
        fetchMessages();
        markAsRead();
    }, 5000); // Polling every 5s
    return () => clearInterval(interval);
  }, []);

  const markAsRead = async () => {
      try {
          await client.put(`/messages/${recipientId}/read`);
          fetchUpdates(); // Refresh global badge
      } catch (e) {
          // ignore
      }
  };

  const fetchMessages = async () => {
    try {
      // Fetch all messages (prototype inefficiency: filtering client side)
      // Real app: /messages?recipient_id=X
      const response = await client.get('/messages/'); // Assuming this endpoint exists and lists my messages
      
      // Filter relevant messages
      const myMessages = response.data.filter(m => 
        (m.sender_id === recipientId) || (m.recipient_id === recipientId)
      );
      
      // Sort by timestamp
      myMessages.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));
      
      setMessages(myMessages);
    } catch (e) {
      console.log("Error fetching messages", e);
    } finally {
      setLoading(false);
    }
  };

  const sendMessage = async () => {
    if (!inputText.trim()) return;
    
    const newMsg = {
      recipient_id: recipientId,
      content: inputText,
      timestamp: new Date().toISOString() // Optimistic update
    };

    try {
      // Optimistic UI update
      setMessages([...messages, { ...newMsg, sender_id: 'me', id: Math.random() }]);
      setInputText('');
      
      await client.post('/messages/', newMsg);
      fetchMessages(); // Refresh to get real ID/timestamp
    } catch (e) {
      console.error("Send failed", e);
    }
  };

  const renderItem = ({ item }) => {
    const isMe = item.sender_id !== recipientId; // Simplified check
    return (
      <View style={[
        styles.bubble, 
        isMe ? styles.myBubble : styles.theirBubble
      ]}>
        <Text style={[styles.text, isMe ? styles.myText : styles.theirText]}>
          {item.content}
        </Text>
      </View>
    );
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Text style={styles.backText}>←</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{name}</Text>
      </View>

      {loading ? (
        <ActivityIndicator size="large" style={{flex:1}} />
      ) : (
        <FlatList
          ref={flatListRef}
          data={messages}
          keyExtractor={item => item.id?.toString() || Math.random().toString()}
          renderItem={renderItem}
          contentContainerStyle={styles.list}
          onContentSizeChange={() => flatListRef.current?.scrollToEnd()}
        />
      )}

      <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : "height"}>
        <View style={styles.inputContainer}>
          <TextInput
            style={styles.input}
            value={inputText}
            onChangeText={setInputText}
            placeholder="Type a message..."
          />
          <TouchableOpacity style={styles.sendBtn} onPress={sendMessage}>
            <Text style={styles.sendText}>Send</Text>
          </TouchableOpacity>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f5f5f5' },
  header: { flexDirection: 'row', alignItems: 'center', padding: 15, backgroundColor: 'white', borderBottomWidth: 1, borderBottomColor: '#ddd' },
  backBtn: { marginRight: 15 },
  backText: { fontSize: 24, color: '#007bff' },
  headerTitle: { fontSize: 18, fontWeight: 'bold' },
  list: { padding: 15 },
  bubble: { maxWidth: '80%', padding: 10, borderRadius: 15, marginBottom: 10 },
  myBubble: { alignSelf: 'flex-end', backgroundColor: '#007bff' },
  theirBubble: { alignSelf: 'flex-start', backgroundColor: '#e5e5ea' },
  text: { fontSize: 16 },
  myText: { color: 'white' },
  theirText: { color: 'black' },
  inputContainer: { flexDirection: 'row', padding: 10, backgroundColor: 'white', alignItems: 'center' },
  input: { flex: 1, backgroundColor: '#f0f0f0', borderRadius: 20, paddingHorizontal: 15, paddingVertical: 10, marginRight: 10 },
  sendBtn: { padding: 10 },
  sendText: { color: '#007bff', fontWeight: 'bold', fontSize: 16 }
});

export default ChatScreen;
