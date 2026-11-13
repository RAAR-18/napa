import { jsonHeaders, requestJson } from './api';
import { withValidSession } from './auth';
import type { EstadoDomicilio, MotivoCancelacionDomicilio } from './domicilios';

export type ModalidadPedido = 'ENTREGA_DIRECTA' | 'RECOGIDA' | 'DOMICILIO';
export type MedioPagoPedido = 'EFECTIVO' | 'CUENTA_BANCARIA';
export type EstadoPedido =
  | 'PENDIENTE'
  | 'ACEPTADO'
  | 'RECHAZADO'
  | 'LISTO_PARA_RECOGER'
  | 'EN_CAMINO'
  | 'ENTREGADO'
  | 'CANCELADO';

export type ItemPedidoInput = {
  productoId: string;
  cantidad: number;
};

export type UbicacionEntregaInput = {
  latitud: number;
  longitud: number;
  direccion: string;
  referencia: string | null;
  /** Obligatorio a domicilio: el domiciliario lo ve antes de aceptar en lugar de la dirección. */
  barrio?: string | null;
};

export type CotizarPedidoInput = {
  emprendimientoId: string;
  modalidad: ModalidadPedido;
  items: ItemPedidoInput[];
  ubicacionEntrega?: UbicacionEntregaInput;
  reserva?: string;
};

export type ItemPedidoCotizado = {
  productoId: string;
  nombre: string;
  unidad: string;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
  estadoProducto: string;
};

export type PagoDomicilio = {
  medio: MedioPagoPedido;
  momento: string;
  total: number;
};

export type CotizacionPedido = {
  items: ItemPedidoCotizado[];
  subtotal: number;
  tarifaDomicilio: number | null;
  total: number;
  modalidadesDisponibles: ModalidadPedido[];
  mediosPagoDisponibles: MedioPagoPedido[];
  pagoDomicilio: PagoDomicilio | null;
  advertencias: string[];
};

export type CrearPedidoInput = CotizarPedidoInput & {
  medioPago: MedioPagoPedido;
  totalEsperado: number;
};

export type PedidoCreado = {
  id: string;
  estado: string;
  modalidad: ModalidadPedido;
  reserva: string | null;
  medioPago: MedioPagoPedido;
  items: ItemPedidoCotizado[];
  subtotal: number;
  tarifaDomicilio: number | null;
  total: number;
  ubicacionEntrega: UbicacionEntregaInput | null;
  pagoDomicilio: PagoDomicilio | null;
  createdAt: string;
};

export type FiltrosPedidosVendedor = {
  estado?: EstadoPedido;
  modalidad?: ModalidadPedido;
  reserva?: boolean;
  desde?: string;
  hasta?: string;
  page?: number;
  size?: number;
  sort?: 'createdAt,asc' | 'createdAt,desc';
};

export type PedidoVendedorListadoItem = {
  id: string;
  cliente: { nombre: string };
  createdAt: string;
  estado: EstadoPedido;
  modalidad: ModalidadPedido;
  reserva: string | null;
  total: number;
};

export type PedidosVendedorPagina = {
  content: PedidoVendedorListadoItem[];
  page: number;
  size: number;
  totalElements: number;
};

export type PedidoVendedorDetalle = {
  id: string;
  estado: EstadoPedido;
  modalidad: ModalidadPedido;
  reserva: string | null;
  createdAt: string;
  updatedAt: string;
  cliente: { nombre: string };
  items: {
    productoId: string;
    nombre: string;
    unidad: string;
    cantidad: number;
    precioUnitario: number;
    subtotal: number;
    productoEliminado: boolean;
  }[];
  subtotal: number;
  tarifaDomicilio: number | null;
  total: number;
  medioPago: MedioPagoPedido;
  pago: { estado: string } | null;
  ubicacionEntrega: UbicacionEntregaInput | null;
  /** Domicilio publicado para el pedido (RF-14); nulo si aún no se publica. */
  domicilio?: {
    id: string;
    estado: EstadoDomicilio;
    motivoCancelacion: MotivoCancelacionDomicilio | null;
  } | null;
};

