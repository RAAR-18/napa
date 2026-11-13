import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

function inBrowserStorage<T>(operation: (storage: Storage) => T): T {
  try {
    return operation(window.localStorage);
  } catch {
    throw new Error(
      'No se pudo acceder a la sesión. Permite el almacenamiento de este sitio en tu navegador e intenta de nuevo.',
    );
  }
}

export async function getItemAsync(key: string): Promise<string | null> {
  if (Platform.OS !== 'web') {
    return SecureStore.getItemAsync(key);
  }
  // El renderizado web en el servidor no dispone de almacenamiento del usuario.
  if (typeof window === 'undefined') {
    return null;
  }
  return inBrowserStorage((storage) => storage.getItem(key));
}

export async function setItemAsync(key: string, value: string): Promise<void> {
  if (Platform.OS !== 'web') {
    return SecureStore.setItemAsync(key, value);
  }
  inBrowserStorage((storage) => storage.setItem(key, value));
}

export async function deleteItemAsync(key: string): Promise<void> {
  if (Platform.OS !== 'web') {
    return SecureStore.deleteItemAsync(key);
  }
  inBrowserStorage((storage) => storage.removeItem(key));
}
