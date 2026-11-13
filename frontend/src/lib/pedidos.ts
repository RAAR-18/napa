import { jsonHeaders, requestJson } from './api';
import { withValidSession } from './auth';

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
