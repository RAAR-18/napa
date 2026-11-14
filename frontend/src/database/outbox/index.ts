import * as Crypto from 'expo-crypto';

import { jsonHeaders, requestVoid } from '../../lib/api';
import { withValidSession } from '../../lib/auth';
import { Outbox } from './outbox';
import { SqliteOutboxStore } from './sqlite-store';
import type { AccionPendiente } from './types';

export { Outbox } from './outbox';
export type { AccionPendiente, EstadoEnvio, OutboxStore } from './types';

/** Envía con la sesión vigente (renovándola si hace falta) y la misma `Idempotency-Key` de siempre. */
async function enviarConSesion(accion: AccionPendiente): Promise<void> {
  await withValidSession((session) =>
    requestVoid(accion.path, {
      method: accion.method,
      headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey: accion.idempotencyKey }),
      body: accion.body ?? undefined,
    }),
  );
}

/** La cola de la app (F041). */
export const outbox = new Outbox({
  store: new SqliteOutboxStore(),
  enviar: enviarConSesion,
  nuevoId: () => Crypto.randomUUID(),
});
