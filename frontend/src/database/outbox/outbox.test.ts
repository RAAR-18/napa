import { ApiError, NetworkError } from '../../lib/api';
import { MAXIMO_REINTENTOS_SERVIDOR, Outbox, RETROCESO_BASE_MS, RETROCESO_MAXIMO_MS } from './outbox';
import type { AccionPendiente, OutboxStore } from './types';

jest.mock('expo-router', () => ({ router: { replace: jest.fn() } }));

/** Igual que la tabla SQLite: la clave de idempotencia es única. */
class MemoryStore implements OutboxStore {
  filas: AccionPendiente[] = [];
  async insertar(a: AccionPendiente) {
    if (!this.filas.some((f) => f.usuarioId === a.usuarioId && f.idempotencyKey === a.idempotencyKey)) this.filas.push({ ...a });
  }
  async listar() {
    return this.filas.map((f) => ({ ...f })).sort((a, b) => a.createdAt - b.createdAt);
  }
  async actualizar(a: AccionPendiente) {
    this.filas = this.filas.map((f) => (f.id === a.id ? { ...a } : f));
  }
  async reemplazarFallida(a: AccionPendiente, claveAnterior: string) {
    const anterior = this.filas.find((fila) => fila.id === a.id && fila.usuarioId === a.usuarioId);
    if (anterior?.status !== 'FALLIDA' || anterior.idempotencyKey !== claveAnterior) return false;
    await this.actualizar(a);
    return true;
  }
  async iniciarPrimerIntento(id: string) {
    const fila = this.filas.find((f) => f.id === id && f.attempts === 0 && f.status === 'PENDIENTE');
    if (!fila) return false;
    fila.attempts = 1;
    return true;
  }
  async borrarSinIntentos(id: string, usuarioId: string) {
    const fila = this.filas.find((f) => f.id === id && f.usuarioId === usuarioId &&
      f.status === 'PENDIENTE' && f.attempts === 0);
    if (!fila) return false;
    await this.borrar(id);
    return true;
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
let usuario: string | null;

beforeEach(() => {
  ahora = 1_000_000;
  usuario = "cuenta-A";
  ids = 0;
  store = new MemoryStore();
  enviar = jest.fn();
  outbox = new Outbox({ store, enviar, usuarioActual: async () => usuario, ahora: () => ahora, nuevoId: () => `id-${++ids}` });
});

async function encolar(clave: string, descripcion = 'Calificación') {
  ahora += 1;
  return outbox.encolar({
    usuarioId: usuario ?? 'cuenta-A',
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
  it('keeps a conflict for manual review because it does not prove success', async () => {
    await encolar('k-1');
    enviar.mockRejectedValueOnce(new ApiError('Ya calificaste esto.', 409, 'CONFLICT'));

    const resultado = await outbox.procesar();

    expect(resultado).toEqual({ enviadas: 0, pendientes: 0, fallidas: 1 });
    expect(await outbox.listar()).toHaveLength(1);
  });

  it('keeps a single entry when the same action is queued twice', async () => {
    await encolar('k-1');
    await encolar('k-1');

    expect(await outbox.listar()).toHaveLength(1);
  });

  it('runs one pass at a time so an action is never sent twice concurrently', async () => {
    await encolar('k-1');
    let liberar: () => void = () => undefined;
    const espera = new Promise<void>((resolve) => { liberar = resolve; });
    enviar.mockImplementationOnce(() => espera);

    const primera = outbox.procesar();
    const segunda = outbox.procesar();
    await Promise.resolve();
    liberar();

    expect(await primera).toEqual(await segunda);
    expect(enviar).toHaveBeenCalledTimes(1);
  });

  it('notifies subscribers when the queue changes and explicitly discards the current account queue', async () => {
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


it('preserves A actions across logout and hides them from B, including discard', async () => {
  await encolar('a');
  const id = store.filas[0].id;
  usuario = null;
  expect(await outbox.listar()).toEqual([]);
  await outbox.procesar();
  usuario = 'cuenta-B';
  expect(await outbox.listar()).toEqual([]);
  await outbox.descartar(id);
  await outbox.vaciar();
  await outbox.procesar();
  expect(enviar).not.toHaveBeenCalled();
  expect(store.filas).toHaveLength(1);
  usuario = 'cuenta-A';
  await outbox.procesar();
  expect(enviar).toHaveBeenCalledTimes(1);
  expect(store.filas).toHaveLength(0);
});

it('notifies the screen when a rejection changes status without deleting a row', async () => {
  await encolar('a');
  const listener = jest.fn();
  outbox.suscribir(listener);
  enviar.mockRejectedValue(new ApiError('Rechazado', 422, 'VALIDATION_ERROR'));
  await outbox.procesar();
  expect(listener).toHaveBeenCalled();
  expect((await outbox.listar())[0].status).toBe('FALLIDA');
});


it('does not send the remaining snapshot under another account', async () => {
  await encolar('a1');
  await encolar('a2');
  enviar.mockImplementationOnce(async () => { usuario = 'cuenta-B'; });
  await outbox.procesar();
  expect(enviar).toHaveBeenCalledTimes(1);
  expect(store.filas).toHaveLength(1);
  expect(store.filas[0].usuarioId).toBe('cuenta-A');
});

it('keeps the captured owner if the session changes before an offline save', async () => {
  const usuarioId = await outbox.propietario();
  usuario = 'cuenta-B';
  await outbox.encolar({ usuarioId, method: 'POST', path: '/api/v1/reportes', idempotencyKey: 'original', descripcion: 'Reporte de A' });
  expect(await outbox.listar()).toEqual([]);
  await outbox.procesar();
  expect(enviar).not.toHaveBeenCalled();
  usuario = usuarioId;
  expect(await outbox.listar()).toHaveLength(1);
});


it('allows independent actions with the same key on different accounts', async () => {
  await encolar('shared-key');
  usuario = 'cuenta-B';
  await encolar('shared-key');
  expect(store.filas).toHaveLength(2);
  expect(await outbox.listar()).toHaveLength(1);
});

it('retries an in-progress duplicate with the original key and body after backoff', async () => {
  await encolar('in-progress');
  enviar.mockRejectedValueOnce(new ApiError('Procesando', 409, 'IDEMPOTENCY_IN_PROGRESS'))
    .mockResolvedValueOnce(undefined);
  expect(await outbox.procesar()).toEqual({ enviadas: 0, pendientes: 1, fallidas: 0 });
  await outbox.procesar();
  expect(enviar).toHaveBeenCalledTimes(1);
  ahora += RETROCESO_BASE_MS;
  expect(await outbox.procesar()).toEqual({ enviadas: 1, pendientes: 0, fallidas: 0 });
  expect(enviar.mock.calls.map(([a]) => a.idempotencyKey)).toEqual(['in-progress', 'in-progress']);
  expect(enviar.mock.calls[1][0].body).toBe(enviar.mock.calls[0][0].body);
});

it('replaces a rejected registration once with the corrected body and a fresh key', async () => {
  enviar.mockRejectedValueOnce(new ApiError('Nombre duplicado', 409, 'CONFLICT'));
  await encolar('original');
  await outbox.procesar();
  const [rechazada] = await outbox.listar();
  expect(rechazada.status).toBe('FALLIDA');

  const corregida = { body: { nombre: 'Papaya' }, idempotencyKey: 'corregida', descripcion: 'Registrar Papaya' };
  await outbox.corregirFallida(rechazada, corregida);
  await expect(outbox.corregirFallida(rechazada, corregida)).rejects.toThrow(/ya no está disponible/);
  expect(await outbox.listar()).toEqual([expect.objectContaining({
    status: 'PENDIENTE', body: JSON.stringify(corregida.body), idempotencyKey: 'corregida', attempts: 0,
  })]);
});

it('allows discarding a seller decision only before the first send begins', async () => {
  const input = {
    usuarioId: 'cuenta-A', method: 'POST' as const, path: '/api/v1/pedidos/order-id/aceptar',
    idempotencyKey: 'aceptacion-original', descripcion: 'Aceptación de pedido',
  };
  const descartable = await outbox.encolar(input);
  await outbox.descartar(descartable.id);
  expect(await outbox.listar()).toEqual([]);

  const pendiente = await outbox.encolar(input);
  enviar.mockImplementationOnce(async () => {
    await expect(outbox.descartar(pendiente.id)).rejects.toThrow('El envío ya se intentó');
    throw new NetworkError('Respuesta perdida');
  });
  await outbox.procesar();
  expect(await outbox.listar()).toEqual([expect.objectContaining({ attempts: 1, status: 'PENDIENTE' })]);

  enviar.mockRejectedValueOnce(new ApiError('El pedido fue cancelado.', 409, 'INVALID_STATE'));
  await outbox.procesar({ forzar: true });
  expect(enviar.mock.calls.map(([accion]) => accion.idempotencyKey)).toEqual([
    'aceptacion-original', 'aceptacion-original',
  ]);
  expect(await outbox.listar()).toEqual([expect.objectContaining({
    status: 'FALLIDA', lastError: 'El pedido fue cancelado.',
  })]);
});