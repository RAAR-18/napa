import * as SQLite from 'expo-sqlite';

import type { RutaDomicilio } from './domicilios';

/**
 * RF-22 (RNF28): la última ruta de cada domicilio activo, para seguir mostrándola sin señal. Usa la misma tabla de
 * lecturas guardadas que el listado de emprendimientos. Se borra cuando el domicilio termina.
 */
const DATABASE_NAME = 'napa.db';
const PREFIX = 'ruta-domicilio:';

let databasePromise: Promise<SQLite.SQLiteDatabase> | null = null;

async function database(): Promise<SQLite.SQLiteDatabase> {
  if (!databasePromise) {
    databasePromise = (async () => {
      const db = await SQLite.openDatabaseAsync(DATABASE_NAME);
      await db.execAsync(`
        CREATE TABLE IF NOT EXISTS cache_lectura (
          clave TEXT PRIMARY KEY NOT NULL,
          contenido TEXT NOT NULL,
          actualizado_at TEXT NOT NULL
        );
      `);
      return db;
    })();
  }
  try {
    return await databasePromise;
  } catch (cause) {
    databasePromise = null;
    throw cause;
  }
}

function isRuta(value: unknown): value is RutaDomicilio {
  if (typeof value !== 'object' || value === null) return false;
  const ruta = value as Partial<RutaDomicilio>;
  return (
    typeof ruta.domicilioId === 'string' &&
    typeof ruta.estado === 'string' &&
    typeof ruta.origen?.latitud === 'number' &&
    typeof ruta.destino?.latitud === 'number' &&
    Array.isArray(ruta.tramos)
  );
}

export async function guardarRuta(ruta: RutaDomicilio): Promise<void> {
  const db = await database();
  await db.runAsync(
    `INSERT INTO cache_lectura (clave, contenido, actualizado_at)
     VALUES (?, ?, ?)
     ON CONFLICT(clave) DO UPDATE SET
       contenido = excluded.contenido,
       actualizado_at = excluded.actualizado_at`,
    PREFIX + ruta.domicilioId,
    JSON.stringify(ruta),
    new Date().toISOString(),
  );
}

export async function leerRuta(
  domicilioId: string,
): Promise<{ ruta: RutaDomicilio; updatedAt: string } | null> {
  const db = await database();
  const row = await db.getFirstAsync<{ contenido: string; actualizado_at: string }>(
    'SELECT contenido, actualizado_at FROM cache_lectura WHERE clave = ?',
    PREFIX + domicilioId,
  );
  if (!row) return null;
  try {
    const ruta: unknown = JSON.parse(row.contenido);
    return isRuta(ruta) ? { ruta, updatedAt: row.actualizado_at } : null;
  } catch {
    return null;
  }
}

/** Al cerrar sesión: las rutas guardadas tienen la dirección exacta de los clientes. */
export async function borrarTodasLasRutas(): Promise<void> {
  const db = await database();
  await db.runAsync('DELETE FROM cache_lectura WHERE clave LIKE ?', `${PREFIX}%`);
}

export async function borrarRuta(domicilioId: string): Promise<void> {
  const db = await database();
  await db.runAsync('DELETE FROM cache_lectura WHERE clave = ?', PREFIX + domicilioId);
}
