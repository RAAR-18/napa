import { randomUUID } from './uuid';

import { ApiError, jsonHeaders, requestJson, requestVoid } from './api';
import * as authStorage from './auth-storage';

const AUTH_SESSION_KEY = 'napa.auth.session';
const DEVICE_ID_KEY = 'napa.auth.device-id';
const AUTH_PATH = '/api/v1/auth';

let sessionGeneration = 0;

export function getSessionGeneration(): number {
  return sessionGeneration;
}
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
  usuarioId?: string;
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
    usuarioId: response.usuario.id,
  };
}

export type StoredSession = AuthTokens & {
  expiresAt: number;
};

export type VerificationPurpose = 'REGISTRO' | 'RECUPERACION_CONTRASENA';
export type VerificationChannel = 'SMS' | 'CORREO';

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
  const newId = randomUUID();
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

/** Crea la cuenta sin enviar código: después la app pregunta si prefiere SMS o correo. */
export async function register(input: RegistrationInput): Promise<string | null> {
  const { confirmacionPassword, ...registration } = input;
  const response = await requestJson<{
    usuarioId: string;
    verificacion?: { estado?: string | null } | null;
  }>(`${AUTH_PATH}/register`, {
    method: 'POST',
    headers: jsonHeaders({
      deviceId: await deviceId(),
      idempotencyKey: randomUUID(),
    }),
    body: JSON.stringify({
      ...registration,
      confirmPassword: confirmacionPassword,
    }),
  });
  return response.verificacion?.estado ?? null;
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

/** La cuenta se identifica por celular o, si trae "@", por su correo. */
function cuenta(identificador: string): { celular: string } | { correo: string } {
  return identificador.includes('@') ? { correo: identificador } : { celular: identificador };
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
      body: JSON.stringify({ ...cuenta(celular), codigo }),
    },
  );
  return saveSession(sessionTokens(response));
}

export async function requestVerification(
  celular: string,
  proposito: VerificationPurpose,
  canal: VerificationChannel = 'SMS',
): Promise<number> {
  const path =
    proposito === 'REGISTRO'
      ? `${AUTH_PATH}/verify-phone/request`
      : `${AUTH_PATH}/password/recover/request`;
  const response = await requestJson<{ reenvioDisponibleEn?: number | string } | null>(path, {
    method: 'POST',
    headers: jsonHeaders(),
    body: JSON.stringify(
      proposito === 'REGISTRO' ? { ...cuenta(celular), canal } : { celular, canal },
    ),
  });
  const espera = Number(response?.reenvioDisponibleEn);
  return Number.isFinite(espera) && espera > 0 ? espera : 120;
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
      idempotencyKey: randomUUID(),
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
  const session = await withValidSession(async (currentSession) => {
    await requestVoid(`${AUTH_PATH}/logout`, {
      method: 'POST',
      headers: jsonHeaders({ accessToken: currentSession.accessToken }),
    });
    return currentSession;
  });
  await clearSession();

  if (session.usuarioId) {
    try {
      const { borrarPerfilGuardado } = await import('./perfil-cache');
      await borrarPerfilGuardado(session.usuarioId);
    } catch (cause) {
      console.warn('No se pudo borrar el perfil guardado al cerrar sesión.', cause);
    }
    try {
      const { borrarNotificacionesGuardadas } = await import('./notificaciones-cache');
      await borrarNotificacionesGuardadas(session.usuarioId);
    } catch (cause) {
      console.warn('No se pudo borrar la bandeja guardada al cerrar sesión.', cause);
    }
  }

  const vendedor =
    session.rol === 'VENDEDOR_AMBULANTE' || session.rol === 'VENDEDOR_PUNTO_FIJO';
  if (session.rol === 'CLIENTE' && session.usuarioId) {
    try {
      const { borrarCatalogoGuardado } = await import('./catalogo-cache');
      await borrarCatalogoGuardado(session.usuarioId);
      const { borrarCotizacionesOffline } = await import('./pedido-offline');
      await borrarCotizacionesOffline(session.usuarioId);
    } catch (cause) {
      console.warn('No se pudo borrar el catálogo al cerrar sesión.', cause);
    }
    try {
      const { borrarPedidosGuardados } = await import('./pedidos-cliente-cache');
      await borrarPedidosGuardados(session.usuarioId);
    } catch (cause) {
      console.warn('No se pudo borrar la caché de pedidos al cerrar sesión.', cause);
    }
  }
  if ((vendedor || session.rol === 'DOMICILIARIO') && session.usuarioId) {
    try {
      const { borrarPedidosGuardados } = await import('./pedidos-cliente-cache');
      await borrarPedidosGuardados(session.usuarioId);
    } catch (cause) {
      console.warn('No se pudieron borrar los pedidos y domicilios guardados al cerrar sesión.', cause);
    }
  }
  if (vendedor && session.usuarioId) {
    try {
      const { borrarInventarioGuardado } = await import('./inventario-cache');
      await borrarInventarioGuardado(session.usuarioId);
    } catch (cause) {
      console.warn('No se pudo borrar el inventario al cerrar sesión.', cause);
    }
    try {
      const { borrarEmprendimientoPropio } = await import('./emprendimiento-propio-cache');
      await borrarEmprendimientoPropio(session.usuarioId);
    } catch (cause) {
      console.warn('No se pudo borrar el emprendimiento guardado al cerrar sesión.', cause);
    }
  }
}

export async function changePassword(
  contrasenaActual: string,
  contrasenaNueva: string,
): Promise<StoredSession> {
  const idempotencyKey = randomUUID();
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
