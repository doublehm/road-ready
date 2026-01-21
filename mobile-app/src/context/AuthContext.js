import React, { createContext, useState, useEffect } from 'react';
import * as SecureStore from 'expo-secure-store';
import client from '../api/client';

export const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [isLoading, setIsLoading] = useState(true);
  const [userToken, setUserToken] = useState(null);
  const [userRole, setUserRole] = useState(null);
  const [userInfo, setUserInfo] = useState(null);
  const [unreadCount, setUnreadCount] = useState(0); // Notifications
  const [unreadMessageCount, setUnreadMessageCount] = useState(0); // Messages

  const fetchUser = async (token) => {
      try {
          const res = await client.get('/users/me', {
              headers: { Authorization: `Bearer ${token}` }
          });
          setUserInfo(res.data);
          return res.data;
      } catch (e) {
          console.log("Error fetching user", e);
      }
  };

  const fetchUpdates = async () => {
      try {
          // 1. Notifications
          const resNotifs = await client.get('/notifications/');
          const unreadNotifs = resNotifs.data.filter(n => !n.is_read).length;
          setUnreadCount(unreadNotifs);

          // 2. Messages
          // We need userInfo to know which messages are for me. 
          // If userInfo is null (race condition), skip or rely on backend filtering if it existed.
          // Since /messages/ returns both sent and received, we can't blindly count all unread.
          // However, we can fetch user profile if missing, or just rely on the stored state.
          if (userInfo) {
              const resMsgs = await client.get('/messages/');
              const unreadMsgs = resMsgs.data.filter(m => m.recipient_id === userInfo.id && !m.is_read).length;
              setUnreadMessageCount(unreadMsgs);
          }
      } catch (e) {
          // ignore
      }
  };

  useEffect(() => {
      if (userToken) {
          fetchUpdates();
          const interval = setInterval(fetchUpdates, 10000); // Poll every 10s (faster for chat)
          return () => clearInterval(interval);
      }
  }, [userToken, userInfo]); // Depend on userInfo so we fetch messages once user is loaded

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

      await fetchUser(access_token);
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
    setUserInfo(null);
    await SecureStore.deleteItemAsync('userToken');
    await SecureStore.deleteItemAsync('userRole');
    setIsLoading(false);
  };

  const isLoggedIn = async () => {
    try {
      setIsLoading(true);
      let userToken = await SecureStore.getItemAsync('userToken');
      let userRole = await SecureStore.getItemAsync('userRole');
      
      if (userToken) {
          setUserToken(userToken);
          setUserRole(userRole);
          await fetchUser(userToken);
      }
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
    <AuthContext.Provider value={{ 
        login, 
        logout, 
        isLoading, 
        userToken, 
        userRole, 
        userInfo, 
        setUserInfo, 
        fetchUser, 
        unreadCount, 
        unreadMessageCount,
        fetchUpdates 
    }}>
      {children}
    </AuthContext.Provider>
  );
};
