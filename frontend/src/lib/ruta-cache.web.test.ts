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
  await guardarRuta(ruta, 'cuenta-A');
  expect(await leerRuta(ruta.domicilioId, 'cuenta-A')).toEqual({ ruta, updatedAt: expect.any(String) });
  await borrarRuta(ruta.domicilioId, 'cuenta-A');
  expect(await leerRuta(ruta.domicilioId, 'cuenta-A')).toBeNull();
});
it('clears cached customer addresses while keeping the business listing', async () => {
  await guardarRuta(ruta, 'cuenta-A');
  await guardarLectura('emprendimientos-listado:prueba', 'listado');
  await borrarTodasLasRutas();
  expect(await leerRuta(ruta.domicilioId, 'cuenta-A')).toBeNull();
  expect((await leerLectura('emprendimientos-listado:prueba'))?.contenido).toBe('listado');
});
it('does not expose malformed cached route data', async () => {
  await guardarLectura('ruta-domicilio:' + JSON.stringify(['cuenta-A', 'invalida']), '{invalid');
  expect(await leerRuta('invalida', 'cuenta-A')).toBeNull();
});

it('isolates routes between accounts and deletes only the specified owner', async () => {
  await guardarRuta(ruta, 'cuenta-A');
  expect(await leerRuta(ruta.domicilioId, 'cuenta-B')).toBeNull();
  await guardarRuta({ ...ruta, destino: { ...ruta.destino, direccion: 'Otra copia de prueba' } }, 'cuenta-B');
  await borrarRuta(ruta.domicilioId, 'cuenta-A');
  expect(await leerRuta(ruta.domicilioId, 'cuenta-A')).toBeNull();
  expect((await leerRuta(ruta.domicilioId, 'cuenta-B'))?.ruta.destino.direccion).toBe('Otra copia de prueba');
});
it('does not adopt historical routes without an owner or read without an account', async () => {
  await guardarLectura('ruta-domicilio:' + ruta.domicilioId, JSON.stringify(ruta));
  expect(await leerRuta(ruta.domicilioId, 'cuenta-C')).toBeNull();
  expect(await leerRuta(ruta.domicilioId, '')).toBeNull();
  await guardarRuta(ruta, '');
  expect(await leerRuta(ruta.domicilioId, '')).toBeNull();
});
it('rejects a route stored under a different delivery id', async () => {
  await guardarLectura('ruta-domicilio:' + JSON.stringify(['cuenta-A', 'otro']), JSON.stringify(ruta));
  expect(await leerRuta('otro', 'cuenta-A')).toBeNull();
});
