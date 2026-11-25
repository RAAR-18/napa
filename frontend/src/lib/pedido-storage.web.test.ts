import { IDBFactory } from 'fake-indexeddb';

let datos: typeof import('./pedido-storage.web');

beforeEach(() => {
  Object.defineProperty(globalThis, 'indexedDB', { configurable: true, value: new IDBFactory() });
  if (typeof structuredClone === 'undefined') {
    const v8 = jest.requireActual<{ serialize: (v: unknown) => Uint8Array; deserialize: (v: Uint8Array) => unknown }>('node:v8');
    Object.defineProperty(globalThis, 'structuredClone', { configurable: true,
      value: (v: unknown) => v8.deserialize(v8.serialize(v)) });
  }
  jest.isolateModules(() => { datos = jest.requireActual('./pedido-storage.web'); });
});

it('persists drafts and keeps each account and business separate', async () => {
  await datos.guardar('A', 'borrador:negocio-1', { items: ['Mango'] });
  await datos.guardar('B', 'borrador:negocio-1', { items: ['Pera'] });
  await datos.guardar('A', 'borrador:negocio-2', { items: ['Limon'] });
  expect(await datos.leer('A', 'borrador:negocio-1')).toEqual({ items: ['Mango'] });
  await datos.borrar('A', 'borrador:negocio-1');
  expect(await datos.leer('A', 'borrador:negocio-1')).toBeNull();
  expect(await datos.leer('B', 'borrador:negocio-1')).toEqual({ items: ['Pera'] });
});

it('removes expired quotes without deleting the last address or drafts', async () => {
  await datos.guardar('A', 'cotizacion:n:RECOGIDA', { total: 100 });
  await datos.guardar('A', 'ultima-direccion', { direccion: 'Centro' });
  await datos.guardar('A', 'borrador:n', { items: [] });
  await datos.guardar('B', 'cotizacion:n:RECOGIDA', { total: 200 });
  expect(await datos.leer('A', 'cotizacion:n:RECOGIDA', Date.now() + 1000)).toBeNull();
  await datos.borrarCotizaciones('A');
  expect(await datos.leer('A', 'cotizacion:n:RECOGIDA')).toBeNull();
  expect(await datos.leer('A', 'ultima-direccion')).toEqual({ direccion: 'Centro' });
  expect(await datos.leer('A', 'borrador:n')).toEqual({ items: [] });
  expect(await datos.leer('B', 'cotizacion:n:RECOGIDA')).toEqual({ total: 200 });
});
