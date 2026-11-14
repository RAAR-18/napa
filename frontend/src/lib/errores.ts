import { router } from 'expo-router';

import { ApiError } from './api';
import { clearSession } from './auth';

/** Con 401 cierra la sesión y vuelve al ingreso; devuelve `null` en ese caso. */
export async function manejarError(cause: unknown, porDefecto: string): Promise<string | null> {
  if (cause instanceof ApiError && cause.status === 401) {
    await clearSession();
    router.replace('/login');
    return null;
  }
  return cause instanceof Error && cause.message ? cause.message : porDefecto;
}

/** El primer mensaje por campo de un `VALIDATION_ERROR`, si la API lo envió. */
export function mensajeDeCampo(cause: unknown): string | null {
  if (!(cause instanceof ApiError)) return null;
  const fields = cause.details.fields;
  if (typeof fields !== 'object' || fields === null) return null;
  const primero = Object.values(fields as Record<string, unknown>)[0];
  return typeof primero === 'string' ? primero : null;
}
