import * as SQLite from 'expo-sqlite';

import type { Emprendimiento } from './emprendimientos';

const VIGENCIA_MS = 7 * 24 * 60 * 60 * 1000;
let databasePromise: Promise<SQLite.SQLiteDatabase> | undefined;
let ultimaEscritura = Promise.resolve();

function database() {
  databasePromise ??= (async () => {
    const db = await SQLite.openDatabaseAsync('napa.db');
    await db.execAsync(`
      CREATE TABLE IF NOT EXISTS cache_emprendimiento_propio (
        cuenta TEXT PRIMARY KEY NOT NULL, contenido TEXT NOT NULL, actualizado_en INTEGER NOT NULL
      )
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

export function guardarEmprendimientoPropio(cuenta: string, valor: Emprendimiento, vigente: () => boolean) {
  return escribir(async () => {
    if (!vigente()) return;
    const db = await database();
    await db.runAsync(
      `INSERT INTO cache_emprendimiento_propio (cuenta, contenido, actualizado_en) VALUES (?, ?, ?)
       ON CONFLICT (cuenta) DO UPDATE SET contenido = excluded.contenido, actualizado_en = excluded.actualizado_en`,
      cuenta, JSON.stringify(valor), Date.now(),
    );
  });
}

export async function leerEmprendimientoPropio(cuenta: string) {
  await ultimaEscritura;
  const db = await database();
  const fila = await db.getFirstAsync<{ contenido: string; actualizado_en: number }>(
    'SELECT contenido, actualizado_en FROM cache_emprendimiento_propio WHERE cuenta = ? AND actualizado_en > ?',
    cuenta, Date.now() - VIGENCIA_MS,
  );
  return fila ? { valor: JSON.parse(fila.contenido) as Emprendimiento, fecha: fila.actualizado_en } : null;
}

export function borrarEmprendimientoPropio(cuenta: string) {
  return escribir(async () => {
    const db = await database();
    await db.runAsync('DELETE FROM cache_emprendimiento_propio WHERE cuenta = ?', cuenta);
  });
}
