import * as SQLite from 'expo-sqlite';

import type {
  EmprendimientoListado,
  EmprendimientosFilter,
  EmprendimientosPage,
} from './emprendimientos';

const DATABASE_NAME = 'napa.db';
const CACHE_KEY = 'emprendimientos-listado';

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

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isListingItem(value: unknown): value is EmprendimientoListado {
  if (!isRecord(value) || !isRecord(value.ubicacion)) {
    return false;
  }
  const location = value.ubicacion;
  return (
    typeof value.id === 'string' &&
    typeof value.nombre === 'string' &&
    typeof value.categoria === 'string' &&
    typeof value.tipoVendedor === 'string' &&
    typeof value.abierto === 'boolean' &&
    typeof value.horaApertura === 'string' &&
    typeof value.horaCierre === 'string' &&
    (value.distanciaMetros === null ||
      typeof value.distanciaMetros === 'number') &&
    typeof location.latitud === 'number' &&
    typeof location.longitud === 'number' &&
    (typeof location.direccion === 'string' || location.direccion === null)
  );
}

function isEmprendimientosPage(value: unknown): value is EmprendimientosPage {
  return (
    isRecord(value) &&
    Array.isArray(value.content) &&
    Number.isInteger(value.page) &&
    Number.isInteger(value.size) &&
    typeof value.totalElements === 'number' &&
    value.content.every(isListingItem)
  );
}

function cacheKey(filter: EmprendimientosFilter): string {
  const order = filter.cercania ? 'CERCANIA' : 'RECIENTES';
  return `${CACHE_KEY}:${filter.tipoVendedor ?? 'TODOS'}:${order}:${filter.radioMetros ?? 'SIN_LIMITE'}:${filter.page}`;
}

export async function saveEmprendimientosPage(
  filter: EmprendimientosFilter,
  page: EmprendimientosPage,
): Promise<void> {
  const db = await database();
  const content = page.content.map((item) => ({
    ...item,
    distanciaMetros: null,
  }));
  await db.runAsync(
    `INSERT INTO cache_lectura (clave, contenido, actualizado_at)
     VALUES (?, ?, ?)
     ON CONFLICT(clave) DO UPDATE SET
       contenido = excluded.contenido,
       actualizado_at = excluded.actualizado_at`,
    cacheKey(filter),
    JSON.stringify({ ...page, content }),
    new Date().toISOString(),
  );
}

export async function readEmprendimientosPage(
  filter: EmprendimientosFilter,
): Promise<{ page: EmprendimientosPage; updatedAt: string } | null> {
  const db = await database();
  const cached = await db.getFirstAsync<{ contenido: string; actualizado_at: string }>(
    'SELECT contenido, actualizado_at FROM cache_lectura WHERE clave = ?',
    cacheKey(filter),
  );
  if (!cached) {
    return null;
  }
  try {
    const page: unknown = JSON.parse(cached.contenido);
    if (!isEmprendimientosPage(page)) {
      throw new Error('El listado guardado no tiene el formato esperado.');
    }
    return {
      page,
      updatedAt: cached.actualizado_at,
    };
  } catch (cause) {
    throw new Error('El listado guardado no es válido y no se puede mostrar.', {
      cause,
    });
  }
}
