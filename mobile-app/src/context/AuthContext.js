import React, { createContext, useState, useEffect } from 'react';
import * as SecureStore from 'expo-secure-store';
import client from '../api/client';

export const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [isLoading, setIsLoading] = useState(true);
  const [userToken, setUserToken] = useState(null);
  const [userRole, setUserRole] = useState(null);

  const login = async (email, password) => {
    setIsLoading(true);
    try {
      const response = await client.post('/auth/login', 
        new URLSearchParams({
          username: email,
          password: password,
        }), {
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
        }
      );
      
      const { access_token, role } = response.data;
      setUserToken(access_token);
      setUserRole(role);
      
      await SecureStore.setItemAsync('userToken', access_token);
      await SecureStore.setItemAsync('userRole', role);
    } catch (e) {
      console.log("Login error", e);
      throw e;
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async () => {
    setIsLoading(true);
    setUserToken(null);
    setUserRole(null);
    await SecureStore.deleteItemAsync('userToken');
    await SecureStore.deleteItemAsync('userRole');
    setIsLoading(false);
  };

  const isLoggedIn = async () => {
    try {
      setIsLoading(true);
      let userToken = await SecureStore.getItemAsync('userToken');
      let userRole = await SecureStore.getItemAsync('userRole');
      setUserToken(userToken);
      setUserRole(userRole);
    } catch (e) {
      console.log("Restore token error", e);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    isLoggedIn();
  }, []);

  return (
    <AuthContext.Provider value={{ login, logout, isLoading, userToken, userRole }}>
      {children}
    </AuthContext.Provider>
  );
};
