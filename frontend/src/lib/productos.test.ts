import * as Crypto from 'expo-crypto';
import { jsonHeaders, requestJson, requestVoid } from './api';
import { withValidSession, type StoredSession } from './auth';
import { createProducto, deleteProducto, editProducto, getMiProducto, listCatalogo, listMisProductos, parseCantidad, parsePrecio, updateInventario } from './productos';

jest.mock('expo-router', () => ({ router: { replace: jest.fn() } }));
jest.mock('expo-crypto', () => ({ randomUUID: jest.fn() }));
jest.mock('./api', () => ({
  ...jest.requireActual('./api'), requestJson: jest.fn(), requestVoid: jest.fn(),
}));
jest.mock('./auth', () => ({ withValidSession: jest.fn(), clearSession: jest.fn() }));

const session: StoredSession = {
  accessToken: 'access', refreshToken: 'refresh', rol: 'VENDEDOR_AMBULANTE',
  tokenType: 'Bearer', expiresIn: 900, expiresAt: Date.now() + 900000,
};
const input = { nombre: 'Mango', precio: 3500, unidad: 'KG' as const, descripcion: null, cantidadDisponible: 12.5 };

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(Crypto.randomUUID).mockReturnValue('operation-id');
  jest.mocked(withValidSession).mockImplementation((operation) => operation(session));
  jest.mocked(requestJson).mockResolvedValue({});
  jest.mocked(requestVoid).mockResolvedValue();
});

it('publishes using the authenticated owner and the expected idempotency header', async () => {
  await createProducto(input);
  expect(requestJson).toHaveBeenCalledWith('/api/v1/productos', {
    method: 'POST', headers: jsonHeaders({ accessToken: 'access', idempotencyKey: 'operation-id' }),
    body: JSON.stringify(input),
  });
});

it('reuses the operation key and body when a session renewal retries the request', async () => {
  jest.mocked(withValidSession).mockImplementation(async (operation) => {
    await operation(session);
    return operation({ ...session, accessToken: 'renewed' });
  });
  await updateInventario('product-id', 0.25);
  const calls = jest.mocked(requestJson).mock.calls;
  expect(Crypto.randomUUID).toHaveBeenCalledTimes(1);
  expect(calls[0][0]).toBe('/api/v1/productos/product-id/inventario');
  expect(calls[0][1].body).toBe('{"cantidadDisponible":0.25}');
  expect(calls[1][1].body).toBe(calls[0][1].body);
  expect(calls[1][1].headers).toEqual(jsonHeaders({ accessToken: 'renewed', idempotencyKey: 'operation-id' }));
});

it('queries separate seller and client listings with pagination and ordering', async () => {
  await listMisProductos(2, 'precio,desc');
  await listCatalogo('business-id', 0);
  expect(requestJson).toHaveBeenNthCalledWith(1, '/api/v1/productos?page=2&size=20&sort=precio%2Cdesc',
    expect.objectContaining({ method: 'GET' }));
  expect(requestJson).toHaveBeenNthCalledWith(2, '/api/v1/emprendimientos/business-id/productos?page=0&size=20&sort=nombre%2Casc',
    expect.objectContaining({ method: 'GET' }));
});

it('accepts comma decimals, allows zero inventory, and rejects invalid quantities before sending', () => {
  expect(parseCantidad('12,50', true)).toBe(12.5);
  expect(parseCantidad('0')).toBe(0);
  for (const invalid of ['-1', '1.234', '1,2,3', '', '1e2', '100000000', 'Infinity']) {
    expect(() => parseCantidad(invalid)).toThrow();
  }
  expect(() => parseCantidad('0', true)).toThrow();
});

it('loads the owner product and preserves the version precondition across a renewed-session retry', async () => {
  await getMiProducto('product-id');
  expect(requestJson).toHaveBeenCalledWith('/api/v1/productos/product-id', expect.objectContaining({ method: 'GET' }));
  jest.mocked(requestJson).mockClear();
  jest.mocked(withValidSession).mockImplementation(async (operation) => {
    await operation(session);
    return operation({ ...session, accessToken: 'renewed' });
  });
  await editProducto('product-id', { precio: 5000, descripcion: null }, 7);
  const calls = jest.mocked(requestJson).mock.calls;
  expect(calls).toHaveLength(2);
  expect(calls[0][1]).toEqual({ method: 'PATCH', body: '{"precio":5000,"descripcion":null}',
    headers: { ...jsonHeaders({ accessToken: 'access' }), 'If-Match': '"7"' } });
  expect(calls[1][1].headers).toEqual({ ...jsonHeaders({ accessToken: 'renewed' }), 'If-Match': '"7"' });
  expect(Crypto.randomUUID).not.toHaveBeenCalled();
});

it('sends explicit deletion confirmation with the same idempotency key on retry', async () => {
  jest.mocked(withValidSession).mockImplementation(async (operation) => {
    await operation(session);
    return operation({ ...session, accessToken: 'renewed' });
  });
  await deleteProducto('product-id');
  expect(Crypto.randomUUID).toHaveBeenCalledTimes(1);
  expect(requestVoid).toHaveBeenNthCalledWith(1, '/api/v1/productos/product-id/eliminar', {
    method: 'POST', body: '{"confirmar":true}',
    headers: jsonHeaders({ accessToken: 'access', idempotencyKey: 'operation-id' }),
  });
  expect(requestVoid).toHaveBeenNthCalledWith(2, '/api/v1/productos/product-id/eliminar', {
    method: 'POST', body: '{"confirmar":true}',
    headers: jsonHeaders({ accessToken: 'renewed', idempotencyKey: 'operation-id' }),
  });
});

it('rejects non-integer or unsafe prices before saving', () => {
  expect(parsePrecio(' 3500 ')).toBe(3500);
  for (const invalid of ['0', '-1', '3500.50', '3,500', '1e3', '', '9007199254740992']) {
    expect(() => parsePrecio(invalid)).toThrow();
  }
});
