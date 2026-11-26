import * as SecureStore from 'expo-secure-store';
import { setItemAsync, prepararSesionSegundoPlano } from './auth-storage';
jest.mock('expo-secure-store', () => ({ AFTER_FIRST_UNLOCK: 1,
  getItemAsync: jest.fn(), setItemAsync: jest.fn(async () => undefined), deleteItemAsync: jest.fn(async () => undefined),
}));
afterEach(() => jest.clearAllMocks());
it('keeps only session and sharing preference accessible after first unlock', async () => {
  await setItemAsync('napa.auth.session', 'sesion');
  await setItemAsync('napa.ubicacion-automatica', 'preferencia');
  await setItemAsync('napa.auth.device-id', 'dispositivo');
  expect(SecureStore.setItemAsync).toHaveBeenNthCalledWith(1, 'napa.auth.session', 'sesion', { keychainAccessible: 1 });
  expect(SecureStore.setItemAsync).toHaveBeenNthCalledWith(3, 'napa.auth.device-id', 'dispositivo');
});
it('does not overwrite a newer token when adapting an existing session', async () => {
  const valores = new Map([['napa.auth.session', 'anterior']]);
  jest.mocked(SecureStore.getItemAsync).mockImplementation(async (key) => valores.get(key) ?? null);
  jest.mocked(SecureStore.setItemAsync).mockImplementation(async (key, value) => { valores.set(key, value); });
  await Promise.all([prepararSesionSegundoPlano(), setItemAsync('napa.auth.session', 'nueva')]);
  expect(valores.get('napa.auth.session')).toBe('nueva');
});
