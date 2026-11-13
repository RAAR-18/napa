import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { router } from 'expo-router';

import ConfiguracionPagosScreen from '../configuracion-pagos';
import { ApiError } from '../../lib/api';
import { clearSession, getValidSession } from '../../lib/auth';
import {
  guardarConfiguracionPago,
  obtenerConfiguracionPago,
  type ConfiguracionPago,
} from '../../lib/pagos';

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn() },
}));
jest.mock('../../lib/auth', () => ({
  clearSession: jest.fn(),
  getValidSession: jest.fn(),
}));
jest.mock('../../lib/pagos', () => ({
  guardarConfiguracionPago: jest.fn(),
  obtenerConfiguracionPago: jest.fn(),
}));

const seller = {
  accessToken: 'access',
  refreshToken: 'refresh',
  rol: 'VENDEDOR_PUNTO_FIJO',
  expiresIn: 900,
  expiresAt: Date.now() + 900000,
  tokenType: 'Bearer',
};
const paymentConfiguration: ConfiguracionPago = {
  aceptaEfectivo: false,
  cuentaBancaria: null,
  mediosHabilitados: [],
  domicilioDisponible: false,
  avisoDomicilio: 'El domicilio siempre exige cuenta bancaria registrada.',
};

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(getValidSession).mockResolvedValue(seller);
  jest.mocked(obtenerConfiguracionPago).mockResolvedValue(paymentConfiguration);
  jest.mocked(guardarConfiguracionPago).mockResolvedValue({
    ...paymentConfiguration,
    aceptaEfectivo: true,
    cuentaBancaria: {
      banco: 'Banco de prueba',
      tipoCuenta: 'AHORROS',
      numeroCuenta: '00001234',
    },
    mediosHabilitados: ['EFECTIVO', 'CUENTA_BANCARIA'],
    domicilioDisponible: true,
  });
});

it('loads the seller settings and explains the bank-account requirement for delivery', async () => {
  const view = await render(<ConfiguracionPagosScreen />);

  await waitFor(() => expect(obtenerConfiguracionPago).toHaveBeenCalled());
  expect(view.getByText('El domicilio siempre exige cuenta bancaria registrada.')).toBeTruthy();
  expect(view.getByText('Registra una cuenta bancaria para habilitar domicilios.')).toBeTruthy();
  expect(view.getByText('Métodos habilitados: ninguno')).toBeTruthy();
});

it('validates a partial bank account, then saves complete settings', async () => {
  const view = await render(<ConfiguracionPagosScreen />);
  await waitFor(() => expect(view.getByLabelText('Banco')).toBeTruthy());
  await fireEvent.press(view.getByRole('radio', { name: 'Acepto efectivo' }));
  await fireEvent.changeText(view.getByLabelText('Banco'), 'Banco de prueba');
  await fireEvent.press(view.getByRole('button', { name: 'Guardar métodos de pago' }));
  await waitFor(() =>
    expect(view.getByText(/Completa el banco, el tipo de cuenta/)).toBeTruthy(),
  );
  expect(guardarConfiguracionPago).not.toHaveBeenCalled();

  await fireEvent.press(view.getByRole('radio', { name: 'Ahorros' }));
  await fireEvent.changeText(view.getByLabelText('Número de cuenta'), '00001234');
  await fireEvent.press(view.getByRole('button', { name: 'Guardar métodos de pago' }));

  await waitFor(() => expect(guardarConfiguracionPago).toHaveBeenCalledWith({
    aceptaEfectivo: true,
    cuentaBancaria: {
      banco: 'Banco de prueba',
      tipoCuenta: 'AHORROS',
      numeroCuenta: '00001234',
    },
  }));
  expect(view.getByText('Tu negocio puede ofrecer domicilios.')).toBeTruthy();
  expect(view.getByText('Métodos habilitados: EFECTIVO, CUENTA_BANCARIA')).toBeTruthy();
});

it('does not expose payment settings to a client', async () => {
  jest.mocked(getValidSession).mockResolvedValue({ ...seller, rol: 'CLIENTE' });
  const view = await render(<ConfiguracionPagosScreen />);

  await waitFor(() =>
    expect(view.getByText('Solo las cuentas de vendedor pueden configurar sus métodos de pago.')).toBeTruthy(),
  );
  expect(obtenerConfiguracionPago).not.toHaveBeenCalled();
  expect(view.queryByRole('button', { name: 'Guardar métodos de pago' })).toBeNull();
});

it('does not show an empty form when loading fails and allows a safe retry', async () => {
  jest.mocked(obtenerConfiguracionPago)
    .mockRejectedValueOnce(new Error('No se pudo conectar con la API.'))
    .mockResolvedValueOnce(paymentConfiguration);
  const view = await render(<ConfiguracionPagosScreen />);

  await waitFor(() => expect(view.getByText('No se pudo conectar con la API.')).toBeTruthy());
  expect(view.queryByRole('button', { name: 'Guardar métodos de pago' })).toBeNull();
  await fireEvent.press(view.getByRole('button', { name: 'Reintentar carga' }));

  await waitFor(() => expect(obtenerConfiguracionPago).toHaveBeenCalledTimes(2));
  expect(view.getByRole('button', { name: 'Guardar métodos de pago' })).toBeTruthy();
});

it('clears and redirects when authentication has expired', async () => {
  jest.mocked(obtenerConfiguracionPago).mockRejectedValue(
    new ApiError('Sesión vencida', 401, 'UNAUTHORIZED'),
  );
  const view = await render(<ConfiguracionPagosScreen />);

  await waitFor(() => expect(router.replace).toHaveBeenCalledWith('/login'));
  expect(clearSession).toHaveBeenCalled();
  expect(view.queryByRole('button', { name: 'Guardar métodos de pago' })).toBeNull();
});
