import * as Crypto from 'expo-crypto';

import { ApiError, jsonHeaders, requestVoid } from '../../lib/api';
import { readSession, withValidSession } from '../../lib/auth';
import { Outbox } from './outbox';
import { SqliteOutboxStore } from './sqlite-store';
import type { AccionPendiente } from './types';

export { Outbox } from './outbox';
export type { AccionPendiente, EstadoEnvio, OutboxStore } from './types';

/** Envía con la sesión vigente (renovándola si hace falta) y la misma `Idempotency-Key` de siempre. */
async function enviarConSesion(accion: AccionPendiente): Promise<void> {
  await withValidSession((session) => {
    if (session.usuarioId !== accion.usuarioId) {
      throw new ApiError('La acción pertenece a otra cuenta.', 401, 'UNAUTHORIZED');
    }
    return requestVoid(accion.path, {
      method: accion.method,
      headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey: accion.idempotencyKey }),
      body: accion.body ?? undefined,
    });
  });
}

/** La cola de la app (F041). */
export const outbox = new Outbox({
  store: new SqliteOutboxStore(),
  usuarioActual: async () => (await readSession())?.usuarioId ?? null,
  enviar: enviarConSesion,
  nuevoId: () => Crypto.randomUUID(),
});
