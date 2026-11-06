import * as Crypto from 'expo-crypto';
import * as SecureStore from 'expo-secure-store';

import { jsonHeaders, requestJson, requestVoid } from './api';

const AUTH_SESSION_KEY = 'napa.auth.session';
const DEVICE_ID_KEY = 'napa.auth.device-id';
const AUTH_PATH = '/api/v1/auth';

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
  const currentId = await SecureStore.getItemAsync(DEVICE_ID_KEY);
  if (currentId) {
    return currentId;
  }
  const newId = Crypto.randomUUID();
  await SecureStore.setItemAsync(DEVICE_ID_KEY, newId);
  return newId;
}

export async function saveSession(tokens: AuthTokens): Promise<StoredSession> {
  const session: StoredSession = {
    ...tokens,
    expiresAt: Date.now() + tokens.expiresIn * 1000,
  };
  await SecureStore.setItemAsync(AUTH_SESSION_KEY, JSON.stringify(session));
  return session;
}

export async function readSession(): Promise<StoredSession | null> {
  const storedSession = await SecureStore.getItemAsync(AUTH_SESSION_KEY);
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
  await SecureStore.deleteItemAsync(AUTH_SESSION_KEY);
}

export async function register(input: RegistrationInput): Promise<void> {
  await requestJson<{ usuarioId: string }>(`${AUTH_PATH}/registro`, {
    method: 'POST',
    headers: jsonHeaders({
      deviceId: await deviceId(),
      idempotencyKey: Crypto.randomUUID(),
    }),
    body: JSON.stringify(input),
  });
}

export async function login(
  identificador: string,
  password: string,
): Promise<StoredSession> {
  const tokens = await requestJson<AuthTokens>(`${AUTH_PATH}/login`, {
    method: 'POST',
    headers: jsonHeaders({ deviceId: await deviceId() }),
    body: JSON.stringify({ identificador, password }),
  });
  return saveSession(tokens);
}

export async function confirmRegistration(
  celular: string,
  codigo: string,
): Promise<StoredSession> {
  const tokens = await requestJson<AuthTokens>(
    `${AUTH_PATH}/verificaciones-celular/confirmar`,
    {
      method: 'POST',
      headers: jsonHeaders({ deviceId: await deviceId() }),
      body: JSON.stringify({ celular, codigo }),
    },
  );
  return saveSession(tokens);
}

export async function requestVerification(
  celular: string,
  proposito: VerificationPurpose,
): Promise<void> {
  await requestJson(`${AUTH_PATH}/verificaciones-celular`, {
    method: 'POST',
    headers: jsonHeaders(),
    body: JSON.stringify({ celular, proposito }),
  });
}

export async function resetPassword(
  celular: string,
  codigo: string,
  contrasenaNueva: string,
): Promise<void> {
  await requestVoid(`${AUTH_PATH}/recuperar-contrasena`, {
    method: 'POST',
    headers: jsonHeaders({
      deviceId: await deviceId(),
      idempotencyKey: Crypto.randomUUID(),
    }),
    body: JSON.stringify({ celular, codigo, contrasenaNueva }),
  });
}

export async function refreshSession(
  session: StoredSession,
): Promise<StoredSession> {
  const tokens = await requestJson<AuthTokens>(`${AUTH_PATH}/refresh`, {
    method: 'POST',
    headers: jsonHeaders(),
    body: JSON.stringify({ refreshToken: session.refreshToken }),
  });
  return saveSession(tokens);
}

export async function getValidSession(): Promise<StoredSession> {
  const session = await readSession();
  if (!session) {
    throw new Error('Tu sesión terminó. Inicia sesión para continuar.');
  }
  if (session.expiresAt > Date.now() + 30_000) {
    return session;
  }
  return refreshSession(session);
}

export async function logout(session: StoredSession): Promise<void> {
  await requestVoid(`${AUTH_PATH}/logout`, {
    method: 'POST',
    headers: jsonHeaders({
      accessToken: session.accessToken,
      idempotencyKey: Crypto.randomUUID(),
    }),
  });
  await clearSession();
}

export async function changePassword(
  session: StoredSession,
  contrasenaActual: string,
  contrasenaNueva: string,
): Promise<void> {
  await requestVoid(`${AUTH_PATH}/cambio-contrasena`, {
    method: 'POST',
    headers: jsonHeaders({
      accessToken: session.accessToken,
      idempotencyKey: Crypto.randomUUID(),
    }),
    body: JSON.stringify({ contrasenaActual, contrasenaNueva }),
  });
}
