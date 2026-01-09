import axios from 'axios';
import * as SecureStore from 'expo-secure-store';

// Android Emulator uses 10.0.2.2 for localhost
// For physical device, change this to your machine's local IP (e.g., http://192.168.1.5:8000/api/v1)
const BASE_URL = 'http://10.0.2.2:8000/api/v1';

const client = axios.create({
  baseURL: BASE_URL,
});

client.interceptors.request.use(async (config) => {
  const token = await SecureStore.getItemAsync('userToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export default client;
