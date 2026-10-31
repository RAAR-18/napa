import React, { useState } from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { AuthProvider, useAuth } from './src/context/AuthContext';
import { HomeScreen } from './src/screens/HomeScreen';
import { LoginScreen } from './src/screens/LoginScreen';
import { RegisterScreen } from './src/screens/RegisterScreen';
import { ProfileScreen } from './src/screens/ProfileScreen';
import { CreateBusinessScreen } from './src/screens/CreateBusinessScreen';
import { BusinessesScreen } from './src/screens/BusinessesScreen';
import { CreateProductScreen } from './src/screens/CreateProductScreen';

function MainNavigator() {
  const { user, isInitializing } = useAuth();
  const [currentScreen, setCurrentScreen] = useState<'login' | 'register' | 'home' | 'profile' | 'create-business' | 'businesses' | 'create-product'>('login');

  // Mientras se restaura la sesión guardada, mostramos una pantalla de carga
  if (isInitializing) {
    return (
      <View style={styles.splash}>
        <Text style={styles.splashTitle}>Ñapa</Text>
        <ActivityIndicator color="#1D5A46" size="large" style={{ marginTop: 24 }} />
      </View>
    );
  }

  if (user) {
    if (currentScreen === 'profile') {
      return <ProfileScreen onGoBack={() => setCurrentScreen('home')} />;
    }
    if (currentScreen === 'create-business') {
      return (
        <CreateBusinessScreen
          onGoBack={() => setCurrentScreen('home')}
          onCreated={() => setCurrentScreen('home')}
        />
      );
    }
    if (currentScreen === 'businesses') {
      return <BusinessesScreen onGoBack={() => setCurrentScreen('home')} />;
    }
    if (currentScreen === 'create-product') {
      return (
        <CreateProductScreen
          onGoBack={() => setCurrentScreen('home')}
          onCreated={() => setCurrentScreen('home')}
        />
      );
    }
    return (
      <HomeScreen
        onOpenProfile={() => setCurrentScreen('profile')}
        onCreateBusiness={() => setCurrentScreen('create-business')}
        onCreateProduct={() => setCurrentScreen('create-product')}
        onOpenBusinesses={() => setCurrentScreen('businesses')}
      />
    );
  }

  if (currentScreen === 'register') {
    return <RegisterScreen onGoToLogin={() => setCurrentScreen('login')} />;
  }

  return <LoginScreen onGoToRegister={() => setCurrentScreen('register')} />;
}

const styles = StyleSheet.create({
  splash: {
    flex: 1,
    backgroundColor: '#F8FAFC',
    justifyContent: 'center',
    alignItems: 'center',
  },
  splashTitle: {
    fontSize: 48,
    fontWeight: '800',
    color: '#1D5A46',
    letterSpacing: -1,
  },
});

export default function App() {
  return (
    <AuthProvider>
      <MainNavigator />
      <StatusBar style="auto" />
    </AuthProvider>
  );
}
