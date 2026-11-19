import * as SQLite from 'expo-sqlite';

import type { Notificacion, NotificacionesPagina } from './notificaciones';

export type NotificacionGuardada = Pick<Notificacion, 'id' | 'titulo' | 'cuerpo' | 'leida' | 'createdAt'>;
export type PaginaNotificacionesGuardada = Omit<NotificacionesPagina, 'content'> & {
  content: NotificacionGuardada[];
};

const VIGENCIA_MS = 7 * 24 * 60 * 60 * 1000;
const MAX_PAGINAS = 50;
let databasePromise: Promise<SQLite.SQLiteDatabase> | undefined;
let escrituraAnterior = Promise.resolve();

function database() {
  databasePromise ??= (async () => {
    const db = await SQLite.openDatabaseAsync('napa.db');
    await db.execAsync(`
      CREATE TABLE IF NOT EXISTS cache_notificaciones (
        cuenta TEXT NOT NULL,
        filtro INTEGER NOT NULL,
        pagina INTEGER NOT NULL,
        contenido TEXT NOT NULL,
        actualizado_en INTEGER NOT NULL,
        PRIMARY KEY (cuenta, filtro, pagina)
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
  const resultado = escrituraAnterior.then(operacion);
  escrituraAnterior = resultado.catch(() => undefined);
  return resultado;
}

export function guardarPaginaNotificaciones(
  cuenta: string, soloNoLeidas: boolean, respuesta: NotificacionesPagina, vigente: () => boolean,
) {
  const { page, size, totalElements } = respuesta;
  const data: PaginaNotificacionesGuardada = {
    page, size, totalElements,
    content: respuesta.content.map(({ id, titulo, cuerpo, leida, createdAt }) => ({
      id, titulo, cuerpo, leida, createdAt,
    })),
  };
  const fecha = Date.now();
  return escribir(async () => {
    if (!vigente()) return;
    const db = await database();
    await db.withExclusiveTransactionAsync(async (tx) => {
      await tx.runAsync(
        'DELETE FROM cache_notificaciones WHERE cuenta = ? AND actualizado_en <= ?',
        cuenta, fecha - VIGENCIA_MS,
      );
      await tx.runAsync(
        `INSERT INTO cache_notificaciones (cuenta, filtro, pagina, contenido, actualizado_en)
         VALUES (?, ?, ?, ?, ?)
         ON CONFLICT (cuenta, filtro, pagina) DO UPDATE SET
           contenido = excluded.contenido, actualizado_en = excluded.actualizado_en`,
        cuenta, Number(soloNoLeidas), page, JSON.stringify(data), fecha,
      );
      await tx.runAsync(
        `DELETE FROM cache_notificaciones WHERE rowid IN (
           SELECT rowid FROM cache_notificaciones WHERE cuenta = ?
           ORDER BY actualizado_en DESC, rowid DESC LIMIT -1 OFFSET ?
         )`,
        cuenta, MAX_PAGINAS,
      );
    });
  });
}

export async function leerPaginaNotificaciones(cuenta: string, soloNoLeidas: boolean, pagina: number) {
  await escrituraAnterior;
  const db = await database();
  const fila = await db.getFirstAsync<{ contenido: string; actualizado_en: number }>(
    `SELECT contenido, actualizado_en FROM cache_notificaciones
     WHERE cuenta = ? AND filtro = ? AND pagina = ? AND actualizado_en > ?`,
    cuenta, Number(soloNoLeidas), pagina, Date.now() - VIGENCIA_MS,
  );
  return fila
    ? { data: JSON.parse(fila.contenido) as PaginaNotificacionesGuardada, fecha: fila.actualizado_en }
    : null;
}

export async function listarPaginasGuardadas(cuenta: string, soloNoLeidas: boolean) {
  await escrituraAnterior;
  const db = await database();
  const filas = await db.getAllAsync<{ pagina: number }>(
    `SELECT pagina FROM cache_notificaciones
     WHERE cuenta = ? AND filtro = ? AND actualizado_en > ? ORDER BY pagina`,
    cuenta, Number(soloNoLeidas), Date.now() - VIGENCIA_MS,
  );
  return filas.map(({ pagina }) => pagina);
}

export function borrarNotificacionesGuardadas(cuenta: string) {
  return escribir(async () => {
    const db = await database();
    await db.runAsync('DELETE FROM cache_notificaciones WHERE cuenta = ?', cuenta);
  });
}
