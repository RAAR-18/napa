import { ApiError, NetworkError } from '../../lib/api';
import type { AccionPendiente, MetodoEnvio, OutboxStore } from './types';

/** Envía una acción con su clave; resuelve si el servidor la aceptó y lanza el error tal cual si no. */
export type EnviarAccion = (accion: AccionPendiente) => Promise<void>;

export const RETROCESO_BASE_MS = 5_000;
export const RETROCESO_MAXIMO_MS = 5 * 60_000;
/** Errores del servidor (5xx) que se reintentan antes de dar la acción por fallida. */
export const MAXIMO_REINTENTOS_SERVIDOR = 8;

/**
 * Respuestas que significan que la acción ya quedó aplicada en un envío anterior cuya respuesta se perdió
 * (por ejemplo, la calificación o el reporte ya existen): se dan por enviadas.
 */
const YA_APLICADA = new Set(['CONFLICT', 'ALREADY_CONFIRMED', 'ALREADY_PUBLISHED']);

export type ResultadoProceso = { enviadas: number; pendientes: number; fallidas: number };

type Opciones = {
  store: OutboxStore;
  enviar: EnviarAccion;
  ahora?: () => number;
  nuevoId: () => string;
};

/**
 * F041 (RNF27, RNF28): cola de salida. Cada acción se reenvía en orden de creación con la misma
 * `Idempotency-Key` hasta que el servidor la acepte, con retroceso exponencial.
 */
export class Outbox {
  private readonly store: OutboxStore;
  private readonly enviar: EnviarAccion;
  private readonly ahora: () => number;
  private readonly nuevoId: () => string;
  private enCurso: Promise<ResultadoProceso> | null = null;
  private readonly oyentes = new Set<() => void>();

  constructor({ store, enviar, ahora = Date.now, nuevoId }: Opciones) {
    this.store = store;
    this.enviar = enviar;
    this.ahora = ahora;
    this.nuevoId = nuevoId;
  }

  /** Guarda la acción para enviarla después; `idempotencyKey` debe ser la del primer intento. */
  async encolar(input: {
    method: MetodoEnvio;
    path: string;
    body?: unknown;
    idempotencyKey: string;
    descripcion: string;
  }): Promise<AccionPendiente> {
    const accion: AccionPendiente = {
      id: this.nuevoId(),
      method: input.method,
      path: input.path,
      body: input.body === undefined ? null : JSON.stringify(input.body),
      idempotencyKey: input.idempotencyKey,
      descripcion: input.descripcion,
      status: 'PENDIENTE',
      attempts: 0,
      lastError: null,
      nextAttemptAt: this.ahora(),
      createdAt: this.ahora(),
    };
    await this.store.insertar(accion);
    this.avisar();
    return accion;
  }

  listar(): Promise<AccionPendiente[]> {
    return this.store.listar();
  }

  async descartar(id: string): Promise<void> {
    await this.store.borrar(id);
    this.avisar();
  }

  /** Al cerrar sesión: las acciones pertenecen a la cuenta que las creó. */
  async vaciar(): Promise<void> {
    await this.store.borrarTodo();
    this.avisar();
  }

  /** Vuelve a poner una acción fallida en la cola para intentarla ya. */
  async reintentar(id: string): Promise<void> {
    const accion = (await this.store.listar()).find((a) => a.id === id);
    if (!accion) return;
    await this.store.actualizar({ ...accion, status: 'PENDIENTE', lastError: null, nextAttemptAt: this.ahora() });
    this.avisar();
  }

  /** Notifica cambios en la cola (para contadores en pantalla). */
  suscribir(oyente: () => void): () => void {
    this.oyentes.add(oyente);
    return () => this.oyentes.delete(oyente);
  }

  /** Una sola pasada a la vez; si ya hay una en curso, se espera esa. */
  procesar(opciones: { forzar?: boolean } = {}): Promise<ResultadoProceso> {
    this.enCurso ??= this.pasada(opciones.forzar ?? false).finally(() => {
      this.enCurso = null;
    });
    return this.enCurso;
  }

  private async pasada(forzar: boolean): Promise<ResultadoProceso> {
    let enviadas = 0;
    const acciones = await this.store.listar();
    for (const accion of acciones) {
      if (accion.status !== 'PENDIENTE') continue;
      if (!forzar && accion.nextAttemptAt > this.ahora()) continue;
      const resultado = await this.intentar(accion);
      if (resultado === 'enviada') {
        enviadas += 1;
      } else if (resultado === 'sin-conexion') {
        // Sin señal no tiene sentido intentar las demás en esta pasada; el orden se conserva.
        break;
      }
    }
    const restantes = await this.store.listar();
    if (enviadas > 0 || restantes.length !== acciones.length) this.avisar();
    return {
      enviadas,
      pendientes: restantes.filter((a) => a.status === 'PENDIENTE').length,
      fallidas: restantes.filter((a) => a.status === 'FALLIDA').length,
    };
  }

  private async intentar(accion: AccionPendiente): Promise<'enviada' | 'reintentar' | 'sin-conexion' | 'fallida'> {
    try {
      await this.enviar(accion);
      await this.store.borrar(accion.id);
      return 'enviada';
    } catch (cause) {
      if (cause instanceof ApiError && cause.code && YA_APLICADA.has(cause.code)) {
        await this.store.borrar(accion.id);
        return 'enviada';
      }
      const attempts = accion.attempts + 1;
      const mensaje = cause instanceof Error ? cause.message : 'Error desconocido';
      if (cause instanceof NetworkError) {
        await this.store.actualizar({ ...accion, attempts, lastError: mensaje, nextAttemptAt: this.siguiente(attempts) });
        return 'sin-conexion';
      }
      const reintentable =
        cause instanceof ApiError &&
        (cause.status === 401 || cause.status === 408 || cause.status === 429 || cause.status >= 500);
      if (reintentable && (cause.status < 500 || attempts < MAXIMO_REINTENTOS_SERVIDOR)) {
        await this.store.actualizar({ ...accion, attempts, lastError: mensaje, nextAttemptAt: this.siguiente(attempts) });
        return 'reintentar';
      }
      // El servidor la rechazó por sus datos o su estado: reenviarla daría el mismo resultado.
      await this.store.actualizar({ ...accion, attempts, status: 'FALLIDA', lastError: mensaje });
      return 'fallida';
    }
  }

  private siguiente(attempts: number) {
    return this.ahora() + Math.min(RETROCESO_MAXIMO_MS, RETROCESO_BASE_MS * 2 ** (attempts - 1));
  }

  private avisar() {
    this.oyentes.forEach((oyente) => oyente());
  }
}
