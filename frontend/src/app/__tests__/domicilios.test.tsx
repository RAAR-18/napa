import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { router } from 'expo-router';
import * as Crypto from 'expo-crypto';

import DomiciliosVendedorScreen from '../domicilios-vendedor';
import PedidoVendedorScreen from '../pedido-vendedor';
import { ApiError } from '../../lib/api';
import { getValidSession } from '../../lib/auth';
import { listarDomiciliosVendedor, publicarDomicilio } from '../../lib/domicilios';
import { obtenerPedidoVendedor, type PedidoVendedorDetalle } from '../../lib/pedidos';

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: () => ({ pedidoId: 'order-id' }),
}));
jest.mock('expo-crypto', () => ({ randomUUID: jest.fn(() => 'publish-key') }));
jest.mock('../../lib/auth', () => ({
  clearSession: jest.fn(),
  getValidSession: jest.fn(),
}));
jest.mock('../../lib/pedidos', () => ({
  MARGEN_RESERVA_MS: jest.requireActual('../../lib/pedidos').MARGEN_RESERVA_MS,
  aceptarPedido: jest.fn(),
  confirmarPagoPedido: jest.fn(),
  obtenerPedidoVendedor: jest.fn(),
  rechazarPedido: jest.fn(),
}));
jest.mock('../../lib/domicilios', () => ({
  ...jest.requireActual('../../lib/domicilios'),
  listarDomiciliosVendedor: jest.fn(),
  publicarDomicilio: jest.fn(),
}));

const fixedSeller = {
  accessToken: 'access',
  refreshToken: 'refresh',
  rol: 'VENDEDOR_PUNTO_FIJO',
  expiresIn: 900,
  expiresAt: Date.now() + 900000,
  tokenType: 'Bearer',
};
const paidOrder: PedidoVendedorDetalle = {
  id: 'order-id',
  estado: 'ACEPTADO',
  modalidad: 'DOMICILIO',
  reserva: null,
  createdAt: '2026-11-13T12:00:00Z',
  updatedAt: '2026-11-13T12:10:00Z',
  cliente: { nombre: 'Ana Pérez' },
  items: [{
    productoId: 'product-id',
    nombre: 'Mango',
    unidad: 'KG',
    cantidad: 1,
    precioUnitario: 3500,
    subtotal: 3500,
    productoEliminado: false,
  }],
  subtotal: 3500,
  tarifaDomicilio: 4500,
  total: 8000,
  medioPago: 'CUENTA_BANCARIA',
  pago: { estado: 'CONFIRMADO' },
  ubicacionEntrega: { latitud: 11.25, longitud: -74.2, direccion: 'Calle 15 # 3-20', referencia: null },
  domicilio: null,
};
const deliveryPage = {
  content: [{
    id: 'delivery-id',
    pedidoId: 'order-id',
    estado: 'CANCELADO' as const,
    tarifa: 4500,
    domiciliario: null,
    motivoCancelacion: 'SIN_ASIGNAR' as const,
    createdAt: '2026-11-13T12:00:00Z',
    updatedAt: '2026-11-13T14:00:00Z',
  }],
  page: 0,
  size: 20,
  totalElements: 1,
};

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(getValidSession).mockResolvedValue(fixedSeller);
  jest.mocked(obtenerPedidoVendedor).mockResolvedValue(paidOrder);
  jest.mocked(listarDomiciliosVendedor).mockResolvedValue(deliveryPage);
});

it('publishes the delivery of a paid order after explicit confirmation', async () => {
  jest.mocked(publicarDomicilio).mockResolvedValue({
    id: 'delivery-id',
    pedidoId: 'order-id',
    estado: 'DISPONIBLE',
    ganancia: 4500,
    createdAt: '2026-11-13T12:20:00Z',
    origen: { latitud: 11.24, longitud: -74.21, direccion: 'Carrera 5', referencia: null },
    destino: { latitud: 11.25, longitud: -74.2, direccion: 'Calle 15 # 3-20', referencia: null },
  });
  jest.mocked(obtenerPedidoVendedor)
    .mockResolvedValueOnce(paidOrder)
    .mockResolvedValueOnce({
      ...paidOrder,
      domicilio: { id: 'delivery-id', estado: 'DISPONIBLE', motivoCancelacion: null },
    });
  const view = await render(<PedidoVendedorScreen />);

  await waitFor(() => expect(view.getByRole('button', { name: 'Publicar domicilio' })).toBeTruthy());
  await fireEvent.press(view.getByRole('button', { name: 'Publicar domicilio' }));
  expect(publicarDomicilio).not.toHaveBeenCalled();
  await waitFor(() => expect(view.getByText(/tarifa de \$4\.500 COP/)).toBeTruthy());
  await fireEvent.press(view.getByRole('button', { name: 'Confirmar publicación' }));

  await waitFor(() => expect(publicarDomicilio).toHaveBeenCalledWith('order-id', 'publish-key'));
  expect(Crypto.randomUUID).toHaveBeenCalledTimes(1);
  await waitFor(() => expect(view.getByText('Disponible')).toBeTruthy());
  expect(view.queryByRole('button', { name: 'Publicar domicilio' })).toBeNull();
});

