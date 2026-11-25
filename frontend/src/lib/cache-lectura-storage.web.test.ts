import { IDBFactory } from 'fake-indexeddb';
let datos: typeof import('./cache-lectura-storage.web');
function cargar() {
  jest.isolateModules(() => { datos = jest.requireActual('./cache-lectura-storage.web'); });
}
beforeEach(() => {
  Object.defineProperty(globalThis, 'indexedDB', { configurable: true, value: new IDBFactory() });
  if (typeof structuredClone === 'undefined') {
    const v8 = jest.requireActual<{ serialize: (v: unknown) => Uint8Array; deserialize: (v: Uint8Array) => unknown }>('node:v8');
    Object.defineProperty(globalThis, 'structuredClone', { configurable: true,
      value: (v: unknown) => v8.deserialize(v8.serialize(v)) });
  }
  cargar();
});
it('persists across module reloads and replaces a reading', async () => {
  await datos.guardarLectura('listado', '{"version":1}');
  cargar();
  expect(await datos.leerLectura('listado')).toEqual({ contenido: '{"version":1}', actualizado_at: expect.any(String) });
  await datos.guardarLectura('listado', '{"version":2}');
  expect((await datos.leerLectura('listado'))?.contenido).toBe('{"version":2}');
});
it('deletes routes selectively and preserves listings', async () => {
  await datos.guardarLectura('ruta-domicilio:A', 'ruta A');
  await datos.guardarLectura('ruta-domicilio:B', 'ruta B');
  await datos.guardarLectura('emprendimientos-listado', 'listado');
  await datos.borrarLectura('ruta-domicilio:A');
  expect(await datos.leerLectura('ruta-domicilio:A')).toBeNull();
  expect((await datos.leerLectura('ruta-domicilio:B'))?.contenido).toBe('ruta B');
  await datos.borrarRutasGuardadas();
  expect(await datos.leerLectura('ruta-domicilio:B')).toBeNull();
  expect((await datos.leerLectura('emprendimientos-listado'))?.contenido).toBe('listado');
});
it('reports unavailable storage and permits retry', async () => {
  const factory = globalThis.indexedDB;
  Object.defineProperty(globalThis, 'indexedDB', { configurable: true, value: undefined });
  await expect(datos.leerLectura('listado')).rejects.toThrow('El navegador no permite');
  Object.defineProperty(globalThis, 'indexedDB', { configurable: true, value: factory });
  await datos.guardarLectura('listado', 'recuperado');
  expect((await datos.leerLectura('listado'))?.contenido).toBe('recuperado');
});
it('rejects aborted writes without storing partial data', async () => {
  const { IDBObjectStore } = jest.requireActual<typeof import('fake-indexeddb')>('fake-indexeddb');
  const put = jest.spyOn(IDBObjectStore.prototype, 'put').mockImplementationOnce(() => { throw new Error('Almacenamiento lleno'); });
  await expect(datos.guardarLectura('listado', 'fallido')).rejects.toThrow('Almacenamiento lleno');
  put.mockRestore();
  expect(await datos.leerLectura('listado')).toBeNull();
});
