import { ApiError, NetworkError } from '../../lib/api';
import { pagoPedidoPendiente } from '../../lib/pago-pedido-pendiente';
import { confirmacionDomicilio } from '../../lib/confirmacion-domicilio';
import { respuestaPedidoPendiente } from '../../lib/respuesta-pedido-pendiente';
import type { AccionPendiente, MetodoEnvio, OutboxStore } from './types';

/** Envía una acción con su clave; resuelve si el servidor la aceptó y lanza el error tal cual si no. */
export type EnviarAccion = (accion: AccionPendiente) => Promise<void>;

export const RETROCESO_BASE_MS = 5_000;
export const RETROCESO_MAXIMO_MS = 5 * 60_000;
/** Errores del servidor (5xx) que se reintentan antes de dar la acción por fallida. */
export const MAXIMO_REINTENTOS_SERVIDOR = 8;

// Un conflicto no prueba que esta acción haya sido aplicada.

export type ResultadoProceso = { enviadas: number; pendientes: number; fallidas: number };

type Opciones = {
  store: OutboxStore;
  usuarioActual: () => Promise<string | null>;
  enviar: EnviarAccion;
  ahora?: () => number;
  nuevoId: () => string;
  conservarReintentosServidor?: (accion: AccionPendiente) => boolean;
};

/**
 * F041 (RNF27, RNF28): cola de salida. Cada acción se reenvía en orden de creación con la misma
 * `Idempotency-Key` hasta que el servidor la acepte, con retroceso exponencial.
 */
export class Outbox {
  private readonly store: OutboxStore;
  private readonly usuarioActual: () => Promise<string | null>;
  private readonly enviar: EnviarAccion;
  private readonly ahora: () => number;
  private readonly nuevoId: () => string;
  private readonly conservarReintentosServidor: (accion: AccionPendiente) => boolean;
  private enCurso: Promise<ResultadoProceso> | null = null;
  private readonly oyentes = new Set<() => void>();

  constructor({ store, enviar, usuarioActual, ahora = Date.now, nuevoId, conservarReintentosServidor = () => false }: Opciones) {
    this.store = store;
    this.usuarioActual = usuarioActual;
    this.enviar = enviar;
    this.ahora = ahora;
    this.nuevoId = nuevoId;
    this.conservarReintentosServidor = conservarReintentosServidor;
  }

  /** Captura el propietario antes de iniciar una petición que puede fallar por red. */
  async propietario(): Promise<string> {
    const id = await this.usuarioActual();
    if (!id) throw new Error('Inicia sesión nuevamente para guardar acciones pendientes.');
    return id;
  }

  /** Guarda la acción con su propietario original y la clave del primer intento. */
  async encolar(input: {
    usuarioId: string;
    method: MetodoEnvio;
    path: string;
    body?: unknown;
    ifMatch?: string;
    idempotencyKey: string;
    descripcion: string;
    intentoPrevio?: boolean;
  }): Promise<AccionPendiente> {
    const usuarioId = input.usuarioId;
    if (!usuarioId) throw new Error('La acción debe tener propietario.');
    const accion: AccionPendiente = {
      usuarioId,
      id: this.nuevoId(),
      method: input.method,
      path: input.path,
      body: input.body === undefined ? null : JSON.stringify(input.body),
      ...(input.ifMatch ? { ifMatch: input.ifMatch } : {}),
      idempotencyKey: input.idempotencyKey,
      descripcion: input.descripcion,
      status: 'PENDIENTE',
      attempts: input.intentoPrevio ? 1 : 0,
      lastError: null,
      nextAttemptAt: this.ahora(),
      createdAt: this.ahora(),
    };
    await this.store.insertar(accion);
    this.avisar();
    return accion;
  }

  async listar(): Promise<AccionPendiente[]> {
    const usuarioId = await this.usuarioActual();
    if (!usuarioId) return [];
    const acciones = await this.store.listar();
    if (usuarioId !== await this.usuarioActual()) return [];
    return acciones.filter((a) => a.usuarioId === usuarioId);
  }

  async descartar(id: string): Promise<void> {
    const accion = (await this.listar()).find((a) => a.id === id);
    if (!accion) return;
    if (respuestaPedidoPendiente(accion) && accion.status === 'PENDIENTE') {
      if (!await this.store.borrarSinIntentos?.(id, accion.usuarioId)) {
        throw new Error('El envío ya se intentó. Consulta el estado del pedido antes de decidir de nuevo.');
      }
    } else {
      await this.store.borrar(id);
    }
    this.avisar();
  }

