import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';

import { darDeBajaDispositivo, registrarDispositivo } from './notificaciones';

import { AndroidImportance } from 'expo-notifications';

import { ApiError } from './api';

/** La web usa consulta periódica; en la app nativa actualizamos también al recibir push. */
export function escucharNovedades(actualizar: () => void): () => void {
  if (Platform.OS === 'web') return () => {};
  try {
    const subscription = Notifications.addNotificationReceivedListener(actualizar);
    return () => subscription.remove();
  } catch {
    return () => {};
  }
}

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

async function tokenDelDispositivo(pedirPermiso: boolean): Promise<string | null> {
  if (!plataforma()) return null;

  try {
    if (pedirPermiso && Platform.OS === 'android') {
      await Notifications.setNotificationChannelAsync('default', {
        name: 'Notificaciones de Ñapa',
        importance: AndroidImportance.DEFAULT,
      });
    }

    let { status } = await Notifications.getPermissionsAsync();
    if (status !== 'granted' && pedirPermiso) {
      ({ status } = await Notifications.requestPermissionsAsync());
    }

    if (status !== 'granted') {
      if (__DEV__ && pedirPermiso) {
        console.info('[push] Permiso de notificaciones no concedido.');
      }
      return null;
    }

    const token = await Notifications.getDevicePushTokenAsync();
    return typeof token.data === 'string' && token.data ? token.data : null;
  } catch (error) {
    if (__DEV__ && pedirPermiso) {
      console.info(
        '[push] Error nativo:',
        error instanceof Error ? error.message : String(error),
      );
    }
    return null;
  }
}

export async function registrarPush(): Promise<boolean> {
  const tipo = plataforma();
  const token = await tokenDelDispositivo(true);
  if (!tipo || !token) return false;

  try {
    await registrarDispositivo(token, tipo);
    return true;
  } catch (error) {
    if (__DEV__) {
      const detalle = error instanceof ApiError ? ` (HTTP ${error.status})` : '';
      console.info(`[push] Falló POST /api/v1/dispositivos${detalle}.`);
    }
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
