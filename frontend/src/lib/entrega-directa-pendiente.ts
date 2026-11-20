import { ApiError } from './api';
import type { PedidoEntregado } from './pedidos';

export const cuerpoConfirmacionEntrega = { cobroConfirmado: true } as const;

export function rutaEntregaDirecta(pedidoId: string) {
  return `/api/v1/pedidos/${encodeURIComponent(pedidoId)}/confirmar-entrega`;
}

export function entregaDirectaPendiente(path: string) {
  const coincidencia = /^\/api\/v1\/pedidos\/([^/]+)\/confirmar-entrega$/.exec(path);
  return coincidencia ? decodeURIComponent(coincidencia[1]) : null;
}

export function validarEntregaDirecta(resultado: PedidoEntregado, id: string) {
  if (resultado?.id !== id || resultado.estado !== 'ENTREGADO' ||
      resultado.modalidad !== 'ENTREGA_DIRECTA' || resultado.pago?.estado !== 'CONFIRMADO' ||
      !Number.isFinite(Date.parse(resultado.updatedAt)) ||
      !Number.isFinite(Date.parse(resultado.pago.confirmadoAt))) {
    throw new ApiError('El servidor no devolvió la confirmación de entrega y cobro.', 502);
  }
  return resultado;
}
