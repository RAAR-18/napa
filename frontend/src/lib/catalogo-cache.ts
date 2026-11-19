import * as SQLite from 'expo-sqlite';

import type { OrdenProductos, ProductosPage } from './productos';

const VIGENCIA_MS = 7 * 24 * 60 * 60 * 1000;
const MAX_PAGINAS = 50;

let databasePromise: Promise<SQLite.SQLiteDatabase> | undefined;
let escrituraAnterior = Promise.resolve();

function database() {
  databasePromise ??= (async () => {
    const db = await SQLite.openDatabaseAsync('napa.db');
    await db.execAsync(`
      CREATE TABLE IF NOT EXISTS cache_catalogo (
        cuenta_id TEXT NOT NULL,
        emprendimiento_id TEXT NOT NULL,
        orden TEXT NOT NULL,
        pagina INTEGER NOT NULL,
        contenido TEXT NOT NULL,
        actualizado_en INTEGER NOT NULL,
        PRIMARY KEY (cuenta_id, emprendimiento_id, orden, pagina)
      );
    `);
    return db;
  })().catch((cause) => {
    databasePromise = undefined;
    throw cause;
  });
  return databasePromise;
}

function encolarEscritura(escribir: () => Promise<void>) {
  const escritura = escrituraAnterior.then(escribir);
  escrituraAnterior = escritura.catch(() => undefined);
  return escritura;
}

export function guardarPaginaCatalogo(
  cuentaId: string,
  emprendimientoId: string,
  orden: OrdenProductos,
  data: ProductosPage,
  sigueVigente: () => boolean,
): Promise<void> {
  const actualizadoEn = Date.now();
  return encolarEscritura(async () => {
    if (!sigueVigente()) return;
    const db = await database();
    await db.withExclusiveTransactionAsync(async (tx) => {
      await tx.runAsync(
        `DELETE FROM cache_catalogo WHERE cuenta_id = ? AND actualizado_en <= ?`,
        cuentaId,
        actualizadoEn - VIGENCIA_MS,
      );
      await tx.runAsync(
        `INSERT INTO cache_catalogo
           (cuenta_id, emprendimiento_id, orden, pagina, contenido, actualizado_en)
         VALUES (?, ?, ?, ?, ?, ?)
         ON CONFLICT (cuenta_id, emprendimiento_id, orden, pagina)
         DO UPDATE SET contenido = excluded.contenido, actualizado_en = excluded.actualizado_en`,
        cuentaId,
        emprendimientoId,
        orden,
        data.page,
        JSON.stringify(data),
        actualizadoEn,
      );
      await tx.runAsync(
        `DELETE FROM cache_catalogo WHERE rowid IN (
           SELECT rowid FROM cache_catalogo WHERE cuenta_id = ?
           ORDER BY actualizado_en DESC, rowid DESC LIMIT -1 OFFSET ?
         )`,
        cuentaId,
        MAX_PAGINAS,
      );
    });
  });
}

export async function leerPaginaCatalogo(
  cuentaId: string,
  emprendimientoId: string,
  orden: OrdenProductos,
  pagina: number,
): Promise<{ data: ProductosPage; updatedAt: number } | null> {
  await escrituraAnterior;
  const db = await database();
  const fila = await db.getFirstAsync<{ contenido: string; actualizado_en: number }>(
    `SELECT contenido, actualizado_en FROM cache_catalogo
     WHERE cuenta_id = ? AND emprendimiento_id = ? AND orden = ?
       AND pagina = ? AND actualizado_en > ?`,
    cuentaId,
    emprendimientoId,
    orden,
    pagina,
    Date.now() - VIGENCIA_MS,
  );
  return fila
    ? { data: JSON.parse(fila.contenido) as ProductosPage, updatedAt: fila.actualizado_en }
    : null;
}

export async function listarPaginasGuardadasCatalogo(
  cuentaId: string,
  emprendimientoId: string,
  orden: OrdenProductos,
): Promise<number[]> {
  await escrituraAnterior;
  const db = await database();
  const filas = await db.getAllAsync<{ pagina: number }>(
    `SELECT pagina FROM cache_catalogo
     WHERE cuenta_id = ? AND emprendimiento_id = ? AND orden = ? AND actualizado_en > ?
     ORDER BY pagina`,
    cuentaId,
    emprendimientoId,
    orden,
    Date.now() - VIGENCIA_MS,
  );
  return filas.map(({ pagina }) => pagina);
}

export function borrarCatalogoGuardado(cuentaId: string, emprendimientoId?: string): Promise<void> {
  return encolarEscritura(async () => {
    const db = await database();
    if (emprendimientoId === undefined) {
      await db.runAsync('DELETE FROM cache_catalogo WHERE cuenta_id = ?', cuentaId);
    } else {
      await db.runAsync(
        'DELETE FROM cache_catalogo WHERE cuenta_id = ? AND emprendimiento_id = ?',
        cuentaId,
        emprendimientoId,
      );
    }
  });
}
