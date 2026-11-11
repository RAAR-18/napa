import * as SecureStore from 'expo-secure-store';

import {
  changePassword,
  confirmRegistration,
  login,
  refreshSession,
  register,
  requestVerification,
  resetPassword,
  type StoredSession,
} from './auth';

jest.mock('expo-crypto', () => ({ randomUUID: () => 'generated-id' }));
jest.mock('expo-secure-store', () => ({
  deleteItemAsync: jest.fn(),
  getItemAsync: jest.fn(),
  setItemAsync: jest.fn(),
}));

const tokens = {
  accessToken: 'access',
  refreshToken: 'refresh',
  expiresIn: 900,
  tokenType: 'Bearer',
  rol: 'CLIENTE',
};

const authResponse = {
  accessToken: tokens.accessToken,
  refreshToken: tokens.refreshToken,
  expiresIn: tokens.expiresIn,
  usuario: { id: 'user-1', nombre: 'Ana', rol: tokens.rol },
};

function mockResponse(body = '{}'): Response {
  return {
    ok: true,
    status: 200,
    text: async () => body,
  } as Response;
}

describe('auth API contract', () => {
  const originalFetch = globalThis.fetch;
  const fetchMock = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
    jest.mocked(SecureStore.getItemAsync).mockResolvedValue('device-1');
    jest.mocked(SecureStore.setItemAsync).mockResolvedValue();
    fetchMock.mockResolvedValue(mockResponse());
    globalThis.fetch = fetchMock;
  });

  afterAll(() => {
    globalThis.fetch = originalFetch;
  });

  it('registers against the backend contract', async () => {
    await register({
      nombre: 'Ana',
      celular: '+573001234567',
      correo: null,
      password: 'password',
      confirmacionPassword: 'password',
      rol: 'CLIENTE',
    });

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/auth\/register$/),
      expect.objectContaining({
        headers: expect.objectContaining({
          'X-Device-Id': 'device-1',
          'Idempotency-Key': 'generated-id',
        }),
        body: JSON.stringify({
          nombre: 'Ana',
          celular: '+573001234567',
          correo: null,
          password: 'password',
          rol: 'CLIENTE',
          confirmPassword: 'password',
        }),
      }),
    );
  });

  it.each([
    ['REGISTRO', '/api/v1/auth/verify-phone/request'],
    ['RECUPERACION_CONTRASENA', '/api/v1/auth/password/recover/request'],
  ] as const)('requests %s verification from the matching endpoint', async (purpose, path) => {
    await requestVerification('+573001234567', purpose);

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(new RegExp(`${path}$`)),
      expect.objectContaining({
        body: JSON.stringify({ celular: '+573001234567' }),
      }),
    );
  });

  it('confirms registration and stores the returned session', async () => {
    fetchMock.mockResolvedValue(mockResponse(JSON.stringify(authResponse)));

    await confirmRegistration('+573001234567', '123456');

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/auth\/verify-phone\/confirm$/),
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-Device-Id': 'device-1' }),
        body: JSON.stringify({ celular: '+573001234567', codigo: '123456' }),
      }),
    );
    expect(SecureStore.setItemAsync).toHaveBeenCalledWith(
      'napa.auth.session',
      expect.stringContaining('"accessToken":"access"'),
    );
  });

  it('confirms password recovery using the backend field names', async () => {
    await resetPassword('+573001234567', '123456', 'new-password');

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/auth\/password\/recover\/confirm$/),
      expect.objectContaining({
        headers: expect.objectContaining({
          'X-Device-Id': 'device-1',
          'Idempotency-Key': 'generated-id',
        }),
        body: JSON.stringify({
          celular: '+573001234567',
          codigo: '123456',
          passwordNueva: 'new-password',
        }),
      }),
    );
  });

  it('refreshes with a device ID and changes password using backend names', async () => {
    fetchMock.mockResolvedValue(mockResponse(JSON.stringify(authResponse)));
    const session: StoredSession = { ...tokens, expiresAt: 0 };
    await refreshSession(session);

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      expect.stringMatching(/\/api\/v1\/auth\/refresh$/),
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-Device-Id': 'device-1' }),
      }),
    );

    jest.mocked(SecureStore.getItemAsync).mockImplementation(async (key) =>
      key === 'napa.auth.session'
        ? JSON.stringify({ ...tokens, expiresAt: Date.now() + 900_000 })
        : 'device-1',
    );
    await changePassword('old-password', 'new-password');

    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      expect.stringMatching(/\/api\/v1\/auth\/password\/change$/),
      expect.objectContaining({
        body: JSON.stringify({
          passwordActual: 'old-password',
          passwordNueva: 'new-password',
        }),
      }),
    );
  });

  it('sends login with the device ID required by the backend', async () => {
    fetchMock.mockResolvedValue(mockResponse(JSON.stringify(authResponse)));

    await login('+573001234567', 'password');

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/auth\/login$/),
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-Device-Id': 'device-1' }),
      }),
    );
  });
});
