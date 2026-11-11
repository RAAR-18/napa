import * as Crypto from 'expo-crypto';

import { ApiError, jsonHeaders, requestJson, requestVoid } from './api';
import * as authStorage from './auth-storage';

const AUTH_SESSION_KEY = 'napa.auth.session';
const DEVICE_ID_KEY = 'napa.auth.device-id';
const AUTH_PATH = '/api/v1/auth';

let sessionGeneration = 0;
let refreshInFlight: {
  refreshToken: string;
  generation: number;
  promise: Promise<StoredSession>;
} | null = null;

export type UserRole =
  | 'CLIENTE'
  | 'VENDEDOR_AMBULANTE'
  | 'VENDEDOR_PUNTO_FIJO'
  | 'DOMICILIARIO';

export type AuthTokens = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
  rol: string;
};

type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  usuario: { id: string; nombre: string; rol: string };
};

function sessionTokens(response: AuthResponse): AuthTokens {
  return {
    accessToken: response.accessToken,
    refreshToken: response.refreshToken,
    expiresIn: response.expiresIn,
    tokenType: 'Bearer',
    rol: response.usuario.rol,
  };
}

export type StoredSession = AuthTokens & {
  expiresAt: number;
};

export type VerificationPurpose = 'REGISTRO' | 'RECUPERACION_CONTRASENA';

type RegistrationInput = {
  nombre: string;
  celular: string;
  correo: string | null;
  password: string;
  confirmacionPassword: string;
  rol: UserRole;
};

function isStoredSession(value: unknown): value is StoredSession {
  return (
    typeof value === 'object' &&
    value !== null &&
    'accessToken' in value &&
    typeof value.accessToken === 'string' &&
    'refreshToken' in value &&
    typeof value.refreshToken === 'string' &&
    'expiresIn' in value &&
    typeof value.expiresIn === 'number' &&
    'expiresAt' in value &&
    typeof value.expiresAt === 'number' &&
    'tokenType' in value &&
    typeof value.tokenType === 'string' &&
    'rol' in value &&
    typeof value.rol === 'string'
  );
}

async function deviceId(): Promise<string> {
  const currentId = await authStorage.getItemAsync(DEVICE_ID_KEY);
  if (currentId) {
    return currentId;
  }
  const newId = Crypto.randomUUID();
  await authStorage.setItemAsync(DEVICE_ID_KEY, newId);
  return newId;
}

export async function saveSession(tokens: AuthTokens): Promise<StoredSession> {
  sessionGeneration += 1;
  return storeSession(tokens);
}

async function storeSession(tokens: AuthTokens): Promise<StoredSession> {
  const session: StoredSession = {
    ...tokens,
    expiresAt: Date.now() + tokens.expiresIn * 1000,
  };
  await authStorage.setItemAsync(AUTH_SESSION_KEY, JSON.stringify(session));
  return session;
}

export async function readSession(): Promise<StoredSession | null> {
  const storedSession = await authStorage.getItemAsync(AUTH_SESSION_KEY);
  if (!storedSession) {
    return null;
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(storedSession);
  } catch {
    await clearSession();
    throw new Error('La sesión guardada está dañada. Inicia sesión de nuevo.');
  }
  if (!isStoredSession(parsed)) {
    await clearSession();
    throw new Error('La sesión guardada no es válida. Inicia sesión de nuevo.');
  }
  return parsed;
}

export async function clearSession(): Promise<void> {
  sessionGeneration += 1;
  await authStorage.deleteItemAsync(AUTH_SESSION_KEY);
}

export async function register(input: RegistrationInput): Promise<void> {
  const { confirmacionPassword, ...registration } = input;
  await requestJson<{ usuarioId: string }>(`${AUTH_PATH}/register`, {
    method: 'POST',
    headers: jsonHeaders({
      deviceId: await deviceId(),
      idempotencyKey: Crypto.randomUUID(),
    }),
    body: JSON.stringify({
      ...registration,
      confirmPassword: confirmacionPassword,
    }),
  });
}

export async function login(
  identificador: string,
  password: string,
): Promise<StoredSession> {
  const response = await requestJson<AuthResponse>(`${AUTH_PATH}/login`, {
    method: 'POST',
    headers: jsonHeaders({ deviceId: await deviceId() }),
    body: JSON.stringify({ identificador, password }),
  });
  return saveSession(sessionTokens(response));
}

