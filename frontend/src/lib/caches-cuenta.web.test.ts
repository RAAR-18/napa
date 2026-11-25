import { IDBFactory } from 'fake-indexeddb';
import type { PerfilUsuario } from './usuarios';
import type { Emprendimiento } from './emprendimientos';
import type { ProductosPage } from './productos';
import type { NotificacionesPagina } from './notificaciones';
import type { PedidosClientePagina } from './pedidos';

let perfil: typeof import('./perfil-cache');
let negocio: typeof import('./emprendimiento-propio-cache');
let catalogo: typeof import('./catalogo-cache');
let inventario: typeof import('./inventario-cache');
let notificaciones: typeof import('./notificaciones-cache');
let pedidos: typeof import('./pedidos-cliente-cache');
let storage: typeof import('./cache-cuenta.web');
function cargar() {
  jest.isolateModules(() => {
    perfil = jest.requireActual('./perfil-cache.web');
    negocio = jest.requireActual('./emprendimiento-propio-cache.web');
    catalogo = jest.requireActual('./catalogo-cache.web');
    inventario = jest.requireActual('./inventario-cache.web');
    notificaciones = jest.requireActual('./notificaciones-cache.web');
    pedidos = jest.requireActual('./pedidos-cliente-cache.web');
    storage = jest.requireActual('./cache-cuenta.web');
  });
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
afterEach(() => jest.restoreAllMocks());
const usuario: PerfilUsuario = {
  id: 'A', nombre: 'Prueba', rol: 'CLIENTE', celular: '3000000000', correo: null,
  fotoUrl: null, estado: 'ACTIVA', celularVerificado: true, createdAt: '2026-11-24T12:00:00Z',
  ubicacion: { direccion: 'Dirección de prueba', latitud: 11.2, longitud: -74.2, referencia: 'Referencia privada' },
};
const emprendimiento: Emprendimiento = {
  id: 'negocio-A', nombre: 'Prueba', categoria: 'OTROS', descripcion: 'Prueba',
  horaApertura: '08:00', horaCierre: '18:00', tipoVendedor: 'AMBULANTE', estado: 'ACTIVO',
  ubicacion: { latitud: 11.2, longitud: -74.2, direccion: null, referencia: null },
};
function pagina(page = 0, id = 'negocio-A'): ProductosPage {
  return { page, size: 20, totalElements: 1, emprendimiento: { id, nombre: 'Prueba' }, content: [{
    id: 'producto-A', emprendimientoId: id, nombre: 'Mango', precio: 1000, unidad: 'UNIDAD', descripcion: null,
    cantidadDisponible: 2, estado: 'DISPONIBLE', soloReserva: false, updatedAt: '2026-11-24T12:00:00Z', version: 0,
  }] };
}
const avisos: NotificacionesPagina = { page: 0, size: 20, totalElements: 1, content: [{
  id: 'aviso-A', tipo: 'pedidos.pedido-creado', titulo: 'Pedido', cuerpo: 'Prueba', leida: false,
  leidaAt: null, createdAt: '2026-11-24T12:00:00Z', referencia: { tipo: 'PEDIDO', id: 'privado' },
}] };
const listaPedidos: PedidosClientePagina = { page: 0, size: 20, totalElements: 1, content: [{
  id: 'pedido-A', emprendimiento: { id: 'negocio-A', nombre: 'Prueba' }, modalidad: 'RECOGIDA',
  createdAt: '2026-11-24T12:00:00Z', estado: 'PENDIENTE', reserva: null, total: 1000,
}] };

it('persists profile across reloads, isolates accounts and omits precise location', async () => {
  await perfil.guardarPerfil(usuario, () => true);
  cargar();
  const copia = await perfil.leerPerfilGuardado('A');
  expect(copia?.perfil.direccion).toBe('Dirección de prueba');
  expect(copia?.perfil).not.toHaveProperty('ubicacion');
  expect(copia?.perfil).not.toHaveProperty('fotoUrl');
  expect(await perfil.leerPerfilGuardado('B')).toBeNull();
  await perfil.borrarPerfilGuardado('B');
  expect(await perfil.leerPerfilGuardado('A')).not.toBeNull();
});
it('expires all six caches at the seven day boundary', async () => {
  const ahora = Date.now();
  const reloj = jest.spyOn(Date, 'now').mockReturnValue(ahora);
  await perfil.guardarPerfil(usuario, () => true);
  await negocio.guardarEmprendimientoPropio('A', emprendimiento, () => true);
  await catalogo.guardarPaginaCatalogo('A', 'negocio-A', 'nombre,asc', pagina(), () => true);
  await inventario.guardarPaginaInventario('A', 'nombre,asc', pagina(), () => true);
  await notificaciones.guardarPaginaNotificaciones('A', false, avisos, () => true);
  await pedidos.guardarConsultaPedidos('A', 'listado', 'pagina:0', listaPedidos, () => true);
  reloj.mockReturnValue(ahora + 7 * 24 * 60 * 60 * 1000);
  expect(await perfil.leerPerfilGuardado('A')).toBeNull();
  expect(await negocio.leerEmprendimientoPropio('A')).toBeNull();
  expect(await catalogo.leerPaginaCatalogo('A', 'negocio-A', 'nombre,asc', 0)).toBeNull();
  expect(await inventario.leerPaginaInventario('A', 'nombre,asc', 0)).toBeNull();
  expect(await notificaciones.leerPaginaNotificaciones('A', false, 0)).toBeNull();
  expect(await pedidos.leerConsultaPedidos('A', 'listado', 'pagina:0')).toBeNull();
});
it('rejects a session invalidated while opening the transaction', async () => {
  let vigente = true;
  const escritura = negocio.guardarEmprendimientoPropio('A', emprendimiento, () => vigente);
  vigente = false;
  await escritura;
  expect(await negocio.leerEmprendimientoPropio('A')).toBeNull();
  await negocio.guardarEmprendimientoPropio('A', emprendimiento, () => true);
  await negocio.borrarEmprendimientoPropio('B');
  expect((await negocio.leerEmprendimientoPropio('A'))?.valor.id).toBe('negocio-A');
});
it('keeps concurrent catalogue writes, caps fifty pages and deletes one business only', async () => {
  await Promise.all(Array.from({ length: 52 }, (_, page) =>
    catalogo.guardarPaginaCatalogo('A', 'negocio-A', 'nombre,asc', pagina(page), () => true)));
  expect(await catalogo.listarPaginasGuardadasCatalogo('A', 'negocio-A', 'nombre,asc')).toEqual(Array.from({ length: 50 }, (_, i) => i + 2));
  expect(await catalogo.leerPaginaCatalogo('B', 'negocio-A', 'nombre,asc', 51)).toBeNull();
  await catalogo.guardarPaginaCatalogo('A', 'negocio-B', 'precio,asc', pagina(0, 'negocio-B'), () => true);
  await catalogo.borrarCatalogoGuardado('A', 'negocio-A');
  expect(await catalogo.listarPaginasGuardadasCatalogo('A', 'negocio-A', 'nombre,asc')).toEqual([]);
  expect((await catalogo.leerPaginaCatalogo('A', 'negocio-B', 'precio,asc', 0))?.data.emprendimiento?.id).toBe('negocio-B');
});
it('replaces inventory from another business and clears missing businesses', async () => {
  await inventario.guardarPaginaInventario('A', 'nombre,asc', pagina(), () => true);
  expect((await inventario.leerProductoInventario('A', 'producto-A'))?.nombre).toBe('Mango');
  expect(await inventario.leerProductoInventario('B', 'producto-A')).toBeNull();
  await inventario.guardarPaginaInventario('A', 'precio,asc', pagina(0, 'negocio-B'), () => true);
  expect(await inventario.leerPaginaInventario('A', 'nombre,asc', 0)).toBeNull();
  await inventario.guardarPaginaInventario('A', 'precio,asc', { ...pagina(), emprendimiento: null }, () => true);
  expect(await inventario.leerProductoInventario('A', 'producto-A')).toBeNull();
});
it('keeps notification filters separate and omits navigation references', async () => {
  await notificaciones.guardarPaginaNotificaciones('A', true, avisos, () => true);
  expect((await notificaciones.leerPaginaNotificaciones('A', true, 0))?.data.content[0]).not.toHaveProperty('referencia');
  expect(await notificaciones.leerPaginaNotificaciones('A', false, 0)).toBeNull();
  expect(await notificaciones.leerPaginaNotificaciones('B', true, 0)).toBeNull();
  expect(await notificaciones.listarPaginasGuardadas('A', true)).toEqual([0]);
  await notificaciones.borrarNotificacionesGuardadas('A');
  expect(await notificaciones.listarPaginasGuardadas('A', true)).toEqual([]);
});
it('removes a cancelled order from all client lists without touching another account or vendor list', async () => {
  await pedidos.guardarConsultaPedidos('A', 'listado', 'pagina:0', listaPedidos, () => true);
  await pedidos.guardarConsultaPedidos('A', 'listado', 'otro:0', listaPedidos, () => true);
  await pedidos.guardarConsultaPedidos('B', 'listado', 'pagina:0', listaPedidos, () => true);
  await pedidos.quitarPedidoGuardado('A', 'pedido-A');
  expect((await pedidos.leerConsultaPedidos<PedidosClientePagina>('A', 'listado', 'pagina:0'))?.data.content).toEqual([]);
  expect((await pedidos.leerConsultaPedidos<PedidosClientePagina>('A', 'listado', 'otro:0'))?.data.content).toEqual([]);
  expect((await pedidos.leerConsultaPedidos<PedidosClientePagina>('B', 'listado', 'pagina:0'))?.data.content).toHaveLength(1);
  expect(await pedidos.listarPaginasGuardadas('A', 'pagina:')).toEqual([0]);
});
it('limits order entries per type rather than discarding other types', async () => {
  await pedidos.guardarConsultaPedidos('A', 'listado', 'pagina:0', listaPedidos, () => true);
  await Promise.all(Array.from({ length: 51 }, (_, i) =>
    pedidos.guardarConsultaPedidos('A', 'listado-vendedor', String(i), { ...listaPedidos, content: [] }, () => true)));
  expect(await pedidos.leerConsultaPedidos('A', 'listado-vendedor', '0')).toBeNull();
  expect(await pedidos.leerConsultaPedidos('A', 'listado-vendedor', '50')).not.toBeNull();
  expect(await pedidos.leerConsultaPedidos('A', 'listado', 'pagina:0')).not.toBeNull();
});
it('rolls back a failed multi-row update and permits later writes', async () => {
  await storage.actualizar('A', 'prueba', (filas) => storage.agregar(filas, 'original', { valor: 1 }));
  const { IDBObjectStore } = jest.requireActual<typeof import('fake-indexeddb')>('fake-indexeddb');
  const put = jest.spyOn(IDBObjectStore.prototype, 'put').mockImplementationOnce(() => { throw new Error('Fallo controlado'); });
  await expect(storage.actualizar('A', 'prueba', (filas) => storage.agregar(filas, 'nuevo', { valor: 2 }))).rejects.toThrow('Fallo controlado');
  put.mockRestore();
  expect((await storage.consultar('A', 'prueba')).map((fila) => fila.clave)).toEqual(['original']);
  await storage.actualizar('A', 'prueba', () => []);
  expect(await storage.consultar('A', 'prueba')).toEqual([]);
});
it('reports unavailable browser storage and retries after recovery', async () => {
  const factory = globalThis.indexedDB;
  Object.defineProperty(globalThis, 'indexedDB', { configurable: true, value: undefined });
  await expect(perfil.leerPerfilGuardado('A')).rejects.toThrow('El navegador no permite');
  Object.defineProperty(globalThis, 'indexedDB', { configurable: true, value: factory });
  await perfil.guardarPerfil(usuario, () => true);
  expect(await perfil.leerPerfilGuardado('A')).not.toBeNull();
});

it('caps inventory and notifications at fifty pages across filters', async () => {
  await Promise.all(Array.from({ length: 51 }, (_, i) =>
    inventario.guardarPaginaInventario('A', 'nombre,asc', pagina(i), () => true)));
  expect(await inventario.leerPaginaInventario('A', 'nombre,asc', 0)).toBeNull();
  expect(await inventario.leerPaginaInventario('A', 'nombre,asc', 50)).not.toBeNull();
  await Promise.all(Array.from({ length: 51 }, (_, i) =>
    notificaciones.guardarPaginaNotificaciones('A', false, { ...avisos, page: i }, () => true)));
  expect(await notificaciones.listarPaginasGuardadas('A', false)).toEqual(Array.from({ length: 50 }, (_, i) => i + 1));
});
it('deletes order details selectively and clears one account only', async () => {
  await storage.actualizar('A', 'pedidos', (filas) => storage.agregar(
    storage.agregar(filas, JSON.stringify(['detalle', 'pedido-A']), { id: 'pedido-A' }),
    JSON.stringify(['detalle-vendedor', 'pedido-A']), { id: 'pedido-A' }));
  await pedidos.quitarDetalleVendedorGuardado('A', 'pedido-A');
  expect(await pedidos.leerConsultaPedidos('A', 'detalle-vendedor', 'pedido-A')).toBeNull();
  expect(await pedidos.leerConsultaPedidos('A', 'detalle', 'pedido-A')).not.toBeNull();
  await pedidos.quitarPedidoGuardado('A', 'pedido-A');
  expect(await pedidos.leerConsultaPedidos('A', 'detalle', 'pedido-A')).toBeNull();
  await pedidos.guardarConsultaPedidos('A', 'listado', 'pagina:0', listaPedidos, () => true);
  await pedidos.guardarConsultaPedidos('B', 'listado', 'pagina:0', listaPedidos, () => true);
  await pedidos.borrarPedidosGuardados('A');
  expect(await pedidos.listarPaginasGuardadas('A', 'pagina:')).toEqual([]);
  expect(await pedidos.listarPaginasGuardadas('B', 'pagina:')).toEqual([0]);
});
