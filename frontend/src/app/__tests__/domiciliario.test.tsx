import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { router } from 'expo-router';
import * as Location from 'expo-location';

import DomicilioDomiciliarioScreen from '../domicilio-domiciliario';
import DomiciliosDisponiblesScreen from '../domicilios-disponibles';
import DomiciliosEnCursoScreen from '../domicilios-en-curso';
import { ApiError } from '../../lib/api';
import { getValidSession } from '../../lib/auth';
import {
  aceptarDomicilio,
  listarDomiciliosDisponibles,
  listarDomiciliosEnCurso,
  obtenerDomicilioDomiciliario,
  rechazarDomicilio,
  type DomicilioDomiciliarioDetalle,
} from '../../lib/domicilios';

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: () => ({ domicilioId: 'delivery-id' }),
}));
jest.mock('expo-crypto', () => ({ randomUUID: jest.fn(() => 'courier-key') }));
jest.mock('expo-location', () => ({
  Accuracy: { Balanced: 3 },
  requestForegroundPermissionsAsync: jest.fn(),
  getCurrentPositionAsync: jest.fn(),
}));
jest.mock('../../lib/auth', () => ({
  clearSession: jest.fn(),
  getValidSession: jest.fn(),
}));
jest.mock('../../lib/domicilios', () => ({
  ...jest.requireActual('../../lib/domicilios'),
  aceptarDomicilio: jest.fn(),
  listarDomiciliosDisponibles: jest.fn(),
  listarDomiciliosEnCurso: jest.fn(),
  obtenerDomicilioDomiciliario: jest.fn(),
  rechazarDomicilio: jest.fn(),
}));

const courier = {
  accessToken: 'access',
  refreshToken: 'refresh',
  rol: 'DOMICILIARIO',
  expiresIn: 900,
  expiresAt: Date.now() + 900000,
  tokenType: 'Bearer',
};
const origen = { latitud: 11.2408, longitud: -74.211, direccion: 'Carrera 5 # 10-20', referencia: null, aproximado: false };
const destinoAproximado = {
  latitud: 11.25, longitud: -74.2, direccion: null, referencia: null, barrio: 'Pescaíto', aproximado: true,
};
const available: DomicilioDomiciliarioDetalle = {
  id: 'delivery-id',
  estado: 'DISPONIBLE',
  ganancia: 4500,
  asignadoAMi: false,
  rutaDisponible: false,
  distanciaMetros: 1850,
  origen,
  destino: destinoAproximado,
  receptor: { nombre: 'Ana Pérez', celular: null },
  resumenItems: { cantidadItems: 3 },
  asignadoAt: null,
};

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(getValidSession).mockResolvedValue(courier);
  jest.mocked(Location.requestForegroundPermissionsAsync).mockResolvedValue({ status: 'granted' } as never);
  jest.mocked(Location.getCurrentPositionAsync).mockResolvedValue(
    { coords: { latitude: 11.24, longitude: -74.21 } } as never,
  );
  jest.mocked(obtenerDomicilioDomiciliario).mockResolvedValue(available);
  jest.mocked(listarDomiciliosDisponibles).mockResolvedValue({
    content: [{
      id: 'delivery-id',
      ganancia: 4500,
      distanciaMetros: 1850,
      distanciaAlOrigenMetros: null,
      createdAt: '2026-11-13T12:00:00Z',
      origen,
      destino: destinoAproximado,
    }],
    page: 0,
    size: 20,
    totalElements: 1,
    radioMetros: 5000,
  });
});

it('lists available deliveries with gain and distance, and opens one', async () => {
  const view = await render(<DomiciliosDisponiblesScreen />);

  await waitFor(() => expect(view.getByText('$4.500 COP')).toBeTruthy());
  expect(view.getByText(/1,9 km/)).toBeTruthy();
  expect(view.queryByText(/Calle 15/)).toBeNull();
  expect(view.getByText(/barrio Pescaíto/)).toBeTruthy();
  expect(view.getByText('Domicilios que se recogen a 5 km o menos de ti.')).toBeTruthy();
  expect(listarDomiciliosDisponibles).toHaveBeenCalledWith({
    orden: 'recientes',
    posicion: { lat: 11.24, lng: -74.21 },
    page: 0,
    size: 20,
  });
  await fireEvent.press(view.getByRole('button', { name: 'Ver domicilio con ganancia de $4.500 COP' }));
  expect(router.push).toHaveBeenCalledWith({
    pathname: '/domicilio-domiciliario',
    params: { domicilioId: 'delivery-id' },
  });
});

it('sorts by proximity using the courier location', async () => {
  const view = await render(<DomiciliosDisponiblesScreen />);

  await waitFor(() => expect(view.getByText('$4.500 COP')).toBeTruthy());
  await fireEvent.press(view.getByRole('radio', { name: 'Más cercanos' }));

  await waitFor(() => expect(listarDomiciliosDisponibles).toHaveBeenLastCalledWith({
    orden: 'cercania',
    posicion: { lat: 11.24, lng: -74.21 },
    page: 0,
    size: 20,
  }));
});

it('shows an empty message when there are no deliveries', async () => {
  jest.mocked(listarDomiciliosDisponibles).mockResolvedValue({
    content: [], page: 0, size: 20, totalElements: 0, radioMetros: 5000,
  });
  const view = await render(<DomiciliosDisponiblesScreen />);

  await waitFor(() => expect(view.getByText('No hay domicilios disponibles cerca de ti en este momento.')).toBeTruthy());
});

