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
              <Ionicons name="shield-checkmark" size={18} color="#15803D" style={{marginLeft: 6}} />
            )}
          </View>
          <Text style={styles.city}>
            <Ionicons name="location-sharp" size={14} color="#64748B" /> {item.city}
          </Text>
        </View>
        <View style={styles.rating}>
          <Ionicons name="star" size={12} color="#D97706" />
          <Text style={styles.ratingText}>{item.rating || 'New'}</Text>
        </View>
      </View>
      
      <View style={styles.cardFooter}>
        <View>
          <Text style={styles.priceLabel}>HOURLY RATE</Text>
          <Text style={styles.price}>${item.hourly_rate}<Text style={styles.perHour}>/hr</Text></Text>
        </View>
        <TouchableOpacity 
          style={styles.viewBtn}
          onPress={() => navigation.navigate('InstructorProfile', { instructor: item, forDiagnosticRide })}
        >
          <Text style={styles.viewBtnText}>View Profile</Text>
        </TouchableOpacity>
      </View>
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Find Instructor</Text>
        <Text style={styles.headerSubtitle}>AVAILABLE NEAR YOU</Text>
      </View>

      <View style={styles.searchContainer}>
        <Ionicons name="search" size={20} color="#94A3B8" style={styles.searchIcon} />
        <TextInput
          style={styles.searchInput}
          placeholder="Search by city or name..."
          placeholderTextColor="#94A3B8"
          value={search}
          onChangeText={setSearch}
        />
      </View>

      {loading ? (
        <ActivityIndicator style={{marginTop: 50}} size="large" color="#1E293B" />
      ) : (
        <FlatList
          data={filteredInstructors}
          keyExtractor={item => item.id.toString()}
          renderItem={renderItem}
          contentContainerStyle={styles.list}
          ListEmptyComponent={
            <View style={styles.emptyContainer}>
              <Ionicons name="search-outline" size={48} color="#CBD5E1" />
              <Text style={styles.empty}>No instructors found in this sector.</Text>
            </View>
          }
          showsVerticalScrollIndicator={false}
        />
      )}
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F6FAFE' },
  header: { paddingHorizontal: 20, paddingTop: 20, marginBottom: 20 },
  headerTitle: { fontSize: 28, fontWeight: '800', color: '#1E293B', letterSpacing: -1 },
  headerSubtitle: { fontSize: 11, fontWeight: '800', color: '#15803D', letterSpacing: 2, marginTop: 4 },
  searchContainer: { 
    flexDirection: 'row', alignItems: 'center', backgroundColor: 'white', 
    marginHorizontal: 20, marginBottom: 24, paddingHorizontal: 20, borderRadius: 16, height: 56,
    shadowColor: '#1E293B', shadowOpacity: 0.06, shadowRadius: 15, elevation: 4
  },
  searchIcon: { marginRight: 12 },
  searchInput: { flex: 1, fontSize: 16, color: '#1E293B', fontWeight: '500' },
  
  list: { paddingHorizontal: 20, paddingBottom: 40 },
  card: {
    backgroundColor: 'white', borderRadius: 24, padding: 24, marginBottom: 20,
    shadowColor: '#1E293B', shadowOpacity: 0.06, shadowRadius: 20, elevation: 4
  },
  cardHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: 20 },
  avatar: { width: 56, height: 56, borderRadius: 20, backgroundColor: '#F1F5F9', justifyContent: 'center', alignItems: 'center' },
  avatarText: { fontSize: 22, fontWeight: '800', color: '#1E293B' },
  cardInfo: { flex: 1, marginLeft: 16 },
  name: { fontSize: 18, fontWeight: '800', color: '#1E293B', letterSpacing: -0.5 },
  city: { color: '#64748B', marginTop: 2, fontSize: 14, fontWeight: '500' },
  rating: { 
    flexDirection: 'row', alignItems: 'center', gap: 4,
    backgroundColor: '#FEF3C7', paddingVertical: 6, paddingHorizontal: 10, borderRadius: 12 
  },
  ratingText: { fontWeight: '800', color: '#D97706', fontSize: 13 },
  
  cardFooter: { 
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end', 
    paddingTop: 20, borderTopWidth: 1, borderTopColor: '#F1F5F9' 
  },
  priceLabel: { fontSize: 10, fontWeight: '800', color: '#94A3B8', letterSpacing: 1, marginBottom: 2 },
  price: { fontSize: 24, fontWeight: '800', color: '#15803D', letterSpacing: -1 },
  perHour: { fontSize: 14, color: '#94A3B8', fontWeight: '600' },
  viewBtn: { 
    backgroundColor: '#1E293B', paddingVertical: 10, paddingHorizontal: 16, borderRadius: 12 
  },
  viewBtnText: { color: 'white', fontWeight: '800', fontSize: 14 },
  
  emptyContainer: { alignItems: 'center', marginTop: 60 },
  empty: { textAlign: 'center', marginTop: 16, color: '#94A3B8', fontWeight: '600', fontSize: 15 }
});

export default FindInstructorScreen;