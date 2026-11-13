import Constants from 'expo-constants';

type ApiErrorBody = {
  code?: string;
  message?: string;
  details?: Record<string, unknown>;
};

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  return (
    typeof value === 'object' &&
    value !== null &&
    (!('code' in value) || typeof value.code === 'string') &&
    (!('message' in value) || typeof value.message === 'string') &&
    (!('details' in value) || (typeof value.details === 'object' && value.details !== null))
  );
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
    readonly details: Record<string, unknown> = {},
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

function getApiBaseUrl(): string {
  const configuredUrl = process.env.EXPO_PUBLIC_API_URL;
  if (configuredUrl?.trim()) {
    return configuredUrl.trim().replace(/\/+$/, '');
  }

  const hostUri = Constants.expoConfig?.hostUri;
  if (hostUri) {
    try {
      const host = new URL(`http://${hostUri}`).hostname;
      return `http://${host}:8080`;
    } catch {
      throw new Error(
        'No se pudo determinar la dirección de la API. Configura EXPO_PUBLIC_API_URL.',
      );
    }
  }

  return 'http://localhost:8080';
}

export const apiBaseUrl = getApiBaseUrl();

export async function requestJson<T>(
  path: string,
  init: RequestInit,
): Promise<T> {
  const response = await sendRequest(path, init);
  const body = await response.text();
  if (!body) {
    throw new ApiError('La API respondió sin datos.', response.status);
  }

  let data: unknown;
  try {
    data = JSON.parse(body);
  } catch {
    throw new ApiError(
      'La API devolvió una respuesta que no se pudo interpretar.',
      response.status,
    );
  }

  if (!response.ok) {
    const errorBody = isApiErrorBody(data) ? data : {};
    throw new ApiError(
      errorBody.message ?? 'No fue posible completar la solicitud.',
      response.status,
      errorBody.code,
      errorBody.details ?? {},
    );
  }

  return data as T;
}

export async function requestVoid(path: string, init: RequestInit): Promise<void> {
  const response = await sendRequest(path, init);
  if (response.ok) {
    return;
  }

  const body = await response.text();
  let parsed: unknown = {};
  if (body) {
    try {
      parsed = JSON.parse(body);
    } catch {
      throw new ApiError(
        `La API devolvió un error ilegible (HTTP ${response.status}).`,
        response.status,
      );
    }
  }
  const errorBody = isApiErrorBody(parsed) ? parsed : {};

  throw new ApiError(
    errorBody.message ??
      `No fue posible completar la solicitud (HTTP ${response.status}).`,
    response.status,
    errorBody.code,
    errorBody.details ?? {},
  );
}

async function sendRequest(path: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(`${apiBaseUrl}${path}`, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...init.headers,
      },
    });
  } catch (error) {
    if (
      error instanceof TypeError ||
      (error instanceof Error &&
        /java\.net|network request failed|failed to connect|connection refused|connection timed out|cleartext/i.test(
          error.message,
        ))
    ) {
      const detail = error instanceof Error ? ` Detalle: ${error.message}` : '';
      throw new Error(
        `No se pudo conectar con la API en ${apiBaseUrl}. Comprueba que el backend esté activo en el puerto 8080 y que el teléfono y el computador estén en la misma red Wi-Fi.${detail}`,
      );
    }
    throw error;
  }
}

export function jsonHeaders(options: {
  deviceId?: string;
  idempotencyKey?: string;
  accessToken?: string;
} = {}): HeadersInit {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (options.deviceId) {
    headers['X-Device-Id'] = options.deviceId;
  }
  if (options.idempotencyKey) {
    headers['Idempotency-Key'] = options.idempotencyKey;
  }
  if (options.accessToken) {
    headers.Authorization = `Bearer ${options.accessToken}`;
  }
  return headers;
}