  /** Descarte explícito solo de los pendientes de la cuenta activa; no usar al cerrar sesión. */
  async vaciar(): Promise<void> {
    for (const accion of await this.listar()) {
      if (!respuestaPedidoPendiente(accion) && !confirmacionDomicilio(accion.path) && !pagoPedidoPendiente(accion.path)) await this.store.borrar(accion.id);
    }
    this.avisar();
  }

  /** Vuelve a poner una acción fallida en la cola para intentarla ya. */
  async reintentar(id: string): Promise<void> {
    const accion = (await this.listar()).find((a) => a.id === id);
    if (!accion || respuestaPedidoPendiente(accion) || confirmacionDomicilio(accion.path) || pagoPedidoPendiente(accion.path)) return;
    await this.store.actualizar({ ...accion, status: 'PENDIENTE', lastError: null, nextAttemptAt: this.ahora() });
    this.avisar();
  }

  /** Una corrección sustituye un rechazo definitivo con un cuerpo y una clave nuevos. */
  async corregirFallida(accion: AccionPendiente, input: {
    body: unknown;
    ifMatch?: string;
    idempotencyKey: string;
    descripcion: string;
  }): Promise<void> {
    if (accion.status !== 'FALLIDA' || accion.usuarioId !== await this.usuarioActual()) {
      throw new Error('Este registro ya no está disponible para corregir.');
    }
    const fecha = this.ahora();
    const modificada = await this.store.reemplazarFallida({
      ...accion,
      body: JSON.stringify(input.body),
      ...(input.ifMatch ? { ifMatch: input.ifMatch, errorStatus: undefined } : {}),
      descripcion: input.descripcion,
      idempotencyKey: input.idempotencyKey,
      status: 'PENDIENTE',
      attempts: 0,
      lastError: null,
      nextAttemptAt: fecha,
      createdAt: fecha,
    }, accion.idempotencyKey);
    if (!modificada) throw new Error('Este registro ya no está disponible para corregir.');
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
    const acciones = await this.listar();
    for (const accion of acciones) {
      if (accion.usuarioId !== await this.usuarioActual()) break;
      if (accion.status !== 'PENDIENTE') continue;
      if (!forzar && accion.nextAttemptAt > this.ahora()) continue;
      if (respuestaPedidoPendiente(accion) && accion.attempts === 0 && this.store.iniciarPrimerIntento) {
        if (!await this.store.iniciarPrimerIntento(accion.id)) continue;
        this.avisar();
      }
      const resultado = await this.intentar(accion);
      if (resultado === 'enviada') {
        enviadas += 1;
      } else if (resultado === 'sin-conexion') {
        // Sin señal no tiene sentido intentar las demás en esta pasada; el orden se conserva.
        break;
      }
    }
    const restantes = await this.listar();
    this.avisar();
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
      const attempts = accion.attempts + 1;
      const mensaje = cause instanceof Error ? cause.message : 'Error desconocido';
      if (cause instanceof NetworkError) {
        await this.store.actualizar({ ...accion, attempts, lastError: mensaje, nextAttemptAt: this.siguiente(attempts) });
        return 'sin-conexion';
      }
      const reintentable =
        cause instanceof ApiError &&
        ((cause.status === 409 && cause.code === 'IDEMPOTENCY_IN_PROGRESS') ||
          cause.status === 401 || cause.status === 408 || cause.status === 429 || cause.status >= 500);
      if (reintentable && (cause.status < 500 || attempts < MAXIMO_REINTENTOS_SERVIDOR || this.conservarReintentosServidor(accion))) {
        await this.store.actualizar({ ...accion, attempts, lastError: mensaje, nextAttemptAt: this.siguiente(attempts) });
        return 'reintentar';
      }
      // El servidor la rechazó por sus datos o su estado: reenviarla daría el mismo resultado.
      await this.store.actualizar({ ...accion, attempts, status: 'FALLIDA', lastError: mensaje,
        ...(cause instanceof ApiError && (accion.ifMatch || this.conservarReintentosServidor(accion)) ? { errorStatus: cause.details.estadoActual === 'ELIMINADO' ? 410 : cause.status } : {}) });
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