it('does not offer publishing until the payment is confirmed', async () => {
  jest.mocked(obtenerPedidoVendedor).mockResolvedValue({
    ...paidOrder,
    pago: { estado: 'PENDIENTE_CONFIRMACION' },
  });
  const view = await render(<PedidoVendedorScreen />);

  await waitFor(() => expect(view.getByRole('button', { name: 'Confirmar pago recibido' })).toBeTruthy());
  expect(view.queryByRole('button', { name: 'Publicar domicilio' })).toBeNull();
});

it('explains when a reservation cannot be published yet', async () => {
  jest.mocked(obtenerPedidoVendedor).mockResolvedValue({ ...paidOrder, reserva: '2026-11-14T18:00:00Z' });
  jest.mocked(publicarDomicilio).mockRejectedValue(
    new ApiError('Aún no', 409, 'RESERVATION_NOT_DUE'),
  );
  const view = await render(<PedidoVendedorScreen />);

  await waitFor(() => expect(view.getByRole('button', { name: 'Publicar domicilio' })).toBeTruthy());
  await fireEvent.press(view.getByRole('button', { name: 'Publicar domicilio' }));
  await waitFor(() => expect(view.getByRole('button', { name: 'Confirmar publicación' })).toBeTruthy());
  await fireEvent.press(view.getByRole('button', { name: 'Confirmar publicación' }));

  await waitFor(() => expect(view.getByText(/Podrás publicar el domicilio desde/)).toBeTruthy());
});

it('shows a cancelled delivery with its reason and no publish action on a cancelled order', async () => {
  jest.mocked(obtenerPedidoVendedor).mockResolvedValue({
    ...paidOrder,
    estado: 'CANCELADO',
    domicilio: { id: 'delivery-id', estado: 'CANCELADO', motivoCancelacion: 'SIN_ASIGNAR' },
  });
  const view = await render(<PedidoVendedorScreen />);

  await waitFor(() => expect(view.getByText('Ningún domiciliario lo tomó a tiempo')).toBeTruthy());
  expect(view.queryByRole('button', { name: 'Publicar domicilio' })).toBeNull();
});

it('lists the seller deliveries, filters by state and opens the order', async () => {
  const view = await render(<DomiciliosVendedorScreen />);

  await waitFor(() => expect(view.getByText('Ningún domiciliario lo tomó a tiempo')).toBeTruthy());
  expect(listarDomiciliosVendedor).toHaveBeenLastCalledWith({ page: 0, size: 20 });
  await fireEvent.press(view.getByRole('radio', { name: 'Cancelados' }));
  await waitFor(() => expect(listarDomiciliosVendedor).toHaveBeenLastCalledWith({
    page: 0,
    size: 20,
    estado: 'CANCELADO',
  }));
  await waitFor(() => expect(view.getByRole('button', { name: 'Abrir pedido del domicilio Cancelado' })).toBeTruthy());
  await fireEvent.press(view.getByRole('button', { name: 'Abrir pedido del domicilio Cancelado' }));
  expect(router.push).toHaveBeenCalledWith({
    pathname: '/pedido-vendedor',
    params: { pedidoId: 'order-id' },
  });
});

it('validates dates and distinguishes an empty list from a filter without results', async () => {
  jest.mocked(listarDomiciliosVendedor).mockResolvedValue({ ...deliveryPage, content: [], totalElements: 0 });
  const view = await render(<DomiciliosVendedorScreen />);

  await waitFor(() => expect(view.getByText('Aún no tienes domicilios.')).toBeTruthy());
  await fireEvent.changeText(view.getByLabelText('Desde (AAAA-MM-DD)'), '13/11/2026');
  await fireEvent.press(view.getByRole('button', { name: 'Aplicar fechas' }));
  await waitFor(() => expect(view.getByText('Escribe las fechas con el formato AAAA-MM-DD.')).toBeTruthy());
  expect(listarDomiciliosVendedor).toHaveBeenCalledTimes(1);

  await fireEvent.changeText(view.getByLabelText('Desde (AAAA-MM-DD)'), '2026-11-01');
  await fireEvent.press(view.getByRole('button', { name: 'Aplicar fechas' }));
  await waitFor(() => expect(listarDomiciliosVendedor).toHaveBeenLastCalledWith({
    page: 0,
    size: 20,
    desde: '2026-11-01',
  }));
  await waitFor(() => expect(view.getByText('Sin resultados.')).toBeTruthy());
  await fireEvent.press(view.getByRole('button', { name: 'Limpiar filtros' }));
  await waitFor(() => expect(view.getByText('Aún no tienes domicilios.')).toBeTruthy());
});

it('does not request deliveries for a street vendor', async () => {
  jest.mocked(getValidSession).mockResolvedValue({ ...fixedSeller, rol: 'VENDEDOR_AMBULANTE' });
  const view = await render(<DomiciliosVendedorScreen />);

  await waitFor(() => expect(view.getByText(
    'Solo los vendedores de punto fijo publican y consultan domicilios.',
  )).toBeTruthy());
  expect(listarDomiciliosVendedor).not.toHaveBeenCalled();
});
