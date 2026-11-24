import { guardar, leer, borrar, borrarCotizaciones } from './pedido-storage';

import type { AccionPendiente } from '../database/outbox/types';
import type { CrearPedidoInput, MedioPagoPedido, ModalidadPedido, UbicacionEntregaInput } from './pedidos';
import type { Producto } from './productos';

export const RUTA_CREACION_PEDIDO = '/api/v1/pedidos';
const VIGENCIA_MS = 7 * 24 * 60 * 60 * 1000;

export type CarritoPedido = {
  items: { producto: Producto; cantidad: number }[];
  modalidad: ModalidadPedido;
  reserva: boolean;
  fechaReserva: string;
  direccionBorrador?: {
    direccion: string;
    latitud: string;
    longitud: string;
    barrio: string;
    referencia: string;
  };
};

export type DatosCotizacionGuardada = {
  medios: MedioPagoPedido[];
  direccion: UbicacionEntregaInput | null;
};

export const guardarBorradorPedido = (cuenta: string, negocio: string, carrito: CarritoPedido) =>
  guardar(cuenta, `borrador:${negocio}`, carrito);
export const leerBorradorPedido = (cuenta: string, negocio: string) =>
  leer<CarritoPedido>(cuenta, `borrador:${negocio}`);
export const borrarBorradorPedido = (cuenta: string, negocio: string) =>
  borrar(cuenta, `borrador:${negocio}`);

export const guardarCarritoEncolado = (cuenta: string, clave: string, carrito: CarritoPedido) =>
  guardar(cuenta, `envio:${clave}`, carrito);
export const leerCarritoEncolado = (cuenta: string, clave: string) =>
  leer<CarritoPedido>(cuenta, `envio:${clave}`);
export const borrarCarritoEncolado = (cuenta: string, clave: string) =>
  borrar(cuenta, `envio:${clave}`);

export const guardarCotizacionOffline = (
  cuenta: string, negocio: string, modalidad: ModalidadPedido, datos: DatosCotizacionGuardada,
) => guardarCotizacionYDireccion(cuenta, negocio, modalidad, datos);

async function guardarCotizacionYDireccion(
  cuenta: string, negocio: string, modalidad: ModalidadPedido, datos: DatosCotizacionGuardada,
) {
  await guardar(cuenta, `cotizacion:${negocio}:${modalidad}`, datos);
  if (datos.direccion) await guardar(cuenta, 'ultima-direccion', datos.direccion);
}

export const leerUltimaDireccion = (cuenta: string) =>
  leer<UbicacionEntregaInput>(cuenta, 'ultima-direccion');

export const leerCotizacionOffline = (cuenta: string, negocio: string, modalidad: ModalidadPedido) =>
  leer<DatosCotizacionGuardada>(cuenta, `cotizacion:${negocio}:${modalidad}`, Date.now() - VIGENCIA_MS);

export const borrarCotizacionesOffline = borrarCotizaciones;

export function pedidoPendiente(accion: AccionPendiente): CrearPedidoInput | null {
  if (accion.method !== 'POST' || accion.path !== RUTA_CREACION_PEDIDO || !accion.body) return null;
  try {
    const pedido = JSON.parse(accion.body) as CrearPedidoInput | null;
    return pedido && typeof pedido.emprendimientoId === 'string' && Array.isArray(pedido.items) &&
      (pedido.modalidad === 'RECOGIDA' || pedido.modalidad === 'ENTREGA_DIRECTA') ? pedido : null;
  } catch {
    return null;
  }
}

export function paramsRevisionPedido(accion: AccionPendiente, pedido: CrearPedidoInput) {
  return {
    emprendimientoId: pedido.emprendimientoId,
    tipoVendedor: pedido.modalidad === 'ENTREGA_DIRECTA' ? 'AMBULANTE' : 'PUNTO_FIJO',
    pendienteId: accion.id,
  };
}

export function prepararPedidoOffline(
  emprendimientoId: string,
  carrito: CarritoPedido,
  cotizacion: DatosCotizacionGuardada | null,
  medioPago: MedioPagoPedido | null,
  direccionConfirmada: boolean,
): CrearPedidoInput | null {
  const { items, modalidad, reserva } = carrito;
  if (reserva || modalidad === 'DOMICILIO' || !items.length || !medioPago
      || !cotizacion?.medios.includes(medioPago)
      || (modalidad === 'ENTREGA_DIRECTA' && (!direccionConfirmada || !cotizacion.direccion))) return null;
  return {
    emprendimientoId, modalidad, medioPago,
    items: items.map(({ producto, cantidad }) => ({ productoId: producto.id, cantidad })),
    totalEsperado: items.reduce((total, item) => total + Math.round(item.producto.precio * item.cantidad), 0),
    preciosUnitariosEsperados: Object.fromEntries(items.map(({ producto }) => [producto.id, producto.precio])),
    ...(modalidad === 'ENTREGA_DIRECTA' && cotizacion.direccion
      ? { ubicacionEntrega: cotizacion.direccion }
      : {}),
  };
}