export async function confirmRegistration(
  celular: string,
  codigo: string,
): Promise<StoredSession> {
  const response = await requestJson<AuthResponse>(
    `${AUTH_PATH}/verify-phone/confirm`,
    {
      method: 'POST',
      headers: jsonHeaders({ deviceId: await deviceId() }),
      body: JSON.stringify({ celular, codigo }),
    },
  );
  return saveSession(sessionTokens(response));
}

export async function requestVerification(
  celular: string,
  proposito: VerificationPurpose,
): Promise<void> {
  const path =
    proposito === 'REGISTRO'
      ? `${AUTH_PATH}/verify-phone/request`
      : `${AUTH_PATH}/password/recover/request`;
  await requestJson(path, {
    method: 'POST',
    headers: jsonHeaders(),
    body: JSON.stringify({ celular }),
  });
}

export async function resetPassword(
  celular: string,
  codigo: string,
  contrasenaNueva: string,
): Promise<void> {
  await requestVoid(`${AUTH_PATH}/password/recover/confirm`, {
    method: 'POST',
    headers: jsonHeaders({
      deviceId: await deviceId(),
      idempotencyKey: Crypto.randomUUID(),
    }),
    body: JSON.stringify({ celular, codigo, passwordNueva: contrasenaNueva }),
  });
}

export async function refreshSession(
  session: StoredSession,
): Promise<StoredSession> {
  if (
    refreshInFlight?.refreshToken === session.refreshToken &&
    refreshInFlight.generation === sessionGeneration
  ) {
    return refreshInFlight.promise;
  }

  const generation = sessionGeneration;
  const promise = (async () => {
    const response = await requestJson<AuthResponse>(`${AUTH_PATH}/refresh`, {
      method: 'POST',
      headers: jsonHeaders({ deviceId: await deviceId() }),
      body: JSON.stringify({ refreshToken: session.refreshToken }),
    });
    // Un cierre de sesión o un nuevo login invalida una renovación anterior.
    if (generation !== sessionGeneration) {
      throw sessionEnded();
    }
    return storeSession(sessionTokens(response));
  })();
  refreshInFlight = { refreshToken: session.refreshToken, generation, promise };
  try {
    return await promise;
  } finally {
    if (refreshInFlight?.promise === promise) {
      refreshInFlight = null;
    }
  }
}

export async function getValidSession(): Promise<StoredSession> {
  const session = await readSession();
  if (!session) {
    throw sessionEnded();
  }
  if (session.expiresAt > Date.now() + 30_000) {
    return session;
  }
  return refreshSession(session);
}

function sessionEnded(): ApiError {
  return new ApiError(
    'Tu sesión terminó. Inicia sesión para continuar.',
    401,
    'UNAUTHORIZED',
  );
}

export async function withValidSession<T>(
  request: (session: StoredSession) => Promise<T>,
): Promise<T> {
  const session = await getValidSession();
  try {
    return await request(session);
  } catch (cause) {
    if (!(cause instanceof ApiError) || cause.status !== 401) {
      throw cause;
    }
    const latest = await getValidSession();
    const renewed =
      latest.accessToken === session.accessToken
        ? await refreshSession(latest)
        : latest;
    // Un segundo 401 se propaga: una sesión revocada no se reintenta en bucle.
    return request(renewed);
  }
}

export async function logout(): Promise<void> {
  await withValidSession((session) =>
    requestVoid(`${AUTH_PATH}/logout`, {
      method: 'POST',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
  await clearSession();
}

export async function changePassword(
  contrasenaActual: string,
  contrasenaNueva: string,
): Promise<StoredSession> {
  const idempotencyKey = Crypto.randomUUID();
  const response = await withValidSession((session) =>
    requestJson<AuthResponse>(`${AUTH_PATH}/password/change`, {
      method: 'POST',
      headers: jsonHeaders({
        accessToken: session.accessToken,
        idempotencyKey,
      }),
      body: JSON.stringify({
        passwordActual: contrasenaActual,
        passwordNueva: contrasenaNueva,
      }),
    }),
  );
  return saveSession(sessionTokens(response));
}
