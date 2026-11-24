import { randomUUID } from '../../lib/uuid';

import { ApiError, jsonHeaders, requestJson, requestVoid } from '../../lib/api';
import { readSession, withValidSession } from '../../lib/auth';
import { RUTA_EDICION_EMPRENDIMIENTO } from '../../lib/emprendimiento-pendiente';
import { RUTA_REGISTRO_PRODUCTO } from '../../lib/productos-pendientes';
import { borrarCarritoEncolado, RUTA_CREACION_PEDIDO } from '../../lib/pedido-offline';
import { respuestaPedidoPendiente } from '../../lib/respuesta-pedido-pendiente';
import { pagoPedidoPendiente, validarPagoPedidoConfirmado } from '../../lib/pago-pedido-pendiente';
import { entregaDirectaPendiente, validarEntregaDirecta } from '../../lib/entrega-directa-pendiente';
import { confirmacionDomicilio, validarConfirmacionDomicilio } from '../../lib/confirmacion-domicilio';
import type { TransicionDomicilio } from '../../lib/domicilios';
import { guardarConsultaPedidos, quitarDetalleVendedorGuardado } from '../../lib/pedidos-cliente-cache';
import type { PagoPedidoConfirmado, PedidoCreado, PedidoEntregado } from '../../lib/pedidos';
import { Outbox } from './outbox';
import { PersistentOutboxStore } from './persistent-store';
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
    const confirmacion = accion.method === 'POST' ? confirmacionDomicilio(accion.path) : null;
    const pagoPedidoId = accion.method === 'POST' ? pagoPedidoPendiente(accion.path) : null;
    const entregaDirecta = accion.method === 'POST' ? entregaDirectaPendiente(accion.path) : null;
    if (confirmacion || pagoPedidoId || entregaDirecta) {
      let resultado: TransicionDomicilio | PagoPedidoConfirmado | PedidoEntregado;
      try {
        resultado = await requestJson<typeof resultado>(accion.path, solicitud);
      } catch (error) {
        if (!(error instanceof ApiError) || error.status < 200 || error.status >= 300) throw error;
        throw new ApiError('No se pudo leer la confirmación del servidor.', 502);
      }
      if (confirmacion) {
        validarConfirmacionDomicilio(resultado as TransicionDomicilio, confirmacion.id, confirmacion.paso);
      } else if (pagoPedidoId) {
        validarPagoPedidoConfirmado(resultado as PagoPedidoConfirmado, pagoPedidoId);
      } else if (entregaDirecta) {
        validarEntregaDirecta(resultado as PedidoEntregado, entregaDirecta);
      }

      const tipo = confirmacion ? 'confirmacion-domicilio'
        : pagoPedidoId ? 'confirmacion-pago-pedido'
        : 'confirmacion-entrega-directa';

      try {
        await guardarConsultaPedidos(accion.usuarioId, tipo, accion.path, resultado, () => true);
      } catch {
        throw new ApiError('No se pudo guardar la confirmación del servidor.', 503);
      }
      return;
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
  store: new PersistentOutboxStore(),
  usuarioActual: async () => (await readSession())?.usuarioId ?? null,
  enviar: enviarConSesion,
  conservarReintentosServidor: (accion) => Boolean(
    accion.ifMatch
    || respuestaPedidoPendiente(accion)
    || confirmacionDomicilio(accion.path)
    || pagoPedidoPendiente(accion.path)
    || entregaDirectaPendiente(accion.path)
  )
    || (accion.method === 'POST' && accion.path === RUTA_CREACION_PEDIDO)
    || (accion.method === 'POST' && accion.path === RUTA_REGISTRO_PRODUCTO)
    || (accion.method === 'PATCH' && accion.path === RUTA_EDICION_EMPRENDIMIENTO),
  nuevoId: () => randomUUID(),
});
