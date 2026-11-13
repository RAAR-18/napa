import { fireEvent, render, waitFor } from '@testing-library/react-native';
import * as Crypto from 'expo-crypto';

import CuentaDomiciliarioScreen from '../cuenta-domiciliario';
import HistorialPagosScreen from '../historial-pagos';
import PagoTarifaDomicilio from '../../components/PagoTarifaDomicilio';
import { ApiError } from '../../lib/api';
import { getValidSession } from '../../lib/auth';
import {
  confirmarPagoTarifa,
  consultarPagoTarifa,
  guardarCuentaDomiciliario,
  listarPagos,
  obtenerCuentaDomiciliario,
  registrarPagoTarifa,
  type EstadoPagoTarifa,
} from '../../lib/pagos';

jest.mock('expo-router', () => ({
  router: { back: jest.fn(), push: jest.fn(), replace: jest.fn() },
}));
jest.mock('expo-crypto', () => ({ randomUUID: jest.fn() }));
jest.mock('../../lib/auth', () => ({ clearSession: jest.fn(), getValidSession: jest.fn() }));
jest.mock('../../lib/pagos', () => ({
  ...jest.requireActual('../../lib/pagos'),
  confirmarPagoTarifa: jest.fn(),
  consultarPagoTarifa: jest.fn(),
  guardarCuentaDomiciliario: jest.fn(),
  listarPagos: jest.fn(),
  obtenerCuentaDomiciliario: jest.fn(),
  registrarPagoTarifa: jest.fn(),
}));

function session(rol: string) {
  return {
    accessToken: 'access',
    refreshToken: 'refresh',
    rol,
    expiresIn: 900,
    expiresAt: Date.now() + 900000,
    tokenType: 'Bearer',
  };
}

const sinRegistrar: EstadoPagoTarifa = {
  domicilioId: 'delivery-id',
  pagoId: null,
  estado: 'SIN_REGISTRAR',
  monto: 5000,
  medio: 'CUENTA_BANCARIA',
  registradoAt: null,
  confirmadoAt: null,
  cuentaDomiciliario: { banco: 'Nequi', tipoCuenta: 'AHORROS', numeroCuenta: '3001112233' },
};
const registrado: EstadoPagoTarifa = {
  ...sinRegistrar,
  pagoId: 'payment-id',
  estado: 'REGISTRADO',
  registradoAt: '2026-11-13T18:00:00Z',
  cuentaDomiciliario: null,
};

beforeEach(() => {
  jest.clearAllMocks();
  let keyCounter = 0;
  jest.mocked(Crypto.randomUUID).mockImplementation(() => `key-${++keyCounter}` as ReturnType<typeof Crypto.randomUUID>);
});

