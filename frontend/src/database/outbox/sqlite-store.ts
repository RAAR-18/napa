import * as SQLite from 'expo-sqlite';

import { pagoPedidoPendiente } from '../../lib/pago-pedido-pendiente';
import { entregaDirectaPendiente } from '../../lib/entrega-directa-pendiente';
import { confirmacionDomicilio } from '../../lib/confirmacion-domicilio';
import { respuestaPedidoPendiente } from '../../lib/respuesta-pedido-pendiente';
import type { AccionPendiente, OutboxStore } from './types';

const DATABASE_NAME = 'napa.db';

type Fila = {
  id: string;
  usuario_id: string;
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
  if_match?: string | null;
  error_status?: number | null;
};

function aAccion(fila: Fila): AccionPendiente {
  return {
    id: fila.id,
    usuarioId: fila.usuario_id,
    method: fila.method as AccionPendiente['method'],
    path: fila.path,
    body: fila.body,
    idempotencyKey: fila.idempotency_key,
    ...(fila.if_match == null ? {} : { ifMatch: fila.if_match }),
    ...(fila.error_status == null ? {} : { errorStatus: fila.error_status }),
    descripcion: fila.descripcion,
    status: fila.status as AccionPendiente['status'],
    attempts: fila.attempts,
    lastError: fila.last_error,
    nextAttemptAt: fila.next_attempt_at,
    createdAt: fila.created_at,
  };
}

/** Cola por cuenta; la tabla antigua sin propietario se conserva aislada, sin reasignar sus filas. */
export class SqliteOutboxStore implements OutboxStore {
  private databasePromise: Promise<SQLite.SQLiteDatabase> | null = null;

