import { ApiError, NetworkError } from '../../lib/api';
import { MAXIMO_REINTENTOS_SERVIDOR, Outbox, RETROCESO_BASE_MS, RETROCESO_MAXIMO_MS } from './outbox';
import type { AccionPendiente, OutboxStore } from './types';

jest.mock('expo-router', () => ({ router: { replace: jest.fn() } }));

/** Igual que la tabla SQLite: la clave de idempotencia es única. */
class MemoryStore implements OutboxStore {
  filas: AccionPendiente[] = [];
  async insertar(a: AccionPendiente) {
    if (!this.filas.some((f) => f.idempotencyKey === a.idempotencyKey)) this.filas.push({ ...a });
  }
  async listar() {
    return this.filas.map((f) => ({ ...f })).sort((a, b) => a.createdAt - b.createdAt);
  }
  async actualizar(a: AccionPendiente) {
    this.filas = this.filas.map((f) => (f.id === a.id ? { ...a } : f));
  }
  async borrar(id: string) {
    this.filas = this.filas.filter((f) => f.id !== id);
  }
  async borrarTodo() {
    this.filas = [];
  }
}

let ahora: number;
let store: MemoryStore;
let enviar: jest.Mock<Promise<void>, [AccionPendiente]>;
let outbox: Outbox;
let ids: number;

beforeEach(() => {
  ahora = 1_000_000;
  ids = 0;
  store = new MemoryStore();
  enviar = jest.fn();
  outbox = new Outbox({ store, enviar, ahora: () => ahora, nuevoId: () => `id-${++ids}` });
});

async function encolar(clave: string, descripcion = 'Calificación') {
  ahora += 1;
  return outbox.encolar({
    method: 'POST',
    path: '/api/v1/calificaciones',
    body: { puntaje: 5 },
    idempotencyKey: clave,
    descripcion,
  });
}

describe('sin red', () => {
  it('keeps the action pending with backoff and stops the pass to preserve order', async () => {
    await encolar('k-1');
    await encolar('k-2');
    enviar.mockRejectedValue(new NetworkError('Sin señal'));

    const resultado = await outbox.procesar();

    expect(resultado).toEqual({ enviadas: 0, pendientes: 2, fallidas: 0 });
    expect(enviar).toHaveBeenCalledTimes(1);
    const [primera] = await outbox.listar();
    expect(primera.attempts).toBe(1);
    expect(primera.nextAttemptAt).toBe(ahora + RETROCESO_BASE_MS);
    expect(primera.lastError).toBe('Sin señal');
  });

  it('does not retry before the backoff unless forced, and caps the wait', async () => {
    await encolar('k-1');
    enviar.mockRejectedValue(new NetworkError('Sin señal'));
    await outbox.procesar();
    await outbox.procesar();
    expect(enviar).toHaveBeenCalledTimes(1);

    await outbox.procesar({ forzar: true });
    expect(enviar).toHaveBeenCalledTimes(2);
    expect((await outbox.listar())[0].nextAttemptAt).toBe(ahora + RETROCESO_BASE_MS * 2);

    for (let i = 0; i < 20; i += 1) await outbox.procesar({ forzar: true });
    expect((await outbox.listar())[0].nextAttemptAt).toBe(ahora + RETROCESO_MAXIMO_MS);
  });
});

describe('reintento', () => {
  it('sends again with the same idempotency key once the connection is back', async () => {
    await encolar('k-1');
    enviar.mockRejectedValueOnce(new NetworkError('Sin señal')).mockResolvedValueOnce(undefined);
    await outbox.procesar();

    ahora += RETROCESO_BASE_MS;
    const resultado = await outbox.procesar();

    expect(resultado).toEqual({ enviadas: 1, pendientes: 0, fallidas: 0 });
    expect(enviar.mock.calls.map(([accion]) => accion.idempotencyKey)).toEqual(['k-1', 'k-1']);
    expect(JSON.parse(enviar.mock.calls[1][0].body ?? '')).toEqual({ puntaje: 5 });
    expect(await outbox.listar()).toEqual([]);
  });

  it('retries server errors and an expired session, but gives up on server errors after the limit', async () => {
    await encolar('k-1');
    enviar.mockRejectedValueOnce(new ApiError('Sesión', 401, 'UNAUTHORIZED'));
    await outbox.procesar();
    expect((await outbox.listar())[0].status).toBe('PENDIENTE');

    enviar.mockRejectedValue(new ApiError('Caído', 503, 'EXTERNAL_SERVICE_UNAVAILABLE'));
    for (let i = 0; i < MAXIMO_REINTENTOS_SERVIDOR; i += 1) await outbox.procesar({ forzar: true });

    expect((await outbox.listar())[0].status).toBe('FALLIDA');
  });

  it('marks a rejected action as failed and lets the user retry or discard it', async () => {
    await encolar('k-1');
    enviar.mockRejectedValueOnce(new ApiError('Solo puedes calificar pedidos entregados.', 409, 'INVALID_STATE'));
    const resultado = await outbox.procesar();

    expect(resultado.fallidas).toBe(1);
    const [fallida] = await outbox.listar();
    expect(fallida.lastError).toBe('Solo puedes calificar pedidos entregados.');
    await outbox.procesar({ forzar: true });
    expect(enviar).toHaveBeenCalledTimes(1);

    enviar.mockResolvedValueOnce(undefined);
    await outbox.reintentar(fallida.id);
    await outbox.procesar();
    expect(await outbox.listar()).toEqual([]);

    await encolar('k-2');
    const [otra] = await outbox.listar();
    await outbox.descartar(otra.id);
    expect(await outbox.listar()).toEqual([]);
  });
});

describe('duplicado', () => {
  it('treats a conflict as already applied: the first send arrived but its answer was lost', async () => {
    await encolar('k-1');
    enviar.mockRejectedValueOnce(new ApiError('Ya calificaste esto.', 409, 'CONFLICT'));

    const resultado = await outbox.procesar();

    expect(resultado).toEqual({ enviadas: 1, pendientes: 0, fallidas: 0 });
    expect(await outbox.listar()).toEqual([]);
  });

  it('keeps a single entry when the same action is queued twice', async () => {
    await encolar('k-1');
    await encolar('k-1');

    expect(await outbox.listar()).toHaveLength(1);
  });

  it('runs one pass at a time so an action is never sent twice concurrently', async () => {
    await encolar('k-1');
    let liberar: () => void = () => undefined;
    enviar.mockImplementationOnce(() => new Promise<void>((resolve) => { liberar = resolve; }));

    const primera = outbox.procesar();
    const segunda = outbox.procesar();
    await Promise.resolve();
    liberar();

    expect(await primera).toEqual(await segunda);
    expect(enviar).toHaveBeenCalledTimes(1);
  });

  it('notifies subscribers when the queue changes and clears it on logout', async () => {
    const oyente = jest.fn();
    outbox.suscribir(oyente);
    await encolar('k-1');
    enviar.mockResolvedValue(undefined);
    await outbox.procesar();
    await encolar('k-2');
    await outbox.vaciar();

    expect(oyente).toHaveBeenCalledTimes(4);
    expect(await outbox.listar()).toEqual([]);
  });
});
