import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { router } from 'expo-router';
import * as Crypto from 'expo-crypto';

import PedidoVendedorScreen from '../pedido-vendedor';
import PedidosVendedorScreen from '../pedidos-vendedor';
import { ApiError } from '../../lib/api';
import { clearSession, getValidSession } from '../../lib/auth';
import {
  aceptarPedido,
  listarPedidosVendedor,
  obtenerPedidoVendedor,
  rechazarPedido,
} from '../../lib/pedidos';

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: () => ({ pedidoId: 'order-id' }),
}));
jest.mock('expo-crypto', () => ({ randomUUID: jest.fn(() => 'response-key') }));
jest.mock('../../lib/auth', () => ({
  clearSession: jest.fn(),
  getValidSession: jest.fn(),
}));
jest.mock('../../lib/pedidos', () => ({
  aceptarPedido: jest.fn(),
  listarPedidosVendedor: jest.fn(),
  obtenerPedidoVendedor: jest.fn(),
  rechazarPedido: jest.fn(),
}));

const seller = {
  accessToken: 'access',
  refreshToken: 'refresh',
  rol: 'VENDEDOR_PUNTO_FIJO',
  expiresIn: 900,
  expiresAt: Date.now() + 900000,
  tokenType: 'Bearer',
};
const page = {
  content: [{
    id: 'order-id',
    cliente: { nombre: 'Ana Pérez' },
    createdAt: '2026-11-12T12:00:00Z',
    estado: 'PENDIENTE' as const,
    modalidad: 'RECOGIDA' as const,
    reserva: null,
    total: 3500,
  }],
  page: 0,
  size: 20,
  totalElements: 1,
};
const pendingOrder = {
  id: 'order-id',
  estado: 'PENDIENTE' as const,
  modalidad: 'ENTREGA_DIRECTA' as const,
  reserva: null,
  createdAt: '2026-11-12T12:00:00Z',
  updatedAt: '2026-11-12T12:00:00Z',
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
  tarifaDomicilio: null,
  total: 3500,
  medioPago: 'EFECTIVO' as const,
  pago: null,
  ubicacionEntrega: {
    latitud: 4.711,
    longitud: -74.0721,
    direccion: 'Carrera 7 # 10-20',
    referencia: null,
  },
};

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(getValidSession).mockResolvedValue(seller);
  jest.mocked(listarPedidosVendedor).mockResolvedValue(page);
  jest.mocked(obtenerPedidoVendedor).mockResolvedValue(pendingOrder);
  jest.mocked(aceptarPedido).mockResolvedValue({
    id: 'order-id',
    estado: 'ACEPTADO',
    modalidad: 'ENTREGA_DIRECTA',
    reserva: null,
    total: 3500,
    pago: { id: 'payment-id', estado: 'PENDIENTE_CONFIRMACION', medio: 'EFECTIVO', monto: 3500 },
  });
  jest.mocked(rechazarPedido).mockResolvedValue({ id: 'order-id', estado: 'RECHAZADO' });
});

it('shows seller orders, filters by reservation, and opens the selected order', async () => {
  const view = await render(<PedidosVendedorScreen />);

  await waitFor(() => expect(view.getByText('Ana Pérez')).toBeTruthy());
  expect(view.getByText('Recogida')).toBeTruthy();
  fireEvent.press(view.getByRole('radio', { name: 'Reservas' }));
  await waitFor(() => expect(listarPedidosVendedor).toHaveBeenLastCalledWith({
    page: 0,
    size: 20,
    reserva: true,
  }));
  fireEvent.press(view.getByRole('button', { name: 'Abrir pedido de Ana Pérez, Pendiente' }));
  expect(router.push).toHaveBeenCalledWith({
    pathname: '/pedido-vendedor',
    params: { pedidoId: 'order-id' },
  });
});

it('shows an empty state and does not request the list for a client', async () => {
  jest.mocked(getValidSession).mockResolvedValue({ ...seller, rol: 'CLIENTE' });
  const view = await render(<PedidosVendedorScreen />);

  await waitFor(() => expect(view.getByText(
    'Solo las cuentas de vendedor pueden consultar los pedidos recibidos.',
  )).toBeTruthy());
  expect(listarPedidosVendedor).not.toHaveBeenCalled();

  jest.mocked(getValidSession).mockResolvedValue(seller);
  jest.mocked(listarPedidosVendedor).mockResolvedValue({ ...page, content: [], totalElements: 0 });
  fireEvent.press(view.getByRole('button', { name: 'Actualizar pedidos' }));
  await waitFor(() => expect(view.getByText(
    'No hay pedidos que coincidan con estos filtros.',
  )).toBeTruthy());
});

