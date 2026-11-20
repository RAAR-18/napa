import type { AccionPendiente } from '../database/outbox/types';

export function rutaRespuestaPedido(pedidoId: string, decision: 'accept' | 'reject') {
  return `/api/v1/pedidos/${encodeURIComponent(pedidoId)}/${decision === 'accept' ? 'aceptar' : 'rechazar'}`;
}

export function respuestaPedidoPendiente(accion: Pick<AccionPendiente, 'path' | 'method'>) {
  if (accion.method !== 'POST') return null;
  const match = /^\/api\/v1\/pedidos\/([^/]+)\/(aceptar|rechazar)$/.exec(accion.path);
  if (!match) return null;
  return { pedidoId: decodeURIComponent(match[1]), decision: match[2] === 'aceptar' ? 'accept' : 'reject' };
}
