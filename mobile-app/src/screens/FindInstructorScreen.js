import React, { useState, useEffect } from 'react';
import { View, Text, FlatList, StyleSheet, TouchableOpacity, TextInput, ActivityIndicator } from 'react-native';
import client from '../api/client';
import Ionicons from '@expo/vector-icons/Ionicons';
import { SafeAreaView } from 'react-native-safe-area-context';

const FindInstructorScreen = ({ navigation, route }) => {
  const forDiagnosticRide = route.params?.forDiagnosticRide;
  const [instructors, setInstructors] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');

  useEffect(() => {
    fetchInstructors();
  }, []);

  const fetchInstructors = async () => {
    try {
      const response = await client.get('/instructors/');
      setInstructors(response.data);
    } catch (e) {
      console.log(e);
    } finally {
      setLoading(false);
    }
  };

  const filteredInstructors = instructors.filter(i => 
    i.city.toLowerCase().includes(search.toLowerCase()) || 
    i.user?.full_name?.toLowerCase().includes(search.toLowerCase())
  );

  const renderItem = ({ item }) => (
    <TouchableOpacity 
      style={styles.card}
      onPress={() => navigation.navigate('InstructorProfile', { instructor: item, forDiagnosticRide })}
    >
      <View style={styles.cardHeader}>
        <View style={styles.avatar}>
          <Text style={styles.avatarText}>{item.user?.full_name?.[0]}</Text>
        </View>
        <View style={styles.cardInfo}>
          <View style={{flexDirection: 'row', alignItems: 'center'}}>
            <Text style={styles.name}>{item.user?.full_name}</Text>
            {item.is_verified && (
              <Ionicons name="checkmark-circle" size={16} color="#007bff" style={{marginLeft: 5}} />
            )}
          </View>
          <Text style={styles.city}><Ionicons name="location-outline" /> {item.city}</Text>
        </View>
        <View style={styles.rating}>
          <Text style={styles.ratingText}>⭐ {item.rating || 'New'}</Text>
        </View>
      </View>
      
      <View style={styles.cardFooter}>
        <Text style={styles.price}>${item.hourly_rate}<Text style={styles.perHour}>/hr</Text></Text>
        <Text style={styles.viewLink}>View Profile →</Text>
      </View>
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.searchContainer}>
        <Ionicons name="search" size={20} color="#666" style={styles.searchIcon} />
        <TextInput
          style={styles.searchInput}
          placeholder="Search by city or name..."
          value={search}
          onChangeText={setSearch}
        />
      </View>

      {loading ? (
        <ActivityIndicator style={{marginTop: 50}} size="large" />
      ) : (
        <FlatList
          data={filteredInstructors}
          keyExtractor={item => item.id.toString()}
          renderItem={renderItem}
          contentContainerStyle={styles.list}
          ListEmptyComponent={<Text style={styles.empty}>No instructors found.</Text>}
        />
      )}
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f5f5f5' },
  searchContainer: { 
    flexDirection: 'row', alignItems: 'center', backgroundColor: 'white', 
    margin: 15, paddingHorizontal: 15, borderRadius: 10, height: 50,
    shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 5, elevation: 2
  },
  searchIcon: { marginRight: 10 },
  searchInput: { flex: 1, fontSize: 16 },
  
  list: { paddingHorizontal: 15 },
  card: {
    backgroundColor: 'white', borderRadius: 12, padding: 15, marginBottom: 15,
    shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 5, elevation: 2
  },
  cardHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: 10 },
  avatar: { width: 50, height: 50, borderRadius: 25, backgroundColor: '#e3f2fd', justifyContent: 'center', alignItems: 'center' },
  avatarText: { fontSize: 20, fontWeight: 'bold', color: '#007bff' },
  cardInfo: { flex: 1, marginLeft: 15 },
  name: { fontSize: 16, fontWeight: 'bold', color: '#333' },
  city: { color: '#666', marginTop: 2 },
  rating: { backgroundColor: '#fff3cd', paddingVertical: 4, paddingHorizontal: 8, borderRadius: 10 },
  ratingText: { fontWeight: 'bold', color: '#856404', fontSize: 12 },
  
  cardFooter: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', borderTopWidth: 1, borderTopColor: '#f0f0f0', paddingTop: 10 },
  price: { fontSize: 18, fontWeight: 'bold', color: '#28a745' },
  perHour: { fontSize: 12, color: '#666', fontWeight: 'normal' },
  viewLink: { color: '#007bff', fontWeight: '600' },
  
  empty: { textAlign: 'center', marginTop: 50, color: '#999' }
});

export default FindInstructorScreen;