describe('tarifa del domicilio (RF-43)', () => {
  it('shows the courier account and registers the transfer after confirming', async () => {
    jest.mocked(consultarPagoTarifa).mockResolvedValue(sinRegistrar);
    jest.mocked(registrarPagoTarifa).mockResolvedValue({ ...registrado, cuentaDomiciliario: null });
    const view = await render(<PagoTarifaDomicilio domicilioId="delivery-id" rol="VENDEDOR" />);

    await waitFor(() => expect(view.getByText('Cuenta: 3001112233')).toBeTruthy());
    await fireEvent.press(view.getByRole('button', { name: 'Registrar pago de la tarifa' }));
    expect(registrarPagoTarifa).not.toHaveBeenCalled();
    expect(view.getByText(/¿Ya le transferiste/)).toBeTruthy();
    await fireEvent.press(view.getByRole('button', { name: 'Sí, confirmar' }));

    await waitFor(() => expect(registrarPagoTarifa).toHaveBeenCalledWith('delivery-id', 'key-1'));
    await waitFor(() => expect(view.getByText(/Falta que el domiciliario lo confirme/)).toBeTruthy());
    expect(view.queryByRole('button', { name: 'Registrar pago de la tarifa' })).toBeNull();
  });

  it('tells the seller when the courier has no account yet', async () => {
    jest.mocked(consultarPagoTarifa).mockResolvedValue({ ...sinRegistrar, cuentaDomiciliario: null });
    const view = await render(<PagoTarifaDomicilio domicilioId="delivery-id" rol="VENDEDOR" />);

    await waitFor(() => expect(view.getByText(/aún no registró su cuenta bancaria/)).toBeTruthy());
  });

  it('lets the courier confirm only once the seller registered it', async () => {
    jest.mocked(consultarPagoTarifa).mockResolvedValue(registrado);
    jest.mocked(confirmarPagoTarifa).mockResolvedValue({
      ...registrado, estado: 'CONFIRMADO', confirmadoAt: '2026-11-13T19:00:00Z',
    });
    const view = await render(<PagoTarifaDomicilio domicilioId="delivery-id" rol="DOMICILIARIO" />);

    await waitFor(() => expect(view.getByText(/Revisa tu cuenta y confírmalo/)).toBeTruthy());
    await fireEvent.press(view.getByRole('button', { name: 'Confirmé que recibí mi tarifa' }));
    await fireEvent.press(view.getByRole('button', { name: 'Sí, confirmar' }));

    await waitFor(() => expect(confirmarPagoTarifa).toHaveBeenCalledWith('delivery-id', 'key-1'));
    await waitFor(() => expect(view.getByText(/Pago confirmado el/)).toBeTruthy());
  });

  it('does not offer the confirmation before the seller registers it', async () => {
    jest.mocked(consultarPagoTarifa).mockResolvedValue({ ...sinRegistrar, cuentaDomiciliario: null });
    const view = await render(<PagoTarifaDomicilio domicilioId="delivery-id" rol="DOMICILIARIO" />);

    await waitFor(() => expect(view.getByText('El vendedor aún no registra el pago de tu tarifa.')).toBeTruthy());
    expect(view.queryByRole('button', { name: 'Confirmé que recibí mi tarifa' })).toBeNull();
  });

  it('explains a business error and reloads the state', async () => {
    jest.mocked(consultarPagoTarifa).mockResolvedValue(registrado);
    jest.mocked(confirmarPagoTarifa).mockRejectedValue(new ApiError('Ya', 409, 'ALREADY_CONFIRMED'));
    const view = await render(<PagoTarifaDomicilio domicilioId="delivery-id" rol="DOMICILIARIO" />);

    await waitFor(() => expect(view.getByRole('button', { name: 'Confirmé que recibí mi tarifa' })).toBeTruthy());
    await fireEvent.press(view.getByRole('button', { name: 'Confirmé que recibí mi tarifa' }));
    await fireEvent.press(view.getByRole('button', { name: 'Sí, confirmar' }));

    await waitFor(() => expect(view.getByText('Ya confirmaste este pago.')).toBeTruthy());
    expect(consultarPagoTarifa).toHaveBeenCalledTimes(2);
  });
});

