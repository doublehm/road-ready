import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, FlatList, TouchableOpacity, Alert, ActivityIndicator, Modal, TextInput, Image } from 'react-native';
import client from '../api/client';
import { SafeAreaView } from 'react-native-safe-area-context';
import Ionicons from 'react-native-vector-icons/Ionicons';

const BookingRequestsScreen = ({ navigation }) => {
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  
  // Rejection Modal State
  const [rejectModalVisible, setRejectModalVisible] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [selectedBookingId, setSelectedBookingId] = useState(null);

  // License Modal State
  const [imageModalVisible, setImageModalVisible] = useState(false);
  const [selectedImage, setSelectedImage] = useState(null);

  useEffect(() => {
    fetchRequests();
  }, []);

  const fetchRequests = async () => {
    try {
      const response = await client.get('/bookings/');
      const pending = response.data.filter(b => b.status === 'pending' || b.status === 'pending_payment');
      setRequests(pending);
    } catch (e) {
      console.log(e);
    } finally {
      setLoading(false);
    }
  };

  const openRejectModal = (id) => {
    setSelectedBookingId(id);
    setRejectReason('');
    setRejectModalVisible(true);
  };

  const confirmReject = async () => {
    if (!selectedBookingId) return;
    try {
      await client.post(`/bookings/${selectedBookingId}/action`, {
          action: 'reject',
          reason: rejectReason
      });
      Alert.alert('Success', 'Booking rejected.');
      setRejectModalVisible(false);
      fetchRequests();
    } catch (e) {
      Alert.alert('Error', 'Failed to reject.');
    }
  };

  const handleAccept = async (id) => {
    try {
      await client.post(`/bookings/${id}/action`, { action: 'accept' });
      Alert.alert('Success', 'Booking accepted.');
      fetchRequests();
    } catch (e) {
      Alert.alert('Error', 'Failed to accept.');
    }
  };

  const viewLicense = (imgUrl) => {
      setSelectedImage(imgUrl);
      setImageModalVisible(true);
  };

  const renderItem = ({ item }) => {
    const today = new Date();
    const expiry = item.student?.student_profile?.license_expiry;
    let isExpired = false;
    if (expiry) {
        isExpired = new Date(expiry) < today;
    }

    const licenseImg = item.student?.student_profile?.license_image;
    // Construct full URL. Adjust IP if needed.
    const imageUrl = licenseImg ? `http://192.168.1.235:8000/static/uploads/${licenseImg}` : null;

    return (
    <View style={styles.card}>
      <View style={styles.header}>
        <Text style={styles.studentName}>{item.student?.full_name || `Student #${item.student_id}`}</Text>
        <Text style={styles.price}>${item.total_amount}</Text>
      </View>
      
      <View style={styles.details}>
        <Text style={styles.detailText}><Ionicons name="calendar" /> {item.date}</Text>
        <Text style={styles.detailText}><Ionicons name="time" /> {item.time}</Text>
        <Text style={styles.detailText}><Ionicons name="card" /> Exp: {expiry || 'N/A'}</Text>
        
        {isExpired && <Text style={styles.expiredText}>⚠️ LICENSE EXPIRED</Text>}

        {imageUrl ? (
            <TouchableOpacity onPress={() => viewLicense(imageUrl)} style={styles.viewLicenseBtn}>
                <Ionicons name="image" size={16} color="white" />
                <Text style={{color: 'white', marginLeft: 5}}>View License</Text>
            </TouchableOpacity>
        ) : (
            <Text style={{color:'#999', fontStyle:'italic'}}>No license photo</Text>
        )}

        <TouchableOpacity 
            onPress={() => navigation.navigate('Chat', { 
                recipientId: item.student_id, 
                name: item.student?.full_name || 'Student' 
            })} 
            style={styles.messageBtn}
        >
            <Ionicons name="chatbubble-ellipses" size={16} color="white" />
            <Text style={{color: 'white', marginLeft: 5}}>Message Student</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.actions}>
        <TouchableOpacity 
          style={[styles.btn, styles.rejectBtn]} 
          onPress={() => openRejectModal(item.id)}
        >
          <Text style={styles.btnTextReject}>Reject</Text>
        </TouchableOpacity>
        
        <TouchableOpacity 
          style={[styles.btn, styles.acceptBtn, isExpired && {backgroundColor: '#ccc'}]} 
          onPress={() => handleAccept(item.id)}
          disabled={isExpired}
        >
          <Text style={styles.btnTextAccept}>Accept</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <Text style={styles.screenTitle}>Booking Requests</Text>
      
      {loading ? (
        <ActivityIndicator style={{marginTop: 50}} size="large" />
      ) : (
        <FlatList
          data={requests}
          keyExtractor={item => item.id.toString()}
          renderItem={renderItem}
          contentContainerStyle={styles.list}
          ListEmptyComponent={<Text style={styles.empty}>No pending requests.</Text>}
        />
      )}

      {/* Reject Modal */}
      <Modal visible={rejectModalVisible} transparent animationType="slide">
          <View style={styles.modalOverlay}>
              <View style={styles.modalContent}>
                  <Text style={styles.modalTitle}>Reject Booking</Text>
                  <Text style={styles.modalSub}>Reason for rejection:</Text>
                  <TextInput 
                    style={styles.modalInput} 
                    placeholder="e.g. Schedule conflict..." 
                    value={rejectReason}
                    onChangeText={setRejectReason}
                  />
                  <View style={styles.modalActions}>
                      <TouchableOpacity onPress={() => setRejectModalVisible(false)} style={styles.modalBtnCancel}>
                          <Text>Cancel</Text>
                      </TouchableOpacity>
                      <TouchableOpacity onPress={confirmReject} style={styles.modalBtnConfirm}>
                          <Text style={{color:'white'}}>Reject</Text>
                      </TouchableOpacity>
                  </View>
              </View>
          </View>
      </Modal>

      {/* Image Viewer Modal */}
      <Modal visible={imageModalVisible} transparent animationType="fade">
          <View style={styles.imageModal}>
              <TouchableOpacity style={styles.closeBtn} onPress={() => setImageModalVisible(false)}>
                  <Ionicons name="close-circle" size={40} color="white" />
              </TouchableOpacity>
              {selectedImage && (
                  <Image source={{ uri: selectedImage }} style={styles.fullImage} resizeMode="contain" />
              )}
          </View>
      </Modal>

    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f5f5f5' },
  screenTitle: { fontSize: 22, fontWeight: 'bold', padding: 20, color: '#333' },
  list: { paddingHorizontal: 15 },
  card: {
    backgroundColor: 'white', borderRadius: 12, padding: 15, marginBottom: 15,
    shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 5, elevation: 2
  },
  header: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 10 },
  studentName: { fontSize: 18, fontWeight: 'bold', color: '#333' },
  price: { fontSize: 18, fontWeight: 'bold', color: '#28a745' },
  
  details: { marginBottom: 15 },
  detailText: { fontSize: 15, color: '#555', marginBottom: 5 },
  expiredText: { color: 'red', fontWeight: 'bold', marginBottom: 5 },
  viewLicenseBtn: { 
      flexDirection: 'row', alignItems: 'center', backgroundColor: '#6c757d', 
      padding: 8, borderRadius: 5, alignSelf: 'flex-start', marginTop: 5 
  },
  messageBtn: { 
      flexDirection: 'row', alignItems: 'center', backgroundColor: '#007bff', 
      padding: 8, borderRadius: 5, alignSelf: 'flex-start', marginTop: 5 
  },
  
  actions: { flexDirection: 'row', justifyContent: 'space-between' },
  btn: { flex: 1, padding: 12, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
  rejectBtn: { backgroundColor: '#fff', borderWidth: 1, borderColor: '#dc3545', marginRight: 10 },
  acceptBtn: { backgroundColor: '#28a745' },
  
  btnTextReject: { color: '#dc3545', fontWeight: 'bold' },
  btnTextAccept: { color: 'white', fontWeight: 'bold' },
  
  empty: { textAlign: 'center', marginTop: 50, color: '#999' },

  // Modal Styles
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center', padding: 20 },
  modalContent: { backgroundColor: 'white', borderRadius: 15, padding: 20 },
  modalTitle: { fontSize: 20, fontWeight: 'bold', marginBottom: 10 },
  modalSub: { marginBottom: 10, color: '#666' },
  modalInput: { borderWidth: 1, borderColor: '#ddd', borderRadius: 8, padding: 10, minHeight: 80, textAlignVertical: 'top', marginBottom: 20 },
  modalActions: { flexDirection: 'row', justifyContent: 'flex-end' },
  modalBtnCancel: { padding: 10, marginRight: 15 },
  modalBtnConfirm: { backgroundColor: '#dc3545', paddingVertical: 10, paddingHorizontal: 20, borderRadius: 8 },

  // Image Modal
  imageModal: { flex: 1, backgroundColor: 'black', justifyContent: 'center', alignItems: 'center' },
  closeBtn: { position: 'absolute', top: 40, right: 20, zIndex: 10 },
  fullImage: { width: '100%', height: '80%' }
});

export default BookingRequestsScreen;