import React, { useState, useEffect, useContext } from 'react';
import { View, Text, StyleSheet, FlatList, TouchableOpacity, ActivityIndicator, Modal, ScrollView } from 'react-native';
import client from '../api/client';
import { SafeAreaView } from 'react-native-safe-area-context';
import Ionicons from '@expo/vector-icons/Ionicons';
import { AuthContext } from '../context/AuthContext';

const NotificationsScreen = ({ navigation }) => {
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedNotif, setSelectedNotif] = useState(null);

  useEffect(() => {
    fetchNotifications();
  }, []);

  const fetchNotifications = async () => {
    try {
      const response = await client.get('/notifications/');
      setNotifications(response.data);
    } catch (e) {
      console.log(e);
    } finally {
      setLoading(false);
    }
  };

  const handlePress = async (item) => {
    if (!item.is_read) {
        await client.post(`/notifications/${item.id}/read`);
        // Update local state
        setNotifications(prev => prev.map(n => n.id === item.id ? {...n, is_read: true} : n));
    }
    setSelectedNotif(item);
  };

  const renderItem = ({ item }) => (
    <TouchableOpacity 
      style={[styles.card, !item.is_read && styles.unread]}
      onPress={() => handlePress(item)}
    >
      <View style={styles.iconBox}>
        <Ionicons 
            name={item.title.includes('Booking') ? "calendar" : "notifications"} 
            size={24} 
            color={!item.is_read ? "#3B82F6" : "#94A3B8"} 
        />
      </View>
      <View style={styles.content}>
        <Text style={[styles.title, !item.is_read && styles.bold]}>{item.title}</Text>
        <Text style={styles.message} numberOfLines={2}>{item.message}</Text>
        <Text style={styles.time}>{new Date(item.timestamp).toLocaleString()}</Text>
      </View>
      {!item.is_read && <View style={styles.dot} />}
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Ionicons name="arrow-back" size={24} color="#FFFFFF" />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Notifications</Text>
        <View style={{width: 24}} />
      </View>

      {loading ? (
        <ActivityIndicator style={{marginTop: 50}} />
      ) : (
        <FlatList
          data={notifications}
          keyExtractor={item => item.id.toString()}
          renderItem={renderItem}
          contentContainerStyle={styles.list}
          ListEmptyComponent={<Text style={styles.empty}>No notifications.</Text>}
        />
      )}

      {/* Detail Modal */}
      <Modal visible={!!selectedNotif} transparent animationType="fade">
          <View style={styles.modalOverlay}>
              <View style={styles.modalContent}>
                  <Text style={styles.modalTitle}>{selectedNotif?.title}</Text>
                  <ScrollView style={{maxHeight: 300}}>
                    <Text style={styles.modalBody}>{selectedNotif?.message}</Text>
                  </ScrollView>
                  <Text style={styles.modalTime}>{selectedNotif && new Date(selectedNotif.timestamp).toLocaleString()}</Text>
                  <TouchableOpacity 
                    style={styles.closeBtn} 
                    onPress={() => setSelectedNotif(null)}
                  >
                      <Text style={styles.closeText}>Close</Text>
                  </TouchableOpacity>
              </View>
          </View>
      </Modal>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0B1326' },
  header: { 
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', 
    padding: 15, backgroundColor: '#131B2E', borderBottomWidth: 1, borderBottomColor: '#1E293B' 
  },
  headerTitle: { fontSize: 18, fontWeight: 'bold', color: '#FFFFFF' },
  list: { padding: 15 },
  
  card: {
    backgroundColor: '#131B2E', flexDirection: 'row', padding: 15, borderRadius: 12, marginBottom: 10,
    alignItems: 'center'
  },
  unread: { backgroundColor: 'rgba(59,130,246,0.15)' },
  iconBox: { marginRight: 15 },
  content: { flex: 1 },
  title: { fontSize: 16, color: '#FFFFFF', marginBottom: 2 },
  bold: { fontWeight: 'bold' },
  message: { fontSize: 14, color: '#94A3B8', marginBottom: 5 },
  time: { fontSize: 12, color: '#64748B' },
  dot: { width: 10, height: 10, borderRadius: 5, backgroundColor: '#3B82F6', marginLeft: 10 },
  
  empty: { textAlign: 'center', marginTop: 50, color: '#64748B' },

  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center', padding: 30 },
  modalContent: { backgroundColor: '#131B2E', borderRadius: 15, padding: 25 },
  modalTitle: { fontSize: 20, fontWeight: 'bold', marginBottom: 15, color: '#FFFFFF' },
  modalBody: { fontSize: 16, color: '#CBD5E1', lineHeight: 24, marginBottom: 20 },
  modalTime: { fontSize: 12, color: '#64748B', marginBottom: 20, fontStyle: 'italic' },
  closeBtn: { backgroundColor: '#3B82F6', padding: 12, borderRadius: 10, alignItems: 'center' },
  closeText: { color: 'white', fontWeight: 'bold' }
});

export default NotificationsScreen;