describe('cuenta del domiciliario', () => {
  it('saves the account and shows it masked', async () => {
    jest.mocked(getValidSession).mockResolvedValue(session('DOMICILIARIO'));
    jest.mocked(obtenerCuentaDomiciliario).mockResolvedValue({ banco: null, tipoCuenta: null, numeroCuenta: null });
    jest.mocked(guardarCuentaDomiciliario).mockResolvedValue({
      banco: 'Nequi', tipoCuenta: 'AHORROS', numeroCuenta: '••••••2233',
    });
    const view = await render(<CuentaDomiciliarioScreen />);

    await waitFor(() => expect(view.getByText(/Aún no registras una cuenta/)).toBeTruthy());
    await fireEvent.changeText(view.getByLabelText('Banco'), 'Nequi');
    await fireEvent.press(view.getByRole('radio', { name: 'Ahorros' }));
    await fireEvent.changeText(view.getByLabelText('Número de cuenta'), '3001112233');
    await fireEvent.press(view.getByRole('button', { name: 'Guardar cuenta' }));

    await waitFor(() => expect(guardarCuentaDomiciliario).toHaveBeenCalledWith({
      banco: 'Nequi', tipoCuenta: 'AHORROS', numeroCuenta: '3001112233',
    }));
    await waitFor(() => expect(view.getByText(/••••••2233/)).toBeTruthy());
  });

  it('validates the account before saving', async () => {
    jest.mocked(getValidSession).mockResolvedValue(session('DOMICILIARIO'));
    jest.mocked(obtenerCuentaDomiciliario).mockResolvedValue({ banco: null, tipoCuenta: null, numeroCuenta: null });
    const view = await render(<CuentaDomiciliarioScreen />);

    await waitFor(() => expect(view.getByRole('button', { name: 'Guardar cuenta' })).toBeTruthy());
    await fireEvent.press(view.getByRole('button', { name: 'Guardar cuenta' }));

    expect(view.getByText(/Completa el banco, el tipo de cuenta/)).toBeTruthy();
    expect(guardarCuentaDomiciliario).not.toHaveBeenCalled();
  });
});

describe('historial de pagos (RF-40 y RF-41)', () => {
  const pagina = {
    content: [{
      id: 'payment-id',
      tipo: 'PEDIDO' as const,
      monto: 14500,
      medio: 'CUENTA_BANCARIA' as const,
      estado: 'CONFIRMADO' as const,
      createdAt: '2026-11-13T12:00:00Z',
      confirmadoAt: '2026-11-13T12:30:00Z',
      pedidoId: 'order-id',
      domicilioId: null,
      modalidad: 'DOMICILIO' as const,
      comprador: { id: 'client-id', nombre: 'Ana Pérez' },
      vendedor: { id: 'seller-id', nombre: 'Rosa Martínez' },
      domiciliario: null,
    }],
    page: 0,
    size: 20,
    totalElements: 1,
  };

  it('lists the payments and filters by method', async () => {
    jest.mocked(getValidSession).mockResolvedValue(session('VENDEDOR_PUNTO_FIJO'));
    jest.mocked(listarPagos).mockResolvedValue(pagina);
    const view = await render(<HistorialPagosScreen />);

    await waitFor(() => expect(view.getByText('Pago de pedido')).toBeTruthy());
    expect(view.getByText('Cuenta bancaria · Confirmado')).toBeTruthy();
    expect(view.getByText('Modalidad: Domicilio')).toBeTruthy();
    expect(view.queryByText('Comprador: Ana Pérez')).toBeNull();
    await fireEvent.press(view.getByRole('radio', { name: 'Efectivo' }));

    await waitFor(() => expect(listarPagos).toHaveBeenLastCalledWith({
      medio: 'EFECTIVO', estado: undefined, tipo: undefined, page: 0, size: 20,
    }));
  });

  it('shows the people involved to the administrator', async () => {
    jest.mocked(getValidSession).mockResolvedValue(session('ADMINISTRADOR'));
    jest.mocked(listarPagos).mockResolvedValue(pagina);
    const view = await render(<HistorialPagosScreen />);

    await waitFor(() => expect(view.getByText('Historial general de pagos')).toBeTruthy());
    await waitFor(() => expect(view.getByText('Comprador: Ana Pérez')).toBeTruthy());
    expect(view.getByText('Vendedor: Rosa Martínez')).toBeTruthy();
  });

  it('shows an empty message', async () => {
    jest.mocked(getValidSession).mockResolvedValue(session('CLIENTE'));
    jest.mocked(listarPagos).mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0 });
    const view = await render(<HistorialPagosScreen />);

    await waitFor(() => expect(view.getByText('Aún no tienes pagos registrados.')).toBeTruthy());
    expect(view.queryByRole('radio', { name: 'Tarifas de domicilio' })).toBeNull();
  });
});
