import { ApiError } from './api';
import type { PagoPedidoConfirmado } from './pedidos';

export function rutaConfirmarPagoPedido(id: string) {
  return `/api/v1/pedidos/${encodeURIComponent(id)}/pago/confirmar`;
}

export function pagoPedidoPendiente(path: string) {
  const coincidencia = /^\/api\/v1\/pedidos\/([^/]+)\/pago\/confirmar$/.exec(path);
  return coincidencia ? decodeURIComponent(coincidencia[1]) : null;
}

export function validarPagoPedidoConfirmado(resultado: PagoPedidoConfirmado, pedidoId: string) {
  if (resultado?.pedidoId !== pedidoId || resultado.pago?.estado !== 'CONFIRMADO' ||
      resultado.pago.medio !== 'CUENTA_BANCARIA' ||
      !Number.isFinite(Date.parse(resultado.pago.confirmadoAt))) {
    throw new ApiError('El servidor no devolvió una confirmación de pago válida.', 502);
  }
  return resultado;
}
