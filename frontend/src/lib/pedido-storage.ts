import * as SQLite from 'expo-sqlite';

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

export async function guardar(cuenta: string, clave: string, contenido: unknown) {
  const db = await database();
  await db.runAsync(
    `INSERT INTO pedido_offline (cuenta, clave, contenido, fecha) VALUES (?, ?, ?, ?)
     ON CONFLICT (cuenta, clave) DO UPDATE SET contenido = excluded.contenido, fecha = excluded.fecha`,
    cuenta, clave, JSON.stringify(contenido), Date.now(),
  );
}

export async function leer<T>(cuenta: string, clave: string, desde = 0): Promise<T | null> {
  const db = await database();
  const row = await db.getFirstAsync<{ contenido: string }>(
    'SELECT contenido FROM pedido_offline WHERE cuenta = ? AND clave = ? AND fecha >= ?',
    cuenta, clave, desde,
  );
  return row ? JSON.parse(row.contenido) as T : null;
}

export async function borrar(cuenta: string, clave: string) {
  const db = await database();
  await db.runAsync('DELETE FROM pedido_offline WHERE cuenta = ? AND clave = ?', cuenta, clave);
}

export async function borrarCotizaciones(cuenta: string) {
  const db = await database();
  await db.runAsync("DELETE FROM pedido_offline WHERE cuenta = ? AND clave LIKE 'cotizacion:%'", cuenta);
}
