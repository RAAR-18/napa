import { jsonHeaders, requestJson } from './api';
import { withValidSession, type StoredSession } from './auth';
import { guardarConfiguracionPago, obtenerConfiguracionPago } from './pagos';

jest.mock('expo-router', () => ({ router: { replace: jest.fn() } }));
jest.mock('./api', () => ({
  ...jest.requireActual('./api'),
  requestJson: jest.fn(),
}));
jest.mock('./auth', () => ({ withValidSession: jest.fn() }));

const session: StoredSession = {
  accessToken: 'access',
  refreshToken: 'refresh',
  rol: 'VENDEDOR_PUNTO_FIJO',
  tokenType: 'Bearer',
  expiresIn: 900,
  expiresAt: Date.now() + 900000,
};

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(withValidSession).mockImplementation((operation) => operation(session));
  jest.mocked(requestJson).mockResolvedValue({});
});

it('reads the private payment configuration with the authenticated session', async () => {
  await obtenerConfiguracionPago();

  expect(requestJson).toHaveBeenCalledWith(
    '/api/v1/vendedores/me/configuracion-pago',
    { method: 'GET', headers: jsonHeaders({ accessToken: 'access' }) },
  );
});

it('replaces payment settings with PUT and keeps the same body during session renewal', async () => {
  jest.mocked(withValidSession).mockImplementation(async (operation) => {
    await operation(session);
    return operation({ ...session, accessToken: 'renewed' });
  });
  const input = {
    aceptaEfectivo: true,
    cuentaBancaria: {
      banco: 'Banco de prueba',
      tipoCuenta: 'AHORROS' as const,
      numeroCuenta: '00001234',
    },
  };

  await guardarConfiguracionPago(input);

  const calls = jest.mocked(requestJson).mock.calls;
  expect(calls).toHaveLength(2);
  expect(calls[0][0]).toBe('/api/v1/vendedores/me/configuracion-pago');
  expect(calls[0][1]).toEqual({
    method: 'PUT',
    headers: jsonHeaders({ accessToken: 'access' }),
    body: JSON.stringify(input),
  });
  expect(calls[1][1].body).toBe(calls[0][1].body);
  expect(calls[1][1].headers).toEqual(jsonHeaders({ accessToken: 'renewed' }));
});
