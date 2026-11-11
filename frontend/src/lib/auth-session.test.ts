import * as Crypto from 'expo-crypto';
import * as SecureStore from 'expo-secure-store';

import { ApiError } from './api';
import {
  changePassword,
  clearSession,
  getValidSession,
  login,
  readSession,
} from './auth';
import { createEmprendimiento, listEmprendimientos } from './emprendimientos';

jest.mock('expo-crypto', () => ({ randomUUID: jest.fn() }));
jest.mock('expo-secure-store', () => ({
  deleteItemAsync: jest.fn(),
  getItemAsync: jest.fn(),
  setItemAsync: jest.fn(),
}));

const SESSION_KEY = 'napa.auth.session';
const initialTime = 1_800_000_000_000;
const tokens = {
  accessToken: 'initial-access',
  refreshToken: 'initial-refresh',
  expiresIn: 900,
  tokenType: 'Bearer',
  rol: 'CLIENTE',
};
const rotated = {
  ...tokens,
  accessToken: 'rotated-access',
  refreshToken: 'rotated-refresh',
};
const page = { content: [], page: 0, size: 20, totalElements: 0 };
const business = {
  nombre: 'Frutas de Ana',
  categoria: 'FRUTAS_VERDURAS' as const,
  descripcion: 'Frutas frescas',
  horaApertura: '08:00',
  horaCierre: '18:00',
  ubicacion: { latitud: 4.711, longitud: -74.0721, direccion: null, referencia: null },
};

function response(body: unknown, status = 200): Response {
  return { ok: status < 400, status, text: async () => JSON.stringify(body) } as Response;
}

function tokenResponse(value: typeof tokens): Response {
  return response({
    accessToken: value.accessToken,
    refreshToken: value.refreshToken,
    expiresIn: value.expiresIn,
    usuario: { id: 'user-1', nombre: 'Ana', rol: value.rol },
  });
}

