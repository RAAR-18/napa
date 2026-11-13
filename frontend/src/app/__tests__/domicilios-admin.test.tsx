import { fireEvent, render, waitFor } from '@testing-library/react-native';
import * as Crypto from 'expo-crypto';
import { router } from 'expo-router';

import DomicilioAdminScreen from '../domicilio-admin';
import DomiciliosAdminScreen from '../domicilios-admin';
import { ApiError } from '../../lib/api';
import { getValidSession } from '../../lib/auth';
import {
  cancelarDomicilioAdmin,
  listarDomiciliosAdmin,
  obtenerDomicilioAdmin,
  type DomicilioAdminDetalle,
  type DomicilioAdminFila,
} from '../../lib/domicilios';

const mockParams: { current: Record<string, string> } = { current: {} };

jest.mock('expo-router', () => ({
  router: { back: jest.fn(), push: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: () => mockParams.current,
}));
jest.mock('expo-crypto', () => ({ randomUUID: jest.fn() }));
jest.mock('../../lib/auth', () => ({ clearSession: jest.fn(), getValidSession: jest.fn() }));
jest.mock('../../lib/domicilios', () => ({
  ...jest.requireActual('../../lib/domicilios'),
  cancelarDomicilioAdmin: jest.fn(),
  listarDomiciliosAdmin: jest.fn(),
  obtenerDomicilioAdmin: jest.fn(),
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

const fila: DomicilioAdminFila = {
  id: 'delivery-id',
  pedidoId: 'order-id',
  estado: 'DISPONIBLE',
  tarifa: 5000,
  minutosSinTomar: 42,
  origen: { latitud: 11.24, longitud: -74.21, direccion: 'Carrera 5 # 10-20', barrio: null },
  destino: { latitud: 11.25, longitud: -74.2, direccion: 'Calle 15 # 3-20', barrio: 'El Prado' },
  vendedor: { id: 'seller-id', nombre: 'Rosa Martínez' },
  cliente: { id: 'client-id', nombre: 'Ana Pérez' },
  domiciliario: null,
  motivoCancelacion: null,
  createdAt: '2026-11-13T17:00:00Z',
  updatedAt: '2026-11-13T17:00:00Z',
};

const enCamino: DomicilioAdminDetalle = {
  ...fila,
  estado: 'EN_CAMINO',
  minutosSinTomar: null,
  domiciliario: { id: 'courier-id', nombre: 'Luis Gómez' },
  pedido: { id: 'order-id', modalidad: 'DOMICILIO', estado: 'EN_CAMINO', total: 14500, cantidadItems: 3 },
  motivoCancelacion: null,
  detalleCancelacion: null,
  pagoTarifa: null,
  trazabilidad: [
    { estado: 'DISPONIBLE', ocurridoAt: '2026-11-13T17:00:00Z', autor: { id: 'seller-id', nombre: 'Rosa Martínez' } },
    { estado: 'ASIGNADO', ocurridoAt: '2026-11-13T17:10:00Z', autor: { id: 'courier-id', nombre: 'Luis Gómez' } },
  ],
};

const MOTIVO = 'El cliente reportó que el domiciliario no llegó';

beforeEach(() => {
  jest.clearAllMocks();
  mockParams.current = {};
  jest.mocked(getValidSession).mockResolvedValue(session('ADMINISTRADOR'));
  let keyCounter = 0;
  jest.mocked(Crypto.randomUUID).mockImplementation(() => `key-${++keyCounter}` as ReturnType<typeof Crypto.randomUUID>);
});

describe('supervisión de domicilios (RF-12)', () => {
  it('lists every delivery with its basic data and highlights the ones nobody took', async () => {
    jest.mocked(listarDomiciliosAdmin).mockResolvedValue({ content: [fila], page: 0, size: 20, totalElements: 1 });
    const view = await render(<DomiciliosAdminScreen />);

    await waitFor(() => expect(view.getByText('42 min sin domiciliario')).toBeTruthy());
    expect(view.getByText('De: Carrera 5 # 10-20')).toBeTruthy();
    expect(view.getByText('A: Calle 15 # 3-20 (El Prado)')).toBeTruthy();
    expect(view.getByText('Vendedor: Rosa Martínez')).toBeTruthy();
    expect(view.getByText('Cliente: Ana Pérez')).toBeTruthy();
    expect(listarDomiciliosAdmin).toHaveBeenCalledWith({ page: 0, size: 20 });

    await fireEvent.press(view.getByRole('radio', { name: 'Cancelados' }));
    await waitFor(() => expect(listarDomiciliosAdmin).toHaveBeenLastCalledWith({ page: 0, size: 20, estado: 'CANCELADO' }));
  });

  it('shows the empty message and filters by the person passed from the detail', async () => {
    mockParams.current = { domiciliarioId: 'courier-id', persona: 'Luis Gómez' };
    jest.mocked(listarDomiciliosAdmin).mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0 });
    const view = await render(<DomiciliosAdminScreen />);

    await waitFor(() => expect(view.getByText('No hay domicilios con esos filtros.')).toBeTruthy());
    expect(listarDomiciliosAdmin).toHaveBeenCalledWith({ page: 0, size: 20, domiciliarioId: 'courier-id' });
    expect(view.getByText(/Solo los domicilios del domiciliario Luis Gómez/)).toBeTruthy();

    await fireEvent.press(view.getByRole('button', { name: 'Ver todos' }));
    await waitFor(() => expect(view.getByText('No hay domicilios registrados.')).toBeTruthy());
  });

  it('opens the detail with the order, the fee payment and the timeline with authors', async () => {
    mockParams.current = { domicilioId: 'delivery-id' };
    jest.mocked(obtenerDomicilioAdmin).mockResolvedValue(enCamino);
    const view = await render(<DomicilioAdminScreen />);

    await waitFor(() => expect(view.getByText('Domiciliario: Luis Gómez')).toBeTruthy());
    expect(view.getByText(/3 productos/)).toBeTruthy();
    expect(view.getByText('Sin registrar')).toBeTruthy();
    expect(view.getByText(/Asignado · .* · Luis Gómez/)).toBeTruthy();
    expect(view.queryByText(/código/i)).toBeNull();

    await fireEvent.press(view.getByRole('button', { name: 'Ver domicilios del domiciliario' }));
    expect(router.push).toHaveBeenCalledWith({
      pathname: '/domicilios-admin',
      params: { domiciliarioId: 'courier-id', persona: 'Luis Gómez' },
    });
  });

  it('shows the cancellation reason and does not offer to cancel again', async () => {
    mockParams.current = { domicilioId: 'delivery-id' };
    jest.mocked(obtenerDomicilioAdmin).mockResolvedValue({
      ...enCamino,
      estado: 'CANCELADO',
      motivoCancelacion: 'ADMINISTRADOR',
      detalleCancelacion: MOTIVO,
    });
    const view = await render(<DomicilioAdminScreen />);

    await waitFor(() => expect(view.getByText(`Motivo: ${MOTIVO}`)).toBeTruthy());
    expect(view.queryByRole('button', { name: 'Cancelar domicilio' })).toBeNull();
  });

  it('explains a missing delivery', async () => {
    mockParams.current = { domicilioId: 'missing-id' };
    jest.mocked(obtenerDomicilioAdmin).mockRejectedValue(new ApiError('No', 404, 'NOT_FOUND'));
    const view = await render(<DomicilioAdminScreen />);

    await waitFor(() => expect(view.getByText('El domicilio no existe.')).toBeTruthy());
  });
});

describe('cancelación por el administrador (RF-13)', () => {
  beforeEach(() => {
    mockParams.current = { domicilioId: 'delivery-id' };
  });

  it('requires a reason before confirming and then cancels', async () => {
    jest.mocked(obtenerDomicilioAdmin)
      .mockResolvedValueOnce(enCamino)
      .mockResolvedValueOnce({ ...enCamino, estado: 'CANCELADO', motivoCancelacion: 'ADMINISTRADOR', detalleCancelacion: MOTIVO });
    jest.mocked(cancelarDomicilioAdmin).mockResolvedValue({
      id: 'delivery-id', estado: 'CANCELADO', motivoCancelacion: MOTIVO, pedido: { id: 'order-id', estado: 'CANCELADO' },
    });
    const view = await render(<DomicilioAdminScreen />);

    await waitFor(() => expect(view.getByRole('button', { name: 'Cancelar domicilio' })).toBeTruthy());
    await fireEvent.press(view.getByRole('button', { name: 'Cancelar domicilio' }));
    expect(view.getByText(/y al domiciliario, que quedará libre/)).toBeTruthy();
    await fireEvent.changeText(view.getByLabelText('Motivo de la cancelación'), 'corto');
    await fireEvent.press(view.getByRole('button', { name: 'Confirmar cancelación' }));
    expect(cancelarDomicilioAdmin).not.toHaveBeenCalled();
    expect(view.getByText(/Escribe al menos 10 caracteres/)).toBeTruthy();

    await fireEvent.changeText(view.getByLabelText('Motivo de la cancelación'), `  ${MOTIVO} `);
    await fireEvent.press(view.getByRole('button', { name: 'Confirmar cancelación' }));

    await waitFor(() => expect(cancelarDomicilioAdmin).toHaveBeenCalledWith('delivery-id', MOTIVO, 'key-1'));
    await waitFor(() => expect(view.getByText(/Domicilio cancelado. Avisamos/)).toBeTruthy());
    await waitFor(() => expect(view.getByText(`Motivo: ${MOTIVO}`)).toBeTruthy());
  });

  it('warns that money is returned outside the app when the fee was paid', async () => {
    jest.mocked(obtenerDomicilioAdmin).mockResolvedValue({
      ...enCamino, pagoTarifa: { estado: 'REGISTRADO', monto: 5000 },
    });
    const view = await render(<DomicilioAdminScreen />);

    await waitFor(() => expect(view.getByRole('button', { name: 'Cancelar domicilio' })).toBeTruthy());
    await fireEvent.press(view.getByRole('button', { name: 'Cancelar domicilio' }));

    expect(view.getByText(/la devolución se acuerda fuera de la app/)).toBeTruthy();
    expect(view.getByText(/Ya hay un pago de la tarifa registrado/)).toBeTruthy();
  });

  it('explains when the delivery was delivered meanwhile and reloads', async () => {
    jest.mocked(obtenerDomicilioAdmin)
      .mockResolvedValueOnce(enCamino)
      .mockResolvedValueOnce({ ...enCamino, estado: 'ENTREGADO' });
    jest.mocked(cancelarDomicilioAdmin).mockRejectedValue(
      new ApiError('No', 409, 'INVALID_STATE', { estadoActual: 'ENTREGADO' }),
    );
    const view = await render(<DomicilioAdminScreen />);

    await waitFor(() => expect(view.getByRole('button', { name: 'Cancelar domicilio' })).toBeTruthy());
    await fireEvent.press(view.getByRole('button', { name: 'Cancelar domicilio' }));
    await fireEvent.changeText(view.getByLabelText('Motivo de la cancelación'), MOTIVO);
    await fireEvent.press(view.getByRole('button', { name: 'Confirmar cancelación' }));

    await waitFor(() => expect(obtenerDomicilioAdmin).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(view.queryByRole('button', { name: 'Cancelar domicilio' })).toBeNull());
    expect(view.getByText('El domicilio ya fue entregado y no se puede cancelar.')).toBeTruthy();
  });
});
