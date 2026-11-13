import { jsonHeaders, requestJson } from './api';
import { withValidSession, type StoredSession } from './auth';
import {
  aceptarPedido,
  crearPedido,
  cotizarPedido,
  listarPedidosVendedor,
  obtenerPedidoVendedor,
  rechazarPedido,
} from './pedidos';

jest.mock('expo-router', () => ({ router: { replace: jest.fn() } }));
jest.mock('./api', () => ({
  ...jest.requireActual('./api'),
  requestJson: jest.fn(),
}));
jest.mock('./auth', () => ({ withValidSession: jest.fn() }));

const session: StoredSession = {
  accessToken: 'access',
  refreshToken: 'refresh',
  rol: 'CLIENTE',
  tokenType: 'Bearer',
  expiresIn: 900,
  expiresAt: Date.now() + 900000,
};

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(withValidSession).mockImplementation((operation) => operation(session));
  jest.mocked(requestJson).mockResolvedValue({});
});

it('quotes a pickup order without sending an unnecessary location', async () => {
  const input = {
    emprendimientoId: 'business-id',
    modalidad: 'RECOGIDA' as const,
    items: [{ productoId: 'product-id', cantidad: 2 }],
  };

  await cotizarPedido(input);

  expect(requestJson).toHaveBeenCalledWith('/api/v1/pedidos/cotizacion', {
    method: 'POST',
    headers: jsonHeaders({ accessToken: 'access' }),
    body: JSON.stringify(input),
  });
});

it('creates orders with the supplied idempotency key and preserves the request body after session renewal', async () => {
  jest.mocked(withValidSession).mockImplementation(async (operation) => {
    await operation(session);
    return operation({ ...session, accessToken: 'renewed' });
  });
  const input = {
    emprendimientoId: 'business-id',
    modalidad: 'ENTREGA_DIRECTA' as const,
    items: [{ productoId: 'product-id', cantidad: 1 }],
    ubicacionEntrega: {
      latitud: 4.711,
      longitud: -74.0721,
      direccion: 'Carrera 7 # 10-20',
      referencia: null,
    },
    medioPago: 'EFECTIVO' as const,
    totalEsperado: 3500,
  };

  await crearPedido(input, 'stable-key');

  const calls = jest.mocked(requestJson).mock.calls;
  expect(calls).toHaveLength(2);
  expect(calls[0][0]).toBe('/api/v1/pedidos');
  expect(calls[0][1]).toEqual({
    method: 'POST',
    headers: jsonHeaders({ accessToken: 'access', idempotencyKey: 'stable-key' }),
    body: JSON.stringify(input),
  });
  expect(calls[1][1].body).toBe(calls[0][1].body);
  expect(calls[1][1].headers).toEqual(
    jsonHeaders({ accessToken: 'renewed', idempotencyKey: 'stable-key' }),
  );
});

it('lists seller orders with encoded filters and the authenticated token', async () => {
  await listarPedidosVendedor({
    estado: 'PENDIENTE',
    modalidad: 'RECOGIDA',
    reserva: false,
    page: 1,
    size: 20,
  });

  expect(requestJson).toHaveBeenCalledWith(
    '/api/v1/pedidos?estado=PENDIENTE&modalidad=RECOGIDA&reserva=false&page=1&size=20',
    {
      method: 'GET',
      headers: jsonHeaders({ accessToken: 'access' }),
    },
  );
});

it('loads seller details and can accept or reject with stable idempotency keys', async () => {
  await obtenerPedidoVendedor('order-id');
  await aceptarPedido('order-id', 'accept-key');
  await rechazarPedido('order-id', 'reject-key');

  expect(requestJson).toHaveBeenNthCalledWith(1, '/api/v1/pedidos/order-id', {
    method: 'GET',
    headers: jsonHeaders({ accessToken: 'access' }),
  });
  expect(requestJson).toHaveBeenNthCalledWith(2, '/api/v1/pedidos/order-id/aceptar', {
    method: 'POST',
    headers: jsonHeaders({ accessToken: 'access', idempotencyKey: 'accept-key' }),
  });
  expect(requestJson).toHaveBeenNthCalledWith(3, '/api/v1/pedidos/order-id/rechazar', {
    method: 'POST',
    headers: jsonHeaders({ accessToken: 'access', idempotencyKey: 'reject-key' }),
  });
});