  private async db(): Promise<SQLite.SQLiteDatabase> {
    if (!this.databasePromise) {
      this.databasePromise = (async () => {
        const db = await SQLite.openDatabaseAsync(DATABASE_NAME);
        await db.execAsync(`
          CREATE TABLE IF NOT EXISTS outbox_por_cuenta (
            id TEXT PRIMARY KEY NOT NULL,
            usuario_id TEXT NOT NULL,
            method TEXT NOT NULL,
            path TEXT NOT NULL,
            body TEXT,
            idempotency_key TEXT NOT NULL,
            descripcion TEXT NOT NULL,
            status TEXT NOT NULL,
            attempts INTEGER NOT NULL DEFAULT 0,
            last_error TEXT,
            next_attempt_at INTEGER NOT NULL,
            created_at INTEGER NOT NULL,
            UNIQUE (usuario_id, idempotency_key)
          );
        `);
        const columnas = await db.getAllAsync<{ name: string }>('PRAGMA table_info(outbox_por_cuenta)');
        if (!columnas.some(({ name }) => name === 'if_match')) {
          await db.execAsync('ALTER TABLE outbox_por_cuenta ADD COLUMN if_match TEXT');
        }
        if (!columnas.some(({ name }) => name === 'error_status')) {
          await db.execAsync('ALTER TABLE outbox_por_cuenta ADD COLUMN error_status INTEGER');
        }
        await db.execAsync(`
          CREATE UNIQUE INDEX IF NOT EXISTS outbox_cambio_por_producto
          ON outbox_por_cuenta (usuario_id, CASE WHEN method = 'POST'
            THEN substr(path, 1, length(path) - length('/inventario')) ELSE path END)
          WHERE if_match IS NOT NULL AND (
            method = 'PATCH' AND path GLOB '/api/v1/productos/*'
            OR method = 'POST' AND path GLOB '/api/v1/productos/*/inventario'
          )
        `);
        await db.execAsync(`
          CREATE UNIQUE INDEX IF NOT EXISTS outbox_edicion_emprendimiento
          ON outbox_por_cuenta (usuario_id, json_extract(body, '$.base.emprendimientoId'))
          WHERE method = 'PATCH' AND path = '/api/v1/emprendimientos/me' AND json_valid(body)
        `);
        await db.execAsync(`
          CREATE UNIQUE INDEX IF NOT EXISTS outbox_respuesta_por_pedido
          ON outbox_por_cuenta (usuario_id, substr(path, 1, length(path) - CASE WHEN path GLOB '*/aceptar' THEN 8 ELSE 9 END))
          WHERE method = 'POST' AND (
            path GLOB '/api/v1/pedidos/*/aceptar' OR path GLOB '/api/v1/pedidos/*/rechazar'
          )
        `);
        await db.execAsync(`
          CREATE UNIQUE INDEX IF NOT EXISTS outbox_confirmacion_por_domicilio
          ON outbox_por_cuenta (usuario_id, path)
          WHERE method = 'POST' AND (
            path GLOB '/api/v1/domicilios/*/confirmar-recogida'
            OR path GLOB '/api/v1/domicilios/*/confirmar-salida'
          )
        `);
        await db.execAsync(`
          CREATE UNIQUE INDEX IF NOT EXISTS outbox_confirmacion_pago_por_pedido
          ON outbox_por_cuenta (usuario_id, path)
          WHERE method = 'POST' AND path GLOB '/api/v1/pedidos/*/pago/confirmar';

          CREATE UNIQUE INDEX IF NOT EXISTS outbox_entrega_directa_por_pedido
          ON outbox_por_cuenta (usuario_id, path)
          WHERE method = 'POST' AND path GLOB '/api/v1/pedidos/*/confirmar-entrega';
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
    const resultado = await db.runAsync(
      `INSERT OR IGNORE INTO outbox_por_cuenta
         (id, usuario_id, method, path, body, idempotency_key, descripcion, status, attempts, last_error, next_attempt_at, created_at, if_match)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      a.id, a.usuarioId, a.method, a.path, a.body, a.idempotencyKey, a.descripcion, a.status, a.attempts, a.lastError,
      a.nextAttemptAt, a.createdAt, a.ifMatch ?? null,
    );
    if (resultado.changes === 0 && a.method === 'PATCH' && a.path === '/api/v1/emprendimientos/me') {
      throw new Error('Ya hay una edición pendiente para este emprendimiento.');
    }
    if (resultado.changes === 0 && respuestaPedidoPendiente(a)) {
      throw new Error('Este pedido ya tiene una decisión pendiente o por revisar.');
    }
    if (resultado.changes === 0 && a.method === 'POST' && confirmacionDomicilio(a.path)) {
      throw new Error('Este domicilio ya tiene registrada esa confirmación pendiente.');
    }
    if (resultado.changes === 0 && a.method === 'POST' && pagoPedidoPendiente(a.path)) {
      throw new Error('Este pedido ya tiene registrada una confirmación de pago.');
    }
    if (resultado.changes === 0 && a.method === 'POST' && entregaDirectaPendiente(a.path)) {
      throw new Error('Este pedido ya tiene una confirmación de entrega y cobro.');
    }
    if (a.ifMatch && resultado.changes === 0) {
      throw new Error('Este producto ya tiene un cambio pendiente.');
    }
  }

  async listar(): Promise<AccionPendiente[]> {
    const db = await this.db();
    const filas = await db.getAllAsync<Fila>('SELECT * FROM outbox_por_cuenta ORDER BY created_at, id');
    return filas.map(aAccion);
  }

  async actualizar(a: AccionPendiente): Promise<void> {
    const db = await this.db();
    await db.runAsync(
      'UPDATE outbox_por_cuenta SET status = ?, attempts = ?, last_error = ?, next_attempt_at = ?, error_status = ? WHERE id = ?',
      a.status, a.attempts, a.lastError, a.nextAttemptAt, a.errorStatus ?? null, a.id,
    );
  }

  async reemplazarFallida(a: AccionPendiente, claveAnterior: string): Promise<boolean> {
    const db = await this.db();
    const result = await db.runAsync(
      `UPDATE outbox_por_cuenta
       SET body = ?, idempotency_key = ?, descripcion = ?, status = ?, attempts = ?,
           last_error = ?, next_attempt_at = ?, created_at = ?, if_match = ?, error_status = NULL
       WHERE id = ? AND usuario_id = ? AND status = 'FALLIDA' AND idempotency_key = ?`,
      a.body, a.idempotencyKey, a.descripcion, a.status, a.attempts, a.lastError,
      a.nextAttemptAt, a.createdAt, a.ifMatch ?? null, a.id, a.usuarioId, claveAnterior,
    );
    return result.changes === 1;
  }

  async iniciarPrimerIntento(id: string): Promise<boolean> {
    const db = await this.db();
    const result = await db.runAsync(
      "UPDATE outbox_por_cuenta SET attempts = 1 WHERE id = ? AND status = 'PENDIENTE' AND attempts = 0", id,
    );
    return result.changes === 1;
  }

  async borrarSinIntentos(id: string, usuarioId: string): Promise<boolean> {
    const db = await this.db();
    const result = await db.runAsync(
      "DELETE FROM outbox_por_cuenta WHERE id = ? AND usuario_id = ? AND status = 'PENDIENTE' AND attempts = 0",
      id, usuarioId,
    );
    return result.changes === 1;
  }

  async borrar(id: string): Promise<void> {
    const db = await this.db();
    await db.runAsync('DELETE FROM outbox_por_cuenta WHERE id = ?', id);
  }

  async borrarTodo(): Promise<void> {
    const db = await this.db();
    await db.runAsync('DELETE FROM outbox_por_cuenta');
  }
}
