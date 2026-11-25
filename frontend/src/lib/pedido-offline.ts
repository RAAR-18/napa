import * as SQLite from 'expo-sqlite';
import { Platform } from 'react-native';

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
};

export type DatosCotizacionGuardada = {
  medios: MedioPagoPedido[];
  direccion: UbicacionEntregaInput | null;
};

let conexion: Promise<SQLite.SQLiteDatabase> | undefined;

function database() {
  conexion ??= (async () => {
    const db = await SQLite.openDatabaseAsync('napa.db');
    await db.execAsync(`
      CREATE TABLE IF NOT EXISTS pedido_offline (
        cuenta TEXT NOT NULL,
        clave TEXT NOT NULL,
        contenido TEXT NOT NULL,
        fecha INTEGER NOT NULL,
        PRIMARY KEY (cuenta, clave)
      )
    `);
    return db;
  })().catch((error) => {
    conexion = undefined;
    throw error;
  });
  return conexion;
}

async function guardar(cuenta: string, clave: string, contenido: unknown) {
  const db = await database();
  await db.runAsync(
    `INSERT INTO pedido_offline (cuenta, clave, contenido, fecha) VALUES (?, ?, ?, ?)
     ON CONFLICT (cuenta, clave) DO UPDATE SET contenido = excluded.contenido, fecha = excluded.fecha`,
    cuenta, clave, JSON.stringify(contenido), Date.now(),
  );
}

async function leer<T>(cuenta: string, clave: string, desde = 0): Promise<T | null> {
  const db = await database();
  const row = await db.getFirstAsync<{ contenido: string }>(
    'SELECT contenido FROM pedido_offline WHERE cuenta = ? AND clave = ? AND fecha >= ?',
    cuenta, clave, desde,
  );
  return row ? JSON.parse(row.contenido) as T : null;
}

async function borrar(cuenta: string, clave: string) {
  const db = await database();
  await db.runAsync('DELETE FROM pedido_offline WHERE cuenta = ? AND clave = ?', cuenta, clave);
}

// Una navegación rápida debe leer después del último guardado, y una confirmación
// debe borrar después de los guardados pendientes para no recuperar un pedido enviado.
let borradoresPendientes: Promise<unknown> = Promise.resolve();
function ordenarBorrador<T>(operacion: () => Promise<T>): Promise<T> {
  const resultado = borradoresPendientes.then(operacion);
  borradoresPendientes = resultado.catch(() => undefined);
  return resultado;
}

const claveCarritoWeb = (cuenta: string, negocio: string) => `napa:carrito:${JSON.stringify([cuenta, negocio])}`;

export const guardarBorradorPedido = (cuenta: string, negocio: string, carrito: CarritoPedido) =>
  ordenarBorrador(async () => {
    // Funciona también en Safari por HTTP local, donde SQLite/WASM requiere
    // funciones del navegador que solo están disponibles en contextos seguros.
    if (Platform.OS === 'web') {
      window.localStorage.setItem(claveCarritoWeb(cuenta, negocio), JSON.stringify(carrito));
      return;
    }
    await guardar(cuenta, `borrador:${negocio}`, carrito);
  });
export const leerBorradorPedido = (cuenta: string, negocio: string) =>
  ordenarBorrador(async () => {
    if (Platform.OS === 'web') {
      const contenido = window.localStorage.getItem(claveCarritoWeb(cuenta, negocio));
      return contenido ? JSON.parse(contenido) as CarritoPedido : null;
    }
    return leer<CarritoPedido>(cuenta, `borrador:${negocio}`);
  });
export const borrarBorradorPedido = (cuenta: string, negocio: string) =>
  ordenarBorrador(async () => {
    if (Platform.OS === 'web') {
      window.localStorage.removeItem(claveCarritoWeb(cuenta, negocio));
      return;
    }
    await borrar(cuenta, `borrador:${negocio}`);
  });

export async function guardarUltimaDireccion(cuenta: string, direccion: UbicacionEntregaInput) {
  if (Platform.OS === 'web') {
    window.localStorage.setItem(`napa:direccion:${cuenta}`, JSON.stringify(direccion));
    return;
  }
  await guardar(cuenta, 'ultima-direccion', direccion);
}

export async function leerUltimaDireccion(cuenta: string): Promise<UbicacionEntregaInput | null> {
  if (Platform.OS === 'web') {
    const contenido = window.localStorage.getItem(`napa:direccion:${cuenta}`);
    return contenido ? JSON.parse(contenido) as UbicacionEntregaInput : null;
  }
  return leer<UbicacionEntregaInput>(cuenta, 'ultima-direccion');
}

export const guardarCarritoEncolado = (cuenta: string, clave: string, carrito: CarritoPedido) =>
  guardar(cuenta, `envio:${clave}`, carrito);
export const leerCarritoEncolado = (cuenta: string, clave: string) =>
  leer<CarritoPedido>(cuenta, `envio:${clave}`);
export const borrarCarritoEncolado = (cuenta: string, clave: string) =>
  borrar(cuenta, `envio:${clave}`);

export const guardarCotizacionOffline = (
  cuenta: string, negocio: string, modalidad: ModalidadPedido, datos: DatosCotizacionGuardada,
) => guardar(cuenta, `cotizacion:${negocio}:${modalidad}`, datos);

export const leerCotizacionOffline = (cuenta: string, negocio: string, modalidad: ModalidadPedido) =>
  leer<DatosCotizacionGuardada>(cuenta, `cotizacion:${negocio}:${modalidad}`, Date.now() - VIGENCIA_MS);

export async function borrarCotizacionesOffline(cuenta: string) {
  const db = await database();
  await db.runAsync("DELETE FROM pedido_offline WHERE cuenta = ? AND clave LIKE 'cotizacion:%'", cuenta);
}

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
