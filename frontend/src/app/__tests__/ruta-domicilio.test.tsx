import * as Location from 'expo-location';
import { act, render, waitFor } from '@testing-library/react-native';

import RutaDomicilioScreen from '../ruta-domicilio';
import { ApiError } from '../../lib/api';
import { readSession, clearSession } from '../../lib/auth';
import { router } from 'expo-router';

import { decodificarPolilinea, obtenerRutaDomicilio, type RutaDomicilio } from '../../lib/domicilios';
import { borrarRuta, guardarRuta, leerRuta } from '../../lib/ruta-cache';

let mockGeneracion = 1;

jest.mock('expo-router', () => ({
  useFocusEffect: (effect: () => void | (() => void)) => {
    const React = jest.requireActual<typeof import('react')>('react');
    React.useEffect(effect, [effect]);
  },
  router: { push: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: () => ({ domicilioId: 'delivery-id' }),
}));
jest.mock('expo-location', () => ({
  Accuracy: { Balanced: 3 },
  getCurrentPositionAsync: jest.fn(),
  requestForegroundPermissionsAsync: jest.fn(),
}));
jest.mock('react-native-maps', () => {
  const { Text, View } = jest.requireActual('react-native');
  const MapView = ({ children, accessibilityLabel }: { children: unknown; accessibilityLabel: string }) => (
    <View accessibilityLabel={accessibilityLabel}>{children as never}</View>
  );
  const Marker = ({ title }: { title: string }) => <Text>{`marcador ${title}`}</Text>;
  const Polyline = ({ coordinates, lineDashPattern }: { coordinates: unknown[]; lineDashPattern?: number[] }) => (
    <Text>{`línea de ${coordinates.length} puntos${lineDashPattern ? ' punteada' : ''}`}</Text>
  );
  return { __esModule: true, default: MapView, Marker, Polyline };
});
jest.mock('../../lib/auth', () => ({ clearSession: jest.fn(), getValidSession: jest.fn(),
  readSession: jest.fn(), getSessionGeneration: () => mockGeneracion }));
jest.mock('../../lib/domicilios', () => ({
  ...jest.requireActual('../../lib/domicilios'),
  obtenerRutaDomicilio: jest.fn(),
}));
jest.mock('../../lib/ruta-cache', () => ({
  borrarRuta: jest.fn(() => Promise.resolve()),
  guardarRuta: jest.fn(() => Promise.resolve()),
  leerRuta: jest.fn(() => Promise.resolve(null)),
}));

const ruta: RutaDomicilio = {
  domicilioId: 'delivery-id',
  estado: 'ASIGNADO',
  origen: {
    latitud: 11.2408, longitud: -74.211, direccion: 'Carrera 5 # 10-20', referencia: null, aproximado: false,
  },
  destino: {
    latitud: 11.2501, longitud: -74.2002, direccion: 'Calle 15 # 3-20', referencia: 'Casa azul',
    barrio: 'Pescaíto', aproximado: false,
  },
  tramos: [
    // "_p~iF~ps|U_ulLnnqC" es el ejemplo de Google: 3 puntos.
    { tipo: 'HACIA_ORIGEN', distanciaMetros: 620, duracionSegundos: 140, polilinea: '_p~iF~ps|U_ulLnnqC_mqNvxq`@', aproximado: false },
    { tipo: 'HACIA_DESTINO', distanciaMetros: 2100, duracionSegundos: null, polilinea: null, aproximado: true },
  ],
};

beforeEach(() => {
  jest.clearAllMocks();
  mockGeneracion = 1;
  jest.mocked(readSession).mockResolvedValue({ usuarioId: 'cuenta-A', rol: 'DOMICILIARIO',
    accessToken: 'test', refreshToken: 'test', tokenType: 'Bearer', expiresIn: 900, expiresAt: Date.now() + 900000 });
  jest.mocked(Location.requestForegroundPermissionsAsync).mockResolvedValue({ status: 'granted' } as never);
  jest.mocked(Location.getCurrentPositionAsync).mockResolvedValue(
    { coords: { latitude: 11.235, longitude: -74.215 } } as never,
  );
});

it('decodes an encoded polyline', () => {
  expect(decodificarPolilinea('_p~iF~ps|U_ulLnnqC_mqNvxq`@')).toEqual([
    { latitude: 38.5, longitude: -120.2 },
    { latitude: 40.7, longitude: -120.95 },
    { latitude: 43.252, longitude: -126.453 },
  ]);
  expect(() => decodificarPolilinea('_p~iF~ps|U_')).toThrow();
});

it('shows the map with the courier, both points, the route and the addresses', async () => {
  jest.mocked(obtenerRutaDomicilio).mockResolvedValue(ruta);
  const view = await render(<RutaDomicilioScreen />);

  await waitFor(() => expect(view.getByLabelText('Mapa de la ruta del domicilio')).toBeTruthy());
  expect(obtenerRutaDomicilio).toHaveBeenCalledWith('delivery-id', { lat: 11.235, lng: -74.215 });
  expect(view.getByText('marcador Recogida (A)')).toBeTruthy();
  expect(view.getByText('marcador Entrega (B)')).toBeTruthy();
  expect(view.getByText('línea de 3 puntos')).toBeTruthy();
  expect(view.getByText('línea de 2 puntos punteada')).toBeTruthy();
  expect(view.getByText(/Hacia la recogida \(A\): 620 m · 2 min/)).toBeTruthy();
  expect(view.getByText(/Hacia la entrega \(B\): 2,1 km \(aproximado, en línea recta\)/)).toBeTruthy();
  expect(view.getByText('Calle 15 # 3-20')).toBeTruthy();
  expect(view.getByText('Referencia: Casa azul')).toBeTruthy();
  expect(guardarRuta).toHaveBeenCalledWith(ruta, 'cuenta-A');
});

it('works without location permission, with points and addresses', async () => {
  jest.mocked(Location.requestForegroundPermissionsAsync).mockResolvedValue({ status: 'denied' } as never);
  jest.mocked(obtenerRutaDomicilio).mockResolvedValue({ ...ruta, tramos: [ruta.tramos[1]] });
  const view = await render(<RutaDomicilioScreen />);

  await waitFor(() => expect(view.getByText(/Sin permiso de ubicación/)).toBeTruthy());
  expect(obtenerRutaDomicilio).toHaveBeenCalledWith('delivery-id', null);
  expect(view.getByText('marcador Entrega (B)')).toBeTruthy();
  expect(view.getByText('Carrera 5 # 10-20')).toBeTruthy();
});

it('shows the saved route when offline', async () => {
  jest.mocked(obtenerRutaDomicilio).mockRejectedValue(new Error('No se pudo conectar con la API'));
  jest.mocked(leerRuta).mockResolvedValue({ ruta, updatedAt: '2026-11-13T15:04:00Z' });
  const view = await render(<RutaDomicilioScreen />);

  await waitFor(() => expect(view.getByText(/Sin conexión. Mostramos la ruta guardada/)).toBeTruthy());
  expect(view.getByText('Calle 15 # 3-20')).toBeTruthy();
});

it('explains when the delivery already finished and forgets the saved route', async () => {
  jest.mocked(obtenerRutaDomicilio).mockRejectedValue(new ApiError('Terminado', 409, 'INVALID_STATE'));
  const view = await render(<RutaDomicilioScreen />);

  await waitFor(() => expect(view.getByText('Este domicilio ya terminó; no hay ruta que seguir.')).toBeTruthy());
  expect(borrarRuta).toHaveBeenCalledWith('delivery-id', 'cuenta-A');
  expect(view.queryByLabelText('Mapa de la ruta del domicilio')).toBeNull();
});

it('denies the route of another courier', async () => {
  jest.mocked(obtenerRutaDomicilio).mockRejectedValue(new ApiError('Prohibido', 403, 'FORBIDDEN'));
  const view = await render(<RutaDomicilioScreen />);

  await waitFor(() => expect(view.getByText('No tienes acceso a la ruta de este domicilio.')).toBeTruthy());
});

it('does not request location or route without an identified courier account', async () => {
  jest.mocked(readSession).mockResolvedValue(null);
  const view = await render(<RutaDomicilioScreen />);
  await waitFor(() => expect(view.getByText(/Inicia sesión con la cuenta de domiciliario asignada/)).toBeTruthy());
  expect(Location.requestForegroundPermissionsAsync).not.toHaveBeenCalled();
  expect(obtenerRutaDomicilio).not.toHaveBeenCalled();
  expect(leerRuta).not.toHaveBeenCalled();
});
it('discards a response that finishes after the session changes', async () => {
  let resolver!: (value: RutaDomicilio) => void;
  jest.mocked(obtenerRutaDomicilio).mockReturnValueOnce(new Promise((resolve) => { resolver = resolve; }));
  const view = await render(<RutaDomicilioScreen />);
  await waitFor(() => expect(obtenerRutaDomicilio).toHaveBeenCalledTimes(1));
  mockGeneracion += 1;
  await act(async () => { resolver(ruta); });
  expect(view.queryByText('Calle 15 # 3-20')).toBeNull();
  expect(guardarRuta).not.toHaveBeenCalled();
});
it('does not read another account cache after an offline response', async () => {
  let rechazar!: (error: Error) => void;
  jest.mocked(obtenerRutaDomicilio).mockReturnValueOnce(new Promise((_resolve, reject) => { rechazar = reject; }));
  const view = await render(<RutaDomicilioScreen />);
  await waitFor(() => expect(obtenerRutaDomicilio).toHaveBeenCalledTimes(1));
  jest.mocked(readSession).mockResolvedValue({ usuarioId: 'cuenta-B', rol: 'DOMICILIARIO',
    accessToken: 'test', refreshToken: 'test', tokenType: 'Bearer', expiresIn: 900, expiresAt: Date.now() + 900000 });
  await act(async () => { rechazar(new Error('Sin conexión')); });
  expect(leerRuta).not.toHaveBeenCalled();
  expect(view.queryByText('Calle 15 # 3-20')).toBeNull();
});
it('ignores an old unauthorized response after leaving the route', async () => {
  let rechazar!: (error: Error) => void;
  jest.mocked(obtenerRutaDomicilio).mockReturnValueOnce(new Promise((_resolve, reject) => { rechazar = reject; }));
  const view = await render(<RutaDomicilioScreen />);
  await waitFor(() => expect(obtenerRutaDomicilio).toHaveBeenCalledTimes(1));
  await view.unmount();
  await act(async () => { rechazar(new ApiError('Sesión anterior', 401, 'UNAUTHORIZED')); });
  expect(clearSession).not.toHaveBeenCalled();
  expect(router.replace).not.toHaveBeenCalled();
});
