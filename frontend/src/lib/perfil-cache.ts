import * as SQLite from 'expo-sqlite';

import type { PerfilUsuario } from './usuarios';

export type PerfilGuardado = Pick<PerfilUsuario, 'nombre' | 'rol' | 'celular' | 'correo'> & {
  direccion: string | null;
};

const VIGENCIA_MS = 7 * 24 * 60 * 60 * 1000;
let databasePromise: Promise<SQLite.SQLiteDatabase> | undefined;
let ultimaEscritura = Promise.resolve();

function database() {
  databasePromise ??= (async () => {
    const db = await SQLite.openDatabaseAsync('napa.db');
    await db.execAsync(`
      CREATE TABLE IF NOT EXISTS cache_perfil (
        cuenta_id TEXT PRIMARY KEY NOT NULL,
        contenido TEXT NOT NULL,
        actualizado_en INTEGER NOT NULL
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

export function guardarPerfil(perfil: PerfilUsuario, vigente: () => boolean) {
  const { id, nombre, rol, celular, correo, ubicacion } = perfil;
  const contenido: PerfilGuardado = { nombre, rol, celular, correo, direccion: ubicacion?.direccion ?? null };
  const fecha = Date.now();
  return escribir(async () => {
    if (!vigente()) return;
    const db = await database();
    await db.runAsync(
      `INSERT INTO cache_perfil (cuenta_id, contenido, actualizado_en) VALUES (?, ?, ?)
       ON CONFLICT (cuenta_id) DO UPDATE SET
         contenido = excluded.contenido, actualizado_en = excluded.actualizado_en`,
      id, JSON.stringify(contenido), fecha,
    );
  });
}

export async function leerPerfilGuardado(cuentaId: string) {
  await ultimaEscritura;
  const db = await database();
  const fila = await db.getFirstAsync<{ contenido: string; actualizado_en: number }>(
    'SELECT contenido, actualizado_en FROM cache_perfil WHERE cuenta_id = ? AND actualizado_en > ?',
    cuentaId, Date.now() - VIGENCIA_MS,
  );
  return fila
    ? { perfil: JSON.parse(fila.contenido) as PerfilGuardado, fecha: fila.actualizado_en }
    : null;
}

export function borrarPerfilGuardado(cuentaId: string) {
  return escribir(async () => {
    const db = await database();
    await db.runAsync('DELETE FROM cache_perfil WHERE cuenta_id = ?', cuentaId);
  });
}
