import * as SQLite from 'expo-sqlite';

import type { DomicilioGuardado, DomicilioEnCursoGuardado, TransicionDomicilio } from './domicilios';
import type {
  PagoPedidoConfirmado,
  PedidoClienteDetalle,
  PedidoEntregado,
  PedidoVendedorDetalle,
  PedidosClientePagina,
  PedidosVendedorPagina,
} from './pedidos';

type TipoConsulta =
  | 'listado'
  | 'detalle'
  | 'detalle-vendedor'
  | 'listado-vendedor'
  | 'detalle-domiciliario'
  | 'listado-domiciliario'
  | 'confirmacion-domicilio'
  | 'confirmacion-pago-pedido'
  | 'confirmacion-entrega-directa';
export type DetallePedidoGuardado = Pick<PedidoClienteDetalle,
  'id' | 'estado' | 'modalidad' | 'reserva' | 'updatedAt' | 'emprendimiento' | 'total' | 'tarifaDomicilio' | 'pago'
> & { items: Pick<PedidoClienteDetalle['items'][number], 'productoId' | 'nombre' | 'unidad' | 'cantidad' | 'subtotal'>[] };

const VIGENCIA_MS = 7 * 24 * 60 * 60 * 1000;
const LIMITE = 50;
let databasePromise: Promise<SQLite.SQLiteDatabase> | undefined;
let ultimaEscritura = Promise.resolve();

function database() {
  databasePromise ??= (async () => {
    const db = await SQLite.openDatabaseAsync('napa.db');
    await db.execAsync(`
      CREATE TABLE IF NOT EXISTS cache_pedidos_cliente (
        cuenta TEXT NOT NULL,
        tipo TEXT NOT NULL,
        clave TEXT NOT NULL,
        contenido TEXT NOT NULL,
        actualizado_en INTEGER NOT NULL,
        PRIMARY KEY (cuenta, tipo, clave)
      );
    `);
    return db;
  })().catch((cause) => {
    databasePromise = undefined;
    throw cause;
  });
  return databasePromise;
}

function escribir(operacion: () => Promise<void>) {
  const resultado = ultimaEscritura.then(operacion);
  ultimaEscritura = resultado.catch(() => undefined);
  return resultado;
}

export function guardarConsultaPedidos(
  cuenta: string,
  tipo: TipoConsulta,
  clave: string,
  contenido: DetallePedidoGuardado | PedidosClientePagina | PedidoVendedorDetalle | PedidosVendedorPagina
    | DomicilioGuardado | DomicilioEnCursoGuardado[] | TransicionDomicilio | PagoPedidoConfirmado | PedidoEntregado,
  vigente: () => boolean,
) {
  const fecha = Date.now();
  return escribir(async () => {
    if (!vigente()) return;
    const db = await database();
    await db.withExclusiveTransactionAsync(async (tx) => {
      await tx.runAsync(
        'DELETE FROM cache_pedidos_cliente WHERE cuenta = ? AND actualizado_en <= ?',
        cuenta, fecha - VIGENCIA_MS,
      );
      await tx.runAsync(
        `INSERT INTO cache_pedidos_cliente (cuenta, tipo, clave, contenido, actualizado_en)
         VALUES (?, ?, ?, ?, ?)
         ON CONFLICT (cuenta, tipo, clave) DO UPDATE SET
           contenido = excluded.contenido, actualizado_en = excluded.actualizado_en`,
        cuenta, tipo, clave, JSON.stringify(contenido), fecha,
      );
      await tx.runAsync(
        `DELETE FROM cache_pedidos_cliente WHERE rowid IN (
           SELECT rowid FROM cache_pedidos_cliente WHERE cuenta = ? AND tipo = ?
           ORDER BY actualizado_en DESC, rowid DESC LIMIT -1 OFFSET ?
         )`,
        cuenta, tipo, LIMITE,
      );
    });
  });
}

export async function leerConsultaPedidos<T>(cuenta: string, tipo: TipoConsulta, clave: string) {
  await ultimaEscritura;
  const db = await database();
  const fila = await db.getFirstAsync<{ contenido: string; actualizado_en: number }>(
    `SELECT contenido, actualizado_en FROM cache_pedidos_cliente
     WHERE cuenta = ? AND tipo = ? AND clave = ? AND actualizado_en > ?`,
    cuenta, tipo, clave, Date.now() - VIGENCIA_MS,
  );
  return fila ? { data: JSON.parse(fila.contenido) as T, fecha: fila.actualizado_en } : null;
}

export async function listarPaginasGuardadas(cuenta: string, prefijo: string): Promise<number[]> {
  await ultimaEscritura;
  const db = await database();
  const filas = await db.getAllAsync<{ clave: string }>(
    `SELECT clave FROM cache_pedidos_cliente
     WHERE cuenta = ? AND tipo = 'listado' AND substr(clave, 1, length(?)) = ? AND actualizado_en > ?`,
    cuenta, prefijo, prefijo, Date.now() - VIGENCIA_MS,
  );
  return filas.map(({ clave }) => Number(clave.slice(prefijo.length))).sort((a, b) => a - b);
}

export function borrarPedidosGuardados(cuenta: string) {
  return escribir(async () => {
    const db = await database();
    await db.runAsync('DELETE FROM cache_pedidos_cliente WHERE cuenta = ?', cuenta);
  });
}

export function quitarDetalleGuardado(cuenta: string, tipo: 'detalle-vendedor' | 'detalle-domiciliario', id: string) {
  return escribir(async () => {
    const db = await database();
    await db.runAsync(
      'DELETE FROM cache_pedidos_cliente WHERE cuenta = ? AND tipo = ? AND clave = ?',
      cuenta, tipo, id,
    );
  });
}

export function quitarDetalleVendedorGuardado(cuenta: string, pedidoId: string) {
  return quitarDetalleGuardado(cuenta, 'detalle-vendedor', pedidoId);
}

export function quitarPedidoGuardado(cuenta: string, pedidoId: string) {
  return escribir(async () => {
    const db = await database();
    await db.withExclusiveTransactionAsync(async (tx) => {
      await tx.runAsync(
        "DELETE FROM cache_pedidos_cliente WHERE cuenta = ? AND tipo = 'detalle' AND clave = ?",
        cuenta, pedidoId,
      );
      const paginas = await tx.getAllAsync<{ clave: string; contenido: string }>(
        "SELECT clave, contenido FROM cache_pedidos_cliente WHERE cuenta = ? AND tipo = 'listado'",
        cuenta,
      );
      for (const pagina of paginas) {
        const data = JSON.parse(pagina.contenido) as PedidosClientePagina;
        const content = data.content.filter(({ id }) => id !== pedidoId);
        if (content.length !== data.content.length) {
          await tx.runAsync(
            "UPDATE cache_pedidos_cliente SET contenido = ? WHERE cuenta = ? AND tipo = 'listado' AND clave = ?",
            JSON.stringify({ ...data, content }), cuenta, pagina.clave,
          );
        }
      }
    });
  });
}
