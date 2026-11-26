import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';
let escritura = Promise.resolve();
function escribir<T>(operacion: () => Promise<T>): Promise<T> {
  const resultado = escritura.then(operacion);
  escritura = resultado.then(() => undefined, () => undefined);
  return resultado;
}
function opciones(key: string): SecureStore.SecureStoreOptions | undefined {
  return key === 'napa.auth.session' || key === 'napa.ubicacion-automatica'
    ? { keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK } : undefined;
}
/** Adapta una sesión existente sin sobrescribir una renovación concurrente en este runtime. */
export async function prepararSesionSegundoPlano(): Promise<void> {
  if (Platform.OS === 'web') return;
  await escribir(async () => {
    const key = 'napa.auth.session';
    const valor = await SecureStore.getItemAsync(key);
    if (!valor) throw new Error('Inicia sesión para compartir ubicación.');
    await SecureStore.setItemAsync(key, valor, opciones(key));
  });
}


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
    return escribir(() => opciones(key)
      ? SecureStore.setItemAsync(key, value, opciones(key))
      : SecureStore.setItemAsync(key, value));
  }
  inBrowserStorage((storage) => storage.setItem(key, value));
}

export async function deleteItemAsync(key: string): Promise<void> {
  if (Platform.OS !== 'web') {
    return escribir(() => SecureStore.deleteItemAsync(key));
  }
  inBrowserStorage((storage) => storage.removeItem(key));
}
