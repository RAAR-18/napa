import * as SQLite from 'expo-sqlite';

const DATABASE_NAME = 'napa.db';

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

export type LecturaGuardada = { contenido: string; actualizado_at: string };

export async function guardarLectura(clave: string, contenido: string): Promise<void> {
  const db = await database();
  await db.runAsync(
    `INSERT INTO cache_lectura (clave, contenido, actualizado_at)
     VALUES (?, ?, ?)
     ON CONFLICT(clave) DO UPDATE SET
       contenido = excluded.contenido,
       actualizado_at = excluded.actualizado_at`,
    clave, contenido, new Date().toISOString(),
  );
}

export async function leerLectura(clave: string): Promise<LecturaGuardada | null> {
  const db = await database();
  return db.getFirstAsync<LecturaGuardada>(
    'SELECT contenido, actualizado_at FROM cache_lectura WHERE clave = ?', clave,
  );
}

export async function borrarLectura(clave: string): Promise<void> {
  const db = await database();
  await db.runAsync('DELETE FROM cache_lectura WHERE clave = ?', clave);
}

export async function borrarRutasGuardadas(): Promise<void> {
  const db = await database();
  await db.runAsync('DELETE FROM cache_lectura WHERE clave LIKE ?', 'ruta-domicilio:%');
}
