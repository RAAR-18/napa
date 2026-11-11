import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

import { clearSession, login, logout, readSession } from './auth';

jest.mock('expo-constants', () => ({ expoConfig: {} }));
jest.mock('expo-crypto', () => ({ randomUUID: () => 'browser-device' }));
jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(() => { throw new Error('Native storage is unavailable on web'); }),
  setItemAsync: jest.fn(() => { throw new Error('Native storage is unavailable on web'); }),
  deleteItemAsync: jest.fn(() => { throw new Error('Native storage is unavailable on web'); }),
}));

const authResponse = {
  accessToken: 'browser-access',
  refreshToken: 'browser-refresh',
  expiresIn: 900,
  usuario: { id: 'user-1', nombre: 'Ana', rol: 'CLIENTE' },
};

describe('authentication in a browser without native SecureStore', () => {
  const originalFetch = globalThis.fetch;
  const originalWindow = Object.getOwnPropertyDescriptor(globalThis, 'window');
  const fetchMock = jest.fn();
  let values: Map<string, string>;
  let browserStorage: Storage;

  beforeEach(() => {
    jest.clearAllMocks();
    jest.replaceProperty(Platform, 'OS', 'web');
    values = new Map();
    browserStorage = {
      getItem: (key) => values.get(key) ?? null,
      setItem: (key, value) => { values.set(key, value); },
      removeItem: (key) => { values.delete(key); },
      clear: () => values.clear(),
      key: (index) => [...values.keys()][index] ?? null,
      get length() { return values.size; },
    };
    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      value: { localStorage: browserStorage },
    });
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      text: async () => JSON.stringify(authResponse),
    });
    globalThis.fetch = fetchMock;
  });

  afterEach(() => {
    jest.restoreAllMocks();
    globalThis.fetch = originalFetch;
    if (originalWindow) {
      Object.defineProperty(globalThis, 'window', originalWindow);
    } else {
      Reflect.deleteProperty(globalThis, 'window');
    }
    expect(SecureStore.getItemAsync).not.toHaveBeenCalled();
    expect(SecureStore.setItemAsync).not.toHaveBeenCalled();
    expect(SecureStore.deleteItemAsync).not.toHaveBeenCalled();
  });

  it('opens the welcome screen with no saved session', async () => {
    await expect(readSession()).resolves.toBeNull();
  });

  it('persists a login and device identity, then removes the session on logout', async () => {
    const session = await login('3001234567', 'password');
    expect(await readSession()).toEqual(session);
    expect(values.get('napa.auth.device-id')).toBe('browser-device');
    await login('3001234567', 'password');
    expect(fetchMock.mock.calls[1][1].headers).toMatchObject({
      'X-Device-Id': 'browser-device',
    });

    await logout();
    expect(await readSession()).toBeNull();
    expect(values.get('napa.auth.device-id')).toBe('browser-device');
  });

  it('clears a corrupt saved session so the user can sign in again', async () => {
    values.set('napa.auth.session', 'invalid json');
    await expect(readSession()).rejects.toThrow('La sesión guardada está dañada');
    expect(await readSession()).toBeNull();
  });

  it('can read an absent session during server rendering without a window', async () => {
    Reflect.deleteProperty(globalThis, 'window');
    await expect(readSession()).resolves.toBeNull();
  });

  it('reports blocked browser storage without exposing a native module error', async () => {
    Object.defineProperty(window, 'localStorage', {
      get() { throw new Error('SecurityError'); },
    });
    await expect(readSession()).rejects.toThrow('Permite el almacenamiento de este sitio');
  });

  it('reports a failed write instead of claiming a login was saved', async () => {
    values.set('napa.auth.device-id', 'browser-device');
    browserStorage.setItem = () => { throw new Error('QuotaExceededError'); };
    await expect(login('3001234567', 'password')).rejects.toThrow(
      'Permite el almacenamiento de este sitio',
    );
    expect(await readSession()).toBeNull();
    await clearSession();
  });
});
