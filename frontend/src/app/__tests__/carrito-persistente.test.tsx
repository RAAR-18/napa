import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { Platform } from 'react-native';

import { PedidoCatalogo } from '../../components/PedidoCatalogo';
import { readSession } from '../../lib/auth';
import { borrarBorradorPedido, guardarBorradorPedido, leerBorradorPedido, leerUltimaDireccion } from '../../lib/pedido-offline';
import { cotizarPedido, crearPedido } from '../../lib/pedidos';
import type { Producto } from '../../lib/productos';

jest.mock('expo-router', () => ({ router: { push: jest.fn() } }));
jest.mock('../../lib/auth', () => ({ readSession: jest.fn(), getSessionGeneration: () => 0 }));
jest.mock('../../lib/uuid', () => ({ randomUUID: () => 'test-key' }));
jest.mock('../../hooks/useResumenesCalificacion', () => ({ useResumenesCalificacion: () => ({}) }));
jest.mock('../../database/outbox', () => ({ outbox: {} }));
jest.mock('../../lib/pedidos', () => ({ cotizarPedido: jest.fn(), crearPedido: jest.fn() }));

const producto: Producto = {
  id: 'mango', emprendimientoId: 'tienda-a', nombre: 'Mango', precio: 3500,
  unidad: 'KG', descripcion: null, cantidadDisponible: 10, estado: 'DISPONIBLE',
  soloReserva: false, updatedAt: '2026-11-25T10:00:00Z', version: 0,
};

async function abrirTienda(id = 'tienda-a') {
  return render(
    <PedidoCatalogo emprendimientoId={id} tipoVendedor="PUNTO_FIJO"
      productos={[{ ...producto, emprendimientoId: id }]} enabled
      pageData={null} pageBusy={false} onPage={jest.fn()} />,
  );
}

let restorePlatform: () => void;
let previousStorage: PropertyDescriptor | undefined;
beforeEach(() => {
  restorePlatform = jest.replaceProperty(Platform, 'OS', 'web').restore;
  previousStorage = Object.getOwnPropertyDescriptor(window, 'localStorage');
  const valores = new Map<string, string>();
  Object.defineProperty(window, 'localStorage', { configurable: true, value: {
    getItem: (key: string) => valores.get(key) ?? null,
    setItem: (key: string, value: string) => { valores.set(key, value); },
    removeItem: (key: string) => { valores.delete(key); },
  } });
  jest.mocked(readSession).mockResolvedValue({ usuarioId: 'cliente-a', rol: 'CLIENTE' } as Awaited<ReturnType<typeof readSession>>);
});
afterEach(() => {
  restorePlatform();
  if (previousStorage) Object.defineProperty(window, 'localStorage', previousStorage);
  else Reflect.deleteProperty(window, 'localStorage');
});

it('conserva cantidades al salir, visitar otra tienda y volver sin mezclar carritos', async () => {
  let vista = await abrirTienda();
  await fireEvent.changeText(vista.getByLabelText('Cantidad de Mango'), '2');
  await fireEvent.press(vista.getByRole('button', { name: 'Agregar Mango al pedido' }));
  await waitFor(async () => expect((await leerBorradorPedido('cliente-a', 'tienda-a'))?.items[0].cantidad).toBe(2));
  await vista.unmount();

  vista = await abrirTienda('tienda-b');
  expect(vista.queryByRole('button', { name: 'Quitar Mango' })).toBeNull();
  await fireEvent.press(vista.getByRole('button', { name: 'Agregar Mango al pedido' }));
  await waitFor(async () => expect((await leerBorradorPedido('cliente-a', 'tienda-b'))?.items[0].cantidad).toBe(1));
  await vista.unmount();

  vista = await abrirTienda();
  await waitFor(() => expect(vista.getByText('2 · Mango')).toBeTruthy());
  expect((await leerBorradorPedido('cliente-a', 'tienda-b'))?.items[0].cantidad).toBe(1);
});

it('no muestra el carrito de otra cuenta y conserva la eliminación del último producto', async () => {
  let vista = await abrirTienda();
  await fireEvent.press(vista.getByRole('button', { name: 'Agregar Mango al pedido' }));
  await waitFor(async () => expect((await leerBorradorPedido('cliente-a', 'tienda-a'))?.items).toHaveLength(1));
  await vista.unmount();

  jest.mocked(readSession).mockResolvedValue({ usuarioId: 'cliente-b', rol: 'CLIENTE' } as Awaited<ReturnType<typeof readSession>>);
  vista = await abrirTienda();
  expect(vista.queryByRole('button', { name: 'Quitar Mango' })).toBeNull();
  await vista.unmount();

  jest.mocked(readSession).mockResolvedValue({ usuarioId: 'cliente-a', rol: 'CLIENTE' } as Awaited<ReturnType<typeof readSession>>);
  vista = await abrirTienda();
  await fireEvent.press(await vista.findByRole('button', { name: 'Quitar Mango' }));
  await waitFor(async () => expect(await leerBorradorPedido('cliente-a', 'tienda-a')).toBeNull());
  await vista.unmount();
  vista = await abrirTienda();
  expect(vista.queryByRole('button', { name: 'Quitar Mango' })).toBeNull();
});

