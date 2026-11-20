import { randomUUID } from '../../lib/uuid';

import { ApiError, jsonHeaders, requestJson, requestVoid } from '../../lib/api';
import { readSession, withValidSession } from '../../lib/auth';
import { RUTA_EDICION_EMPRENDIMIENTO } from '../../lib/emprendimiento-pendiente';
import { RUTA_REGISTRO_PRODUCTO } from '../../lib/productos-pendientes';
import { borrarCarritoEncolado, RUTA_CREACION_PEDIDO } from '../../lib/pedido-offline';
import { respuestaPedidoPendiente } from '../../lib/respuesta-pedido-pendiente';
import { quitarDetalleVendedorGuardado } from '../../lib/pedidos-cliente-cache';
import type { PedidoCreado } from '../../lib/pedidos';
import { Outbox } from './outbox';
import { SqliteOutboxStore } from './sqlite-store';
import type { AccionPendiente } from './types';

export { Outbox } from './outbox';
export type { AccionPendiente, EstadoEnvio, OutboxStore } from './types';

/** Envía con la sesión vigente (renovándola si hace falta) y la misma `Idempotency-Key` de siempre. */
async function enviarConSesion(accion: AccionPendiente): Promise<void> {
  await withValidSession(async (session) => {
    if (session.usuarioId !== accion.usuarioId) {
      throw new ApiError('La acción pertenece a otra cuenta.', 401, 'UNAUTHORIZED');
    }
    const solicitud = {
      method: accion.method,
      headers: {
        ...jsonHeaders({ accessToken: session.accessToken, idempotencyKey: accion.idempotencyKey }),
        ...(accion.ifMatch ? { 'If-Match': accion.ifMatch } : {}),
      },
      body: accion.body ?? undefined,
    };
    if (accion.method === 'POST' && accion.path === RUTA_CREACION_PEDIDO) {
      try {
        const pedido = await requestJson<PedidoCreado>(accion.path, solicitud);
        if (typeof pedido?.id === 'string' && pedido.id.trim()) {
          await borrarCarritoEncolado(accion.usuarioId, accion.idempotencyKey).catch(() => undefined);
          return;
        }
      } catch (error) {
        if (!(error instanceof ApiError) || error.status < 200 || error.status >= 300) {
          throw error;
        }
      }
      throw new ApiError('El servidor no devolvió el identificador del pedido.', 502);
    }
    await requestVoid(accion.path, solicitud);
    const respuesta = respuestaPedidoPendiente(accion);
    if (respuesta) {
      try {
        await quitarDetalleVendedorGuardado(accion.usuarioId, respuesta.pedidoId);
      } catch {
        throw new ApiError('No se pudo actualizar el detalle guardado.', 503);
      }
    }
  });
}

/** La cola de la app (F041). */
export const outbox = new Outbox({
  store: new SqliteOutboxStore(),
  usuarioActual: async () => (await readSession())?.usuarioId ?? null,
  enviar: enviarConSesion,
  conservarReintentosServidor: (accion) => Boolean(accion.ifMatch || respuestaPedidoPendiente(accion))
    || (accion.method === 'POST' && accion.path === RUTA_CREACION_PEDIDO)
    || (accion.method === 'POST' && accion.path === RUTA_REGISTRO_PRODUCTO)
    || (accion.method === 'PATCH' && accion.path === RUTA_EDICION_EMPRENDIMIENTO),
  nuevoId: () => randomUUID(),
});
