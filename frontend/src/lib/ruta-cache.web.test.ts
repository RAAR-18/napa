import { IDBFactory } from 'fake-indexeddb';
import type { RutaDomicilio } from './domicilios';

import { guardarRuta, leerRuta, borrarRuta, borrarTodasLasRutas } from './ruta-cache';
import { guardarLectura, leerLectura } from './cache-lectura-storage';

jest.mock('./cache-lectura-storage', () => jest.requireActual('./cache-lectura-storage.web'));

beforeAll(() => {
  Object.defineProperty(globalThis, 'indexedDB', { configurable: true, value: new IDBFactory() });
  if (typeof structuredClone === 'undefined') {
    const v8 = jest.requireActual<{ serialize: (v: unknown) => Uint8Array; deserialize: (v: Uint8Array) => unknown }>('node:v8');
    Object.defineProperty(globalThis, 'structuredClone', { configurable: true,
      value: (v: unknown) => v8.deserialize(v8.serialize(v)) });
  }
});
const ruta: RutaDomicilio = {
  domicilioId: 'domicilio-A', estado: 'ASIGNADO',
  origen: { latitud: 11.24, longitud: -74.20, direccion: 'Origen de prueba', referencia: null, aproximado: false },
  destino: { latitud: 11.25, longitud: -74.21, direccion: 'Destino de prueba', referencia: null, aproximado: false },
  tramos: [],
};
it('recovers a saved route and removes it when the delivery ends', async () => {
  await guardarRuta(ruta);
  expect(await leerRuta(ruta.domicilioId)).toEqual({ ruta, updatedAt: expect.any(String) });
  await borrarRuta(ruta.domicilioId);
  expect(await leerRuta(ruta.domicilioId)).toBeNull();
});
it('clears cached customer addresses while keeping the business listing', async () => {
  await guardarRuta(ruta);
  await guardarLectura('emprendimientos-listado:prueba', 'listado');
  await borrarTodasLasRutas();
  expect(await leerRuta(ruta.domicilioId)).toBeNull();
  expect((await leerLectura('emprendimientos-listado:prueba'))?.contenido).toBe('listado');
});
it('does not expose malformed cached route data', async () => {
  await guardarLectura('ruta-domicilio:invalida', '{invalid');
  expect(await leerRuta('invalida')).toBeNull();
});