it('ordena los guardados y el borrado aunque se soliciten sin esperar entre ellos', async () => {
  const guardado = guardarBorradorPedido('cliente-a', 'tienda-a', {
    items: [{ producto, cantidad: 2 }], modalidad: 'RECOGIDA', reserva: false, fechaReserva: '',
  });
  const borrado = borrarBorradorPedido('cliente-a', 'tienda-a');
  expect(await leerBorradorPedido('cliente-a', 'tienda-a')).toBeNull();
  await Promise.all([guardado, borrado]);
});

it('vacía el carrito al confirmar y no lo recupera al volver a la tienda', async () => {
  const cotizacion = {
    items: [], subtotal: 3500, tarifaDomicilio: null, total: 3500,
    modalidadesDisponibles: ['RECOGIDA'] as const,
    mediosPagoDisponibles: ['CUENTA_BANCARIA'] as const, pagoDomicilio: null, advertencias: [],
  };
  jest.mocked(cotizarPedido).mockResolvedValue({ ...cotizacion,
    modalidadesDisponibles: ['RECOGIDA'], mediosPagoDisponibles: ['CUENTA_BANCARIA'],
  });
  jest.mocked(crearPedido).mockResolvedValue({
    id: 'pedido', estado: 'PENDIENTE', modalidad: 'RECOGIDA', reserva: null,
    medioPago: 'CUENTA_BANCARIA', items: [], subtotal: 3500, tarifaDomicilio: null,
    total: 3500, ubicacionEntrega: null, pagoDomicilio: null, createdAt: '2026-11-25T10:00:00Z',
  });
  const vista = await abrirTienda();
  await fireEvent.press(vista.getByRole('button', { name: 'Agregar Mango al pedido' }));
  await waitFor(async () => expect((await leerBorradorPedido('cliente-a', 'tienda-a'))?.items).toHaveLength(1));
  await fireEvent.press(vista.getByRole('button', { name: 'Continuar con la entrega' }));
  await fireEvent.press(vista.getByRole('button', { name: 'Cotizar pedido' }));
  await fireEvent.press(await vista.findByRole('button', { name: 'Confirmar pedido' }));
  await vista.findByText('Pedido enviado');
  await waitFor(async () => expect(await leerBorradorPedido('cliente-a', 'tienda-a')).toBeNull());
  await vista.unmount();
  const nuevaVista = await abrirTienda();
  expect(nuevaVista.queryByRole('button', { name: 'Quitar Mango' })).toBeNull();
});

it('recuerda una dirección cotizada en otra tienda y la separa por cuenta', async () => {
  jest.mocked(cotizarPedido).mockResolvedValue({
    items: [], subtotal: 3500, tarifaDomicilio: 3000, total: 6500,
    modalidadesDisponibles: ['DOMICILIO'], mediosPagoDisponibles: ['CUENTA_BANCARIA'],
    pagoDomicilio: null, advertencias: [],
  });
  let vista = await abrirTienda();
  await fireEvent.press(vista.getByRole('button', { name: 'Agregar Mango al pedido' }));
  await fireEvent.press(vista.getByRole('button', { name: 'Continuar con la entrega' }));
  await fireEvent.press(vista.getByRole('radio', { name: 'Domicilio' }));
  await fireEvent.changeText(vista.getByLabelText('Dirección de entrega'), 'Calle 20 # 5-10');
  await fireEvent.changeText(vista.getByLabelText('Barrio'), 'Centro');
  await fireEvent.changeText(vista.getByLabelText('Referencia (opcional)'), 'Portón azul');
  await fireEvent.changeText(vista.getByLabelText('Latitud'), '11.24');
  await fireEvent.changeText(vista.getByLabelText('Longitud'), '-74.20');
  await fireEvent.press(vista.getByRole('button', { name: 'Cotizar pedido' }));
  await waitFor(async () => expect((await leerUltimaDireccion('cliente-a'))?.direccion).toBe('Calle 20 # 5-10'));
  await vista.unmount();
  vista = await abrirTienda('tienda-b');
  await fireEvent.press(vista.getByRole('button', { name: 'Agregar Mango al pedido' }));
  await fireEvent.press(vista.getByRole('button', { name: 'Continuar con la entrega' }));
  await fireEvent.press(vista.getByRole('radio', { name: 'Domicilio' }));
  await waitFor(() => expect(vista.getByLabelText('Dirección de entrega').props.value).toBe('Calle 20 # 5-10'));
  expect(vista.getByLabelText('Referencia (opcional)').props.value).toBe('Portón azul');
  expect(vista.getByLabelText('Latitud').props.value).toBe('11.24');
  expect(await leerUltimaDireccion('cliente-b')).toBeNull();
});