it('requires confirmation, reuses the idempotency key after a network failure, and refreshes the accepted order', async () => {
  jest.mocked(obtenerPedidoVendedor)
    .mockResolvedValueOnce(pendingOrder)
    .mockResolvedValueOnce({ ...pendingOrder, estado: 'ACEPTADO', pago: { estado: 'PENDIENTE_CONFIRMACION' } });
  jest.mocked(aceptarPedido)
    .mockRejectedValueOnce(new Error('No se pudo conectar con la API.'))
    .mockResolvedValueOnce({
      id: 'order-id',
      estado: 'ACEPTADO',
      modalidad: 'ENTREGA_DIRECTA',
      reserva: null,
      total: 3500,
      pago: { id: 'payment-id', estado: 'PENDIENTE_CONFIRMACION', medio: 'EFECTIVO', monto: 3500 },
    });
  const view = await render(<PedidoVendedorScreen />);

  await waitFor(() => expect(view.getByText('Carrera 7 # 10-20')).toBeTruthy());
  fireEvent.press(view.getByRole('button', { name: 'Aceptar pedido' }));
  expect(aceptarPedido).not.toHaveBeenCalled();
  await waitFor(() => expect(view.getByRole('button', { name: 'Confirmar aceptación' })).toBeTruthy());
  fireEvent.press(view.getByRole('button', { name: 'Confirmar aceptación' }));
  await waitFor(() => expect(view.getByText('No se pudo conectar con la API.')).toBeTruthy());
  fireEvent.press(view.getByRole('button', { name: 'Confirmar aceptación' }));

  await waitFor(() => expect(aceptarPedido).toHaveBeenLastCalledWith('order-id', 'response-key'));
  expect(Crypto.randomUUID).toHaveBeenCalledTimes(1);
  await waitFor(() => expect(view.getByText('Aceptado')).toBeTruthy());
  expect(view.queryByRole('button', { name: 'Aceptar pedido' })).toBeNull();
});

it('explains bank-account conflicts and provides a link to payment settings', async () => {
  jest.mocked(aceptarPedido).mockRejectedValue(
    new ApiError('Registra una cuenta bancaria primero.', 409, 'BANK_ACCOUNT_REQUIRED'),
  );
  const view = await render(<PedidoVendedorScreen />);

  await waitFor(() => expect(view.getByText('Carrera 7 # 10-20')).toBeTruthy());
  fireEvent.press(view.getByRole('button', { name: 'Aceptar pedido' }));
  await waitFor(() => expect(view.getByRole('button', { name: 'Confirmar aceptación' })).toBeTruthy());
  fireEvent.press(view.getByRole('button', { name: 'Confirmar aceptación' }));
  await waitFor(() => expect(view.getByText(
    'Registra una cuenta bancaria antes de aceptar un pedido a domicilio.',
  )).toBeTruthy());
  fireEvent.press(view.getByRole('button', { name: 'Configurar métodos de pago' }));
  expect(router.push).toHaveBeenCalledWith('/configuracion-pagos');
});

it('rejects only after explicit confirmation', async () => {
  const view = await render(<PedidoVendedorScreen />);

  await waitFor(() => expect(view.getByText('Carrera 7 # 10-20')).toBeTruthy());
  fireEvent.press(view.getByRole('button', { name: 'Rechazar pedido' }));
  expect(rechazarPedido).not.toHaveBeenCalled();
  await waitFor(() => expect(view.getByRole('button', { name: 'Confirmar rechazo' })).toBeTruthy());
  fireEvent.press(view.getByRole('button', { name: 'Confirmar rechazo' }));

  await waitFor(() => expect(rechazarPedido).toHaveBeenCalledWith('order-id', 'response-key'));
});

it('clears and redirects when authentication has expired', async () => {
  jest.mocked(obtenerPedidoVendedor).mockRejectedValue(new ApiError('Sesión vencida', 401, 'UNAUTHORIZED'));
  const view = await render(<PedidoVendedorScreen />);

  await waitFor(() => expect(router.replace).toHaveBeenCalledWith('/login'));
  expect(clearSession).toHaveBeenCalled();
  expect(view.queryByRole('button', { name: 'Aceptar pedido' })).toBeNull();
});
