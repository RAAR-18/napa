import * as SQLite from 'expo-sqlite';

import type { AccionPendiente, OutboxStore } from './types';

const DATABASE_NAME = 'napa.db';

type Fila = {
  id: string;
  method: string;
  path: string;
  body: string | null;
  idempotency_key: string;
  descripcion: string;
  status: string;
  attempts: number;
  last_error: string | null;
  next_attempt_at: number;
  created_at: number;
};

function aAccion(fila: Fila): AccionPendiente {
  return {
    id: fila.id,
    method: fila.method as AccionPendiente['method'],
    path: fila.path,
    body: fila.body,
    idempotencyKey: fila.idempotency_key,
    descripcion: fila.descripcion,
    status: fila.status as AccionPendiente['status'],
    attempts: fila.attempts,
    lastError: fila.last_error,
    nextAttemptAt: fila.next_attempt_at,
    createdAt: fila.created_at,
  };
}

/** Tabla `outbox` de foundations (F041) en la misma base local que las cachés de lectura. */
export class SqliteOutboxStore implements OutboxStore {
  private databasePromise: Promise<SQLite.SQLiteDatabase> | null = null;

  private async db(): Promise<SQLite.SQLiteDatabase> {
    if (!this.databasePromise) {
      this.databasePromise = (async () => {
        const db = await SQLite.openDatabaseAsync(DATABASE_NAME);
        await db.execAsync(`
          CREATE TABLE IF NOT EXISTS outbox (
            id TEXT PRIMARY KEY NOT NULL,
            method TEXT NOT NULL,
            path TEXT NOT NULL,
            body TEXT,
            idempotency_key TEXT NOT NULL UNIQUE,
            descripcion TEXT NOT NULL,
            status TEXT NOT NULL,
            attempts INTEGER NOT NULL DEFAULT 0,
            last_error TEXT,
            next_attempt_at INTEGER NOT NULL,
            created_at INTEGER NOT NULL
          );
        `);
        return db;
      })();
    }
    try {
      return await this.databasePromise;
    } catch (cause) {
      this.databasePromise = null;
      throw cause;
    }
  }

  async insertar(a: AccionPendiente): Promise<void> {
    const db = await this.db();
    // Si la misma acción se encola dos veces (doble toque), la clave única deja solo una.
    await db.runAsync(
      `INSERT OR IGNORE INTO outbox
         (id, method, path, body, idempotency_key, descripcion, status, attempts, last_error, next_attempt_at, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      a.id, a.method, a.path, a.body, a.idempotencyKey, a.descripcion, a.status, a.attempts, a.lastError,
      a.nextAttemptAt, a.createdAt,
    );
  }

  async listar(): Promise<AccionPendiente[]> {
    const db = await this.db();
    const filas = await db.getAllAsync<Fila>('SELECT * FROM outbox ORDER BY created_at, id');
    return filas.map(aAccion);
  }

  async actualizar(a: AccionPendiente): Promise<void> {
    const db = await this.db();
    await db.runAsync(
      'UPDATE outbox SET status = ?, attempts = ?, last_error = ?, next_attempt_at = ? WHERE id = ?',
      a.status, a.attempts, a.lastError, a.nextAttemptAt, a.id,
    );
  }

  async borrar(id: string): Promise<void> {
    const db = await this.db();
    await db.runAsync('DELETE FROM outbox WHERE id = ?', id);
  }

  async borrarTodo(): Promise<void> {
    const db = await this.db();
    await db.runAsync('DELETE FROM outbox');
  }
}