describe('session continuity against the HTTP contract', () => {
  const originalFetch = globalThis.fetch;
  const fetchMock = jest.fn();
  let storage: Map<string, string>;
  let now: number;

  beforeEach(() => {
    jest.resetAllMocks();
    now = initialTime;
    jest.spyOn(Date, 'now').mockImplementation(() => now);
    storage = new Map([
      ['napa.auth.device-id', 'device-1'],
      [SESSION_KEY, JSON.stringify({ ...tokens, expiresAt: now + 900_000 })],
    ]);
    jest.mocked(Crypto.randomUUID).mockReturnValue('operation-key');
    jest.mocked(SecureStore.getItemAsync).mockImplementation(async (key) => storage.get(key) ?? null);
    jest.mocked(SecureStore.setItemAsync).mockImplementation(async (key, value) => {
      storage.set(key, value);
    });
    jest.mocked(SecureStore.deleteItemAsync).mockImplementation(async (key) => {
      storage.delete(key);
    });
    globalThis.fetch = fetchMock;
  });

  afterEach(() => {
    jest.restoreAllMocks();
    globalThis.fetch = originalFetch;
  });

  it('renews with the token returned by a password change', async () => {
    fetchMock
      .mockResolvedValueOnce(tokenResponse(rotated))
      .mockResolvedValueOnce(tokenResponse({ ...rotated, refreshToken: 'next-refresh' }));

    await changePassword('old-password', 'new-password');
    expect(await readSession()).toEqual({ ...rotated, expiresAt: initialTime + 900_000 });

    now += 901_000;
    await getValidSession();

    expect(fetchMock).toHaveBeenNthCalledWith(2, expect.stringMatching(/\/auth\/refresh$/),
      expect.objectContaining({ body: JSON.stringify({ refreshToken: 'rotated-refresh' }) }));
    expect((await readSession())?.refreshToken).toBe('next-refresh');
  });

  it('keeps the current tokens when the password change is rejected', async () => {
    fetchMock.mockResolvedValueOnce(response({ code: 'INVALID_CURRENT_PASSWORD', message: 'Contraseña incorrecta' }, 400));

    await expect(changePassword('wrong-password', 'new-password')).rejects.toMatchObject({ status: 400 });
    expect((await readSession())?.refreshToken).toBe('initial-refresh');
    expect(SecureStore.setItemAsync).not.toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('renews before loading another page after a long stay on the screen', async () => {
    fetchMock
      .mockResolvedValueOnce(response(page))
      .mockResolvedValueOnce(tokenResponse(rotated))
      .mockResolvedValueOnce(response({ ...page, page: 1 }));

    await listEmprendimientos({ page: 0, size: 20 });
    now += 901_000;
    await listEmprendimientos({ page: 1, size: 20 });

    expect(fetchMock).toHaveBeenNthCalledWith(2, expect.stringMatching(/\/auth\/refresh$/), expect.anything());
    expect(fetchMock).toHaveBeenNthCalledWith(3, expect.stringContaining('page=1'),
      expect.objectContaining({ headers: expect.objectContaining({ Authorization: 'Bearer rotated-access' }) }));
    expect(SecureStore.deleteItemAsync).not.toHaveBeenCalled();
  });

  it('renews before publishing a form that was open beyond token expiry', async () => {
    now += 901_000;
    fetchMock.mockResolvedValueOnce(tokenResponse(rotated)).mockResolvedValueOnce(response({ ...business, id: 'business-id' }, 201));

    await createEmprendimiento(business);

    expect(fetchMock).toHaveBeenNthCalledWith(2, expect.stringMatching(/\/emprendimientos$/),
      expect.objectContaining({ headers: expect.objectContaining({ Authorization: 'Bearer rotated-access' }) }));
  });

  it('shares one refresh between simultaneous requests with expired tokens', async () => {
    now += 901_000;
    fetchMock.mockImplementation(async (url: string) =>
      url.endsWith('/refresh') ? tokenResponse(rotated) : response(page));

    await Promise.all([
      listEmprendimientos({ page: 0, size: 20 }),
      listEmprendimientos({ page: 1, size: 20 }),
      getValidSession(),
    ]);

    expect(fetchMock.mock.calls.filter(([url]) => url.endsWith('/refresh'))).toHaveLength(1);
    expect((await readSession())?.refreshToken).toBe('rotated-refresh');
  });

  it('retries a 401 with fresh credentials and the same creation key and body', async () => {
    fetchMock
      .mockResolvedValueOnce(response({ code: 'UNAUTHORIZED' }, 401))
      .mockResolvedValueOnce(tokenResponse(rotated))
      .mockResolvedValueOnce(response({ ...business, id: 'business-id' }, 201));

    await createEmprendimiento(business);

    const firstRequest = fetchMock.mock.calls[0][1] as RequestInit;
    const retry = fetchMock.mock.calls[2][1] as RequestInit;
    expect(firstRequest.body).toBe(retry.body);
    expect(firstRequest.headers).toMatchObject({ 'Idempotency-Key': 'operation-key' });
    expect(retry.headers).toMatchObject({ 'Idempotency-Key': 'operation-key', Authorization: 'Bearer rotated-access' });
    expect(Crypto.randomUUID).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('shares one refresh when simultaneous requests receive 401', async () => {
    fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
      if (url.endsWith('/refresh')) return tokenResponse(rotated);
      return (init.headers as Record<string, string>).Authorization === 'Bearer initial-access'
        ? response({ code: 'UNAUTHORIZED' }, 401)
        : response(page);
    });

    await Promise.all([
      listEmprendimientos({ page: 0, size: 20 }),
      listEmprendimientos({ page: 1, size: 20 }),
    ]);

    expect(fetchMock.mock.calls.filter(([url]) => url.endsWith('/refresh'))).toHaveLength(1);
  });

  it('propagates a revoked refresh without retrying the protected operation', async () => {
    fetchMock
      .mockResolvedValueOnce(response({ code: 'UNAUTHORIZED' }, 401))
      .mockResolvedValueOnce(response({ code: 'UNAUTHORIZED' }, 401));

    await expect(listEmprendimientos({ page: 0, size: 20 })).rejects.toMatchObject({ status: 401 });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('stops after the protected request is rejected a second time', async () => {
    fetchMock
      .mockResolvedValueOnce(response({ code: 'UNAUTHORIZED' }, 401))
      .mockResolvedValueOnce(tokenResponse(rotated))
      .mockResolvedValueOnce(response({ code: 'UNAUTHORIZED' }, 401));

    await expect(listEmprendimientos({ page: 0, size: 20 })).rejects.toMatchObject({ status: 401 });
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('preserves the session on a temporary refresh outage and allows another attempt', async () => {
    now += 901_000;
    fetchMock
      .mockResolvedValueOnce(response({ code: 'EXTERNAL_SERVICE_UNAVAILABLE' }, 503))
      .mockResolvedValueOnce(tokenResponse(rotated));

    await expect(getValidSession()).rejects.toMatchObject({ status: 503 });
    expect((await readSession())?.refreshToken).toBe('initial-refresh');
    expect(SecureStore.deleteItemAsync).not.toHaveBeenCalled();
    await expect(getValidSession()).resolves.toMatchObject(rotated);
  });

  it('does not restore a cleared session when an earlier refresh completes', async () => {
    now += 901_000;
    let finish!: (value: Response) => void;
    let started!: () => void;
    const refreshStarted = new Promise<void>((resolve) => { started = resolve; });
    const delayedResponse = new Promise<Response>((resolve) => { finish = resolve; });
    fetchMock.mockImplementationOnce(() => { started(); return delayedResponse; });

    const pending = getValidSession();
    await refreshStarted;
    await clearSession();
    finish(tokenResponse(rotated));

    await expect(pending).rejects.toMatchObject({ status: 401 });
    expect(await readSession()).toBeNull();
    expect(SecureStore.setItemAsync).not.toHaveBeenCalled();
  });

  it('treats a missing session as unauthorized without contacting the API', async () => {
    storage.delete(SESSION_KEY);

    await expect(getValidSession()).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('persists the role from the actual API response and can read the session back', async () => {
    fetchMock.mockResolvedValueOnce(tokenResponse(tokens));

    await expect(login('+573001234567', 'password')).resolves.toMatchObject({
      rol: 'CLIENTE', tokenType: 'Bearer',
    });
    await expect(readSession()).resolves.toMatchObject({
      accessToken: tokens.accessToken, rol: 'CLIENTE', tokenType: 'Bearer',
    });
  });
});