it('does not list deliveries without the courier location', async () => {
  jest.mocked(Location.requestForegroundPermissionsAsync).mockResolvedValue({ status: 'denied' } as never);
  const view = await render(<DomiciliosDisponiblesScreen />);

  await waitFor(() => expect(
    view.getByText('Activa el permiso de ubicación para ver los domicilios cerca de ti.'),
  ).toBeTruthy());
  expect(listarDomiciliosDisponibles).not.toHaveBeenCalled();
  expect(view.getByRole('button', { name: 'Usar mi ubicación' })).toBeTruthy();
});

it('hides the exact address and phone before accepting, then accepts after confirmation', async () => {
  jest.mocked(aceptarDomicilio).mockResolvedValue({
    id: 'delivery-id', estado: 'ASIGNADO', ganancia: 4500, rutaDisponible: true, updatedAt: '2026-11-13T12:05:00Z',
  });
  jest.mocked(obtenerDomicilioDomiciliario)
    .mockResolvedValueOnce(available)
    .mockResolvedValueOnce({
      ...available,
      estado: 'ASIGNADO',
      asignadoAMi: true,
      rutaDisponible: true,
      destino: { latitud: 11.2501, longitud: -74.2002, direccion: 'Calle 15 # 3-20', referencia: 'Casa azul', aproximado: false },
      receptor: { nombre: 'Ana Pérez', celular: '+573001112233' },
      asignadoAt: '2026-11-13T12:05:00Z',
    });
  const view = await render(<DomicilioDomiciliarioScreen />);

  await waitFor(() => expect(view.getByText('Ana Pérez')).toBeTruthy());
  expect(view.getByText(/Barrio Pescaíto\. Zona aproximada/)).toBeTruthy();
  expect(view.queryByText(/Celular/)).toBeNull();
  expect(view.getByText('Pedido de 3 productos')).toBeTruthy();

  await fireEvent.press(view.getByRole('button', { name: 'Aceptar domicilio' }));
  expect(aceptarDomicilio).not.toHaveBeenCalled();
  await fireEvent.press(view.getByRole('button', { name: 'Confirmar aceptación' }));

  await waitFor(() => expect(aceptarDomicilio).toHaveBeenCalledWith('delivery-id', 'courier-key'));
  await waitFor(() => expect(view.getByText('Calle 15 # 3-20')).toBeTruthy());
  expect(view.getByText('Celular: +573001112233')).toBeTruthy();
  expect(view.queryByRole('button', { name: 'Aceptar domicilio' })).toBeNull();
});

it('explains the limit of three deliveries in progress', async () => {
  jest.mocked(aceptarDomicilio).mockRejectedValue(new ApiError('Máximo', 409, 'MAX_ACTIVE_DELIVERIES'));
  const view = await render(<DomicilioDomiciliarioScreen />);

  await waitFor(() => expect(view.getByRole('button', { name: 'Aceptar domicilio' })).toBeTruthy());
  await fireEvent.press(view.getByRole('button', { name: 'Aceptar domicilio' }));
  await fireEvent.press(view.getByRole('button', { name: 'Confirmar aceptación' }));

  await waitFor(() => expect(view.getByText(
    'Ya tienes 3 domicilios en curso. Entrega uno antes de aceptar otro.',
  )).toBeTruthy());
});

it('rejects definitively after confirmation and goes back to the list', async () => {
  jest.mocked(rechazarDomicilio).mockResolvedValue(undefined);
  const view = await render(<DomicilioDomiciliarioScreen />);

  await waitFor(() => expect(view.getByRole('button', { name: 'Rechazar domicilio' })).toBeTruthy());
  await fireEvent.press(view.getByRole('button', { name: 'Rechazar domicilio' }));
  expect(view.getByText(/no podrás recuperarlo/)).toBeTruthy();
  await fireEvent.press(view.getByRole('button', { name: 'Confirmar rechazo' }));

  await waitFor(() => expect(rechazarDomicilio).toHaveBeenCalledWith('delivery-id', 'courier-key'));
  expect(router.replace).toHaveBeenCalledWith('/domicilios-disponibles');
});

it('tells the courier when the delivery was taken by someone else', async () => {
  jest.mocked(obtenerDomicilioDomiciliario).mockRejectedValue(new ApiError('Ya no', 409, 'NOT_AVAILABLE'));
  const view = await render(<DomicilioDomiciliarioScreen />);

  await waitFor(() => expect(view.getByText('Este domicilio ya no está disponible.')).toBeTruthy());
  expect(view.queryByRole('button', { name: 'Aceptar domicilio' })).toBeNull();
});

it('lists the deliveries in progress with the exact destination', async () => {
  jest.mocked(listarDomiciliosEnCurso).mockResolvedValue([{
    id: 'delivery-id',
    estado: 'ASIGNADO',
    ganancia: 4500,
    asignadoAt: '2026-11-13T12:05:00Z',
    origen,
    destino: { latitud: 11.2501, longitud: -74.2002, direccion: 'Calle 15 # 3-20', referencia: null, aproximado: false },
  }]);
  const view = await render(<DomiciliosEnCursoScreen />);

  await waitFor(() => expect(view.getByText('Entrega en: Calle 15 # 3-20')).toBeTruthy());
  expect(view.getByText('1 de 3 domicilios en curso.')).toBeTruthy();
});
