import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';

import { darDeBajaDispositivo, registrarDispositivo } from './notificaciones';

/** Con la app abierta el aviso también se muestra; la lista de la app es la fuente de verdad. */
export function configurarAvisosEnPrimerPlano() {
  if (Platform.OS === 'web') return;
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: true,
      shouldSetBadge: false,
    }),
  });
}

function plataforma(): 'ANDROID' | 'IOS' | null {
  if (Platform.OS === 'android') return 'ANDROID';
  if (Platform.OS === 'ios') return 'IOS';
  return null;
}

/** Token FCM nativo; null si no hay permiso, en web o si el build no tiene Firebase configurado. */
async function tokenDelDispositivo(pedirPermiso: boolean): Promise<string | null> {
  if (!plataforma()) return null;
  try {
    let { status } = await Notifications.getPermissionsAsync();
    if (status !== 'granted' && pedirPermiso) {
      ({ status } = await Notifications.requestPermissionsAsync());
    }
    if (status !== 'granted') return null;
    const token = await Notifications.getDevicePushTokenAsync();
    return typeof token.data === 'string' && token.data ? token.data : null;
  } catch {
    // Expo Go y los builds sin google-services.json no entregan token: la lista de la app sigue funcionando.
    return null;
  }
}

/**
 * RF-38: registra el teléfono para recibir los avisos de la cuenta. Nunca falla: si el push no está disponible,
 * el usuario igual ve sus notificaciones en la app.
 */
export async function registrarPush(): Promise<boolean> {
  const tipo = plataforma();
  const token = await tokenDelDispositivo(true);
  if (!tipo || !token) return false;
  try {
    await registrarDispositivo(token, tipo);
    return true;
  } catch {
    return false;
  }
}

/** Antes de cerrar sesión, para que este teléfono deje de recibir los avisos de la cuenta. */
export async function darDeBajaPush(): Promise<void> {
  const token = await tokenDelDispositivo(false);
  if (!token) return;
  try {
    await darDeBajaDispositivo(token);
  } catch {
    // Si falla, el servidor lo reemplaza cuando otra cuenta registre este mismo teléfono.
  }
}
