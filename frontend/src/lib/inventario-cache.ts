import * as SQLite from 'expo-sqlite';

import type { OrdenProductos, ProductosPage } from './productos';

const MAX_PAGINAS = 50;
const VIGENCIA_MS = 7 * 24 * 60 * 60 * 1000;

let databasePromise: Promise<SQLite.SQLiteDatabase> | undefined;
let ultimaEscritura = Promise.resolve();

function encolarEscritura(escribir: () => Promise<void>): Promise<void> {
  const escritura = ultimaEscritura.then(escribir);
  ultimaEscritura = escritura.catch(() => undefined);
  return escritura;
}

async function database(): Promise<SQLite.SQLiteDatabase> {
  databasePromise ??= (async () => {
    const db = await SQLite.openDatabaseAsync('napa.db');
    await db.execAsync(`
      CREATE TABLE IF NOT EXISTS cache_inventario (
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
  })().catch((error) => {
    databasePromise = undefined;
    throw error;
  });

  return databasePromise;
}

export async function guardarPaginaInventario(
  cuentaId: string,
  orden: OrdenProductos,
  pagina: ProductosPage,
  sesionVigente: () => boolean,
): Promise<void> {
  await encolarEscritura(async () => {
    if (!sesionVigente()) return;
    await persistirPaginaInventario(cuentaId, orden, pagina);
  });
}

async function persistirPaginaInventario(
  cuentaId: string,
  orden: OrdenProductos,
  pagina: ProductosPage,
): Promise<void> {
  const db = await database();
  const emprendimientoId = pagina.emprendimiento?.id;
  const actualizadoEn = Date.now();

  await db.withExclusiveTransactionAsync(async (tx) => {
    if (!emprendimientoId) {
      await tx.runAsync(
        'DELETE FROM cache_inventario WHERE cuenta_id = ?',
        cuentaId,
      );
      return;
    }

    await tx.runAsync(
      `DELETE FROM cache_inventario
       WHERE cuenta_id = ?
         AND (emprendimiento_id <> ? OR actualizado_en <= ?)`,
      cuentaId,
      emprendimientoId,
      actualizadoEn - VIGENCIA_MS,
    );

    await tx.runAsync(
      `INSERT INTO cache_inventario
         (cuenta_id, emprendimiento_id, orden, pagina, contenido, actualizado_en)
       VALUES (?, ?, ?, ?, ?, ?)
       ON CONFLICT (cuenta_id, emprendimiento_id, orden, pagina)
       DO UPDATE SET
         contenido = excluded.contenido,
         actualizado_en = excluded.actualizado_en`,
      cuentaId,
      emprendimientoId,
      orden,
      pagina.page,
      JSON.stringify(pagina),
      actualizadoEn,
    );

    await tx.runAsync(
      `DELETE FROM cache_inventario
       WHERE rowid IN (
         SELECT rowid FROM cache_inventario
         WHERE cuenta_id = ?
         ORDER BY actualizado_en DESC, rowid DESC
         LIMIT -1 OFFSET ?
       )`,
      cuentaId,
      MAX_PAGINAS,
    );
  });
}

export async function leerPaginaInventario(
  cuentaId: string,
  orden: OrdenProductos,
  pagina: number,
): Promise<{ data: ProductosPage; updatedAt: number } | null> {
  const db = await database();
  const fila = await db.getFirstAsync<{
    contenido: string;
    actualizado_en: number;
  }>(
    `SELECT contenido, actualizado_en
     FROM cache_inventario
     WHERE cuenta_id = ? AND orden = ? AND pagina = ?
       AND actualizado_en > ?
     ORDER BY actualizado_en DESC
     LIMIT 1`,
    cuentaId,
    orden,
    pagina,
    Date.now() - VIGENCIA_MS,
  );

  return fila
    ? {
        data: JSON.parse(fila.contenido) as ProductosPage,
        updatedAt: fila.actualizado_en,
      }
    : null;
}

export async function borrarInventarioGuardado(
  cuentaId: string,
): Promise<void> {
  await encolarEscritura(async () => {
    const db = await database();
    await db.runAsync(
      'DELETE FROM cache_inventario WHERE cuenta_id = ?',
      cuentaId,
    );
  });
}