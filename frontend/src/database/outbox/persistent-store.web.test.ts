import { IDBFactory, IDBObjectStore } from 'fake-indexeddb';
import { PersistentOutboxStore } from './persistent-store.web';
import type { AccionPendiente } from './types';

const { deserialize, serialize } = jest.requireActual<{
  serialize: (value: unknown) => Uint8Array;
  deserialize: (value: Uint8Array) => unknown;
}>('node:v8');


const accion = (cambios: Partial<AccionPendiente> = {}): AccionPendiente => ({
  id: 'accion-1', usuarioId: 'cuenta-A', method: 'POST',
  path: '/api/v1/productos', body: '{"nombre":"Mango"}',
  idempotencyKey: 'clave-1', descripcion: 'Registrar Mango',
  status: 'PENDIENTE', attempts: 0, lastError: null,
  nextAttemptAt: 10, createdAt: 10, ...cambios,
});

beforeEach(() => {
  Object.defineProperty(globalThis, 'indexedDB', {
    configurable: true, value: new IDBFactory(),
  });
  if (typeof structuredClone === 'undefined') {
    Object.defineProperty(globalThis, 'structuredClone', {
      configurable: true, value: (value: unknown) => deserialize(serialize(value)),
    });
  }
});

afterEach(() => jest.restoreAllMocks());

it('persists pending actions across store instances and keeps chronological order', async () => {
  const store = new PersistentOutboxStore();
  await store.insertar(accion({ id: 'b', createdAt: 20 }));
  await store.insertar(accion({ id: 'a', idempotencyKey: 'clave-2' }));
  expect((await new PersistentOutboxStore().listar()).map((a) => a.id)).toEqual(['a', 'b']);
});

it('deduplicates simultaneous inserts with the same account and idempotency key', async () => {
  const a = new PersistentOutboxStore();
  const b = new PersistentOutboxStore();
  await Promise.all([a.insertar(accion()), b.insertar(accion({ id: 'otro-id' }))]);
  expect(await a.listar()).toHaveLength(1);
});

it('keeps equal idempotency keys independent between accounts', async () => {
  const store = new PersistentOutboxStore();
  await store.insertar(accion());
  await store.insertar(accion({ id: 'accion-2', usuarioId: 'cuenta-B' }));
  expect(await store.listar()).toHaveLength(2);
  await store.actualizar(accion({ usuarioId: 'cuenta-B', status: 'FALLIDA' }));
  expect((await store.listar())[0].status).toBe('PENDIENTE');
});

it.each([
  ['/api/v1/pedidos/p/aceptar', '/api/v1/pedidos/p/rechazar', undefined],
  ['/api/v1/productos/p', '/api/v1/productos/p/inventario', '"0"'],
  ['/api/v1/domicilios/d/confirmar-recogida', '/api/v1/domicilios/d/confirmar-recogida', undefined],
  ['/api/v1/domicilios/d/confirmar-salida', '/api/v1/domicilios/d/confirmar-salida', undefined],
  ['/api/v1/pedidos/p/pago/confirmar', '/api/v1/pedidos/p/pago/confirmar', undefined],
  ['/api/v1/pedidos/p/confirmar-entrega', '/api/v1/pedidos/p/confirmar-entrega', undefined],
])('rejects simultaneous incompatible changes: %s / %s', async (path, otraRuta, ifMatch) => {
  const a = new PersistentOutboxStore();
  const b = new PersistentOutboxStore();
  const resultados = await Promise.allSettled([
    a.insertar(accion({ path, method: ifMatch ? 'PATCH' : 'POST', ifMatch })),
    b.insertar(accion({ id: 'accion-2', idempotencyKey: 'clave-2', path: otraRuta, ifMatch })),
  ]);
  expect(resultados.filter((r) => r.status === 'fulfilled')).toHaveLength(1);
  expect(await a.listar()).toHaveLength(1);
});

it('allows only one pending business edit per account and business', async () => {
  const store = new PersistentOutboxStore();
  const edicion = accion({ method: 'PATCH', path: '/api/v1/emprendimientos/me',
    body: JSON.stringify({ base: { emprendimientoId: 'negocio-A' } }) });
  await store.insertar(edicion);
  await expect(store.insertar({ ...edicion, id: 'otra', idempotencyKey: 'otra' }))
    .rejects.toThrow('Ya hay una edición pendiente');
  await store.insertar({ ...edicion, id: 'otro-negocio', idempotencyKey: 'otro-negocio',
    body: JSON.stringify({ base: { emprendimientoId: 'negocio-B' } }) });
  expect(await store.listar()).toHaveLength(2);
});

it('allows discarding only actions without an attempt and from the right account', async () => {
  const store = new PersistentOutboxStore();
  await store.insertar(accion());
  expect(await store.borrarSinIntentos('accion-1', 'cuenta-B')).toBe(false);
  expect(await store.iniciarPrimerIntento('accion-1')).toBe(true);
  expect(await store.iniciarPrimerIntento('accion-1')).toBe(false);
  expect(await store.borrarSinIntentos('accion-1', 'cuenta-A')).toBe(false);
});

it('replaces a failed action only once and clears the previous server error', async () => {
  const store = new PersistentOutboxStore();
  await store.insertar(accion({ status: 'FALLIDA', attempts: 2, errorStatus: 409 }));
  const corregida = accion({ idempotencyKey: 'corregida', body: '{"nombre":"Pera"}' });
  const resultados = await Promise.all([
    store.reemplazarFallida(corregida, 'clave-1'),
    new PersistentOutboxStore().reemplazarFallida(corregida, 'clave-1'),
  ]);
  expect(resultados.sort()).toEqual([false, true]);
  expect((await store.listar())[0]).toEqual(corregida);
});

it('rolls back a correction if its idempotency key belongs to another action', async () => {
  const store = new PersistentOutboxStore();
  await store.insertar(accion({ status: 'FALLIDA' }));
  await store.insertar(accion({ id: 'accion-2', idempotencyKey: 'ocupada' }));
  await expect(store.reemplazarFallida(accion({ idempotencyKey: 'ocupada' }), 'clave-1'))
    .rejects.toThrow('La clave de envío ya pertenece');
  expect((await store.listar())[0].status).toBe('FALLIDA');
});

it('does not report a write as saved when its transaction aborts', async () => {
  const store = new PersistentOutboxStore();
  const add = IDBObjectStore.prototype.add;
  const spy = jest.spyOn(IDBObjectStore.prototype, 'add').mockImplementation(function (
    this: IDBObjectStore, ...args: Parameters<typeof add>
  ) {
    const request = add.apply(this, args);
    this.transaction.abort();
    return request;
  });
  await expect(store.insertar(accion())).rejects.toThrow('No se pudo guardar');
  spy.mockRestore();
  expect(await store.listar()).toEqual([]);
});

it('retries opening after a browser storage permission error with an understandable message', async () => {
  const store = new PersistentOutboxStore();
  jest.spyOn(indexedDB, 'open').mockImplementationOnce(() => {
    throw new DOMException('Blocked by policy', 'SecurityError');
  });
  await expect(store.insertar(accion())).rejects.toThrow('No se pudo abrir el almacenamiento');
  await store.insertar(accion());
  expect(await store.listar()).toHaveLength(1);
});

it('does not silently fall back to volatile memory when storage is unavailable', async () => {
  Object.defineProperty(globalThis, 'indexedDB', { configurable: true, value: undefined });
  await expect(new PersistentOutboxStore().insertar(accion()))
    .rejects.toThrow('Este navegador no permite guardar');
});