export type PedidoAceptado = {
  id: string;
  estado: 'ACEPTADO';
  modalidad: ModalidadPedido;
  reserva: string | null;
  total: number;
  pago: { id: string; estado: string; medio: MedioPagoPedido; monto: number };
};

export type PedidoRechazado = {
  id: string;
  estado: 'RECHAZADO';
};

export type PagoPedidoConfirmado = {
  pedidoId: string;
  pago: {
    id: string;
    estado: 'CONFIRMADO';
    medio: MedioPagoPedido;
    monto: number;
    confirmadoAt: string;
  };
};

export function cotizarPedido(input: CotizarPedidoInput) {
  return withValidSession((session) =>
    requestJson<CotizacionPedido>('/api/v1/pedidos/cotizacion', {
      method: 'POST',
      headers: jsonHeaders({ accessToken: session.accessToken }),
      body: JSON.stringify(input),
    }),
  );
}

export function listarPedidosVendedor(filters: FiltrosPedidosVendedor = {}) {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([name, value]) => {
    if (value !== undefined) query.set(name, String(value));
  });
  const queryString = query.toString();
  const suffix = queryString ? `?${queryString}` : '';
  return withValidSession((session) =>
    requestJson<PedidosVendedorPagina>(`/api/v1/pedidos${suffix}`, {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}

export function obtenerPedidoVendedor(pedidoId: string) {
  return withValidSession((session) =>
    requestJson<PedidoVendedorDetalle>(`/api/v1/pedidos/${encodeURIComponent(pedidoId)}`, {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}

export function aceptarPedido(pedidoId: string, idempotencyKey: string) {
  return withValidSession((session) =>
    requestJson<PedidoAceptado>(`/api/v1/pedidos/${encodeURIComponent(pedidoId)}/aceptar`, {
      method: 'POST',
      headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
    }),
  );
}

export function rechazarPedido(pedidoId: string, idempotencyKey: string) {
  return withValidSession((session) =>
    requestJson<PedidoRechazado>(`/api/v1/pedidos/${encodeURIComponent(pedidoId)}/rechazar`, {
      method: 'POST',
      headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
    }),
  );
}

export function confirmarPagoPedido(pedidoId: string, idempotencyKey: string) {
  return withValidSession((session) =>
    requestJson<PagoPedidoConfirmado>(
      `/api/v1/pedidos/${encodeURIComponent(pedidoId)}/pago/confirmar`,
      {
        method: 'POST',
        headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
      },
    ),
  );
}

/** RF-36 y RF-64: una reserva solo se inicia o se marca lista desde 15 minutos antes de la hora acordada. */
export const MARGEN_RESERVA_MS = 15 * 60 * 1000;

export type PedidoTransicionado = {
  id: string;
  estado: EstadoPedido;
  modalidad: ModalidadPedido;
  reserva: string | null;
  updatedAt: string;
};

export type PedidoEntregado = {
  id: string;
  estado: 'ENTREGADO';
  modalidad: ModalidadPedido;
  updatedAt: string;
  pago: { id: string; estado: 'CONFIRMADO'; medio: MedioPagoPedido; monto: number; confirmadoAt: string };
};

function accionPedido<T>(pedidoId: string, accion: string, idempotencyKey: string, body?: unknown) {
  return withValidSession((session) =>
    requestJson<T>(`/api/v1/pedidos/${encodeURIComponent(pedidoId)}/${accion}`, {
      method: 'POST',
      headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    }),
  );
}

/** RF-36: el vendedor ambulante sale a entregar; el cliente ve el pedido en camino. */
export function iniciarEntregaDirecta(pedidoId: string, idempotencyKey: string) {
  return accionPedido<PedidoTransicionado>(pedidoId, 'iniciar-entrega', idempotencyKey);
}

/** RF-37: entrega y cobro por el medio que eligió el cliente. */
export function confirmarEntregaDirecta(pedidoId: string, idempotencyKey: string) {
  return accionPedido<PedidoEntregado>(pedidoId, 'confirmar-entrega', idempotencyKey, { cobroConfirmado: true });
}

/** RF-64: el pedido de recogida ya se puede retirar en el punto fijo. */
export function marcarListoParaRecoger(pedidoId: string, idempotencyKey: string) {
  return accionPedido<PedidoTransicionado>(pedidoId, 'marcar-listo', idempotencyKey);
}

/** RF-65: el cliente retiró el pedido y lo pagó. */
export function confirmarRetiro(pedidoId: string, idempotencyKey: string) {
  return accionPedido<PedidoEntregado>(pedidoId, 'confirmar-retiro', idempotencyKey, { cobroConfirmado: true });
}

export function crearPedido(input: CrearPedidoInput, idempotencyKey: string) {
  return withValidSession((session) =>
    requestJson<PedidoCreado>('/api/v1/pedidos', {
      method: 'POST',
      headers: jsonHeaders({
        accessToken: session.accessToken,
        idempotencyKey,
      }),
      body: JSON.stringify(input),
    }),
  );
}

export const estadoPedidoNombres: Record<EstadoPedido, string> = {
  PENDIENTE: 'Pendiente',
  ACEPTADO: 'Aceptado',
  RECHAZADO: 'Rechazado',
  LISTO_PARA_RECOGER: 'Listo para recoger',
  EN_CAMINO: 'En camino',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
};

export const modalidadPedidoNombres: Record<ModalidadPedido, string> = {
  ENTREGA_DIRECTA: 'Entrega directa',
  RECOGIDA: 'Recogida',
  DOMICILIO: 'Domicilio',
};

/** RF-47: mismos filtros que el vendedor. */
export type FiltrosPedidosCliente = FiltrosPedidosVendedor;

export type PedidoClienteListadoItem = {
  id: string;
  emprendimiento: { id: string; nombre: string };
  modalidad: ModalidadPedido;
  createdAt: string;
  estado: EstadoPedido;
  reserva: string | null;
  total: number;
};

export type PedidosClientePagina = {
  content: PedidoClienteListadoItem[];
  page: number;
  size: number;
  totalElements: number;
};

/** RF-48: como el detalle del vendedor, pero con el emprendimiento en lugar del cliente. */
export type PedidoClienteDetalle = Omit<PedidoVendedorDetalle, 'cliente'> & {
  emprendimiento: { id: string; nombre: string };
};

/** `GET /pedidos` responde según el rol de la sesión; con un cliente trae sus propios pedidos. */
export function listarPedidosCliente(filters: FiltrosPedidosCliente = {}) {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([name, value]) => {
    if (value !== undefined) query.set(name, String(value));
  });
  const queryString = query.toString();
  return withValidSession((session) =>
    requestJson<PedidosClientePagina>(`/api/v1/pedidos${queryString ? `?${queryString}` : ''}`, {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}

export function obtenerPedidoCliente(pedidoId: string) {
  return withValidSession((session) =>
    requestJson<PedidoClienteDetalle>(`/api/v1/pedidos/${encodeURIComponent(pedidoId)}`, {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}

/** RF-27: datos básicos del domiciliario; sin celular ni documento. */
export type DomiciliarioDePedido = {
  pedidoId: string;
  domicilioId: string | null;
  estadoDomicilio: EstadoDomicilio | null;
  domiciliario: {
    nombre: string;
    fotoUrl: string | null;
    /** Nula hasta que existan las calificaciones (RF-01). */
    calificacion: { promedio: number; total: number } | null;
  } | null;
};

/** Cliente del pedido o su vendedor de punto fijo. */
export function obtenerDomiciliarioPedido(pedidoId: string) {
  return withValidSession((session) =>
    requestJson<DomiciliarioDePedido>(`/api/v1/pedidos/${encodeURIComponent(pedidoId)}/domiciliario`, {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}
