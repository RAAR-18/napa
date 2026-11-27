import '../lib/ubicacion-segundo-plano';
import { AvisosNovedades } from '../components/AvisosNovedades';
import { useUbicacionAutomatica } from '../lib/ubicacion-automatica';
import { useEffect } from 'react';
import * as Notifications from 'expo-notifications';
import { Stack, router } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { Platform } from 'react-native';
import { AppFonts } from '../components/AppFonts';

import { useProcesarOutbox } from '../hooks/useOutbox';
import { useRegistroPush } from '../hooks/useRegistroPush';
import { configurarAvisosEnPrimerPlano } from '../lib/push';
import { NapaNavigationProvider } from '../components/AppShell';

configurarAvisosEnPrimerPlano();

export default function RootLayout() {
  // F041: las acciones guardadas sin conexión se envían solas cuando vuelve la señal.
  useProcesarOutbox();
  useUbicacionAutomatica();
  // RF-38: el celular queda registrado para push en cuanto hay sesión, en cualquier pantalla.
  useRegistroPush();
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
      <AppFonts>
        <NapaNavigationProvider>
          <Stack screenOptions={{ headerShown: false, animation: 'fade' }} />
          <AvisosNovedades />
        </NapaNavigationProvider>
      </AppFonts>
    </>
  );
}
