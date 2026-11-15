import { useEffect } from 'react';
import * as Notifications from 'expo-notifications';
import { Stack, router } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { Platform } from 'react-native';

import { useProcesarOutbox } from '../hooks/useOutbox';
import { configurarAvisosEnPrimerPlano } from '../lib/push';

configurarAvisosEnPrimerPlano();

export default function RootLayout() {
  // F041: las acciones guardadas sin conexión se envían solas cuando vuelve la señal.
  useProcesarOutbox();
  useEffect(() => {
    if (Platform.OS === 'web') return undefined;
    // Tocar un aviso push abre la lista de notificaciones (RF-38).
    const subscription = Notifications.addNotificationResponseReceivedListener(() => {
      router.push('/notificaciones');
    });
    return () => subscription.remove();
  }, []);

  return (
    <>
      <StatusBar style="dark" />
      <Stack screenOptions={{ headerShown: false, animation: 'fade' }} />
    </>
  );
}
