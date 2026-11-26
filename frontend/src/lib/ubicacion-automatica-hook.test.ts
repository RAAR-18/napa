import { renderHook, waitFor } from '@testing-library/react-native';
import { AppState } from 'react-native';
import * as Location from 'expo-location';
import { useUbicacionAutomatica } from './ubicacion-automatica';
import { leerUbicacionAutomatica } from './ubicacion-preferencia';

const mockRemove = jest.fn();
jest.mock('./ubicacion-segundo-plano', () => ({
  solicitarPermisoSegundoPlano: jest.fn(), iniciarSegundoPlano: jest.fn(), detenerSegundoPlano: jest.fn(async () => undefined),
  reconciliarSegundoPlano: jest.fn(async () => undefined),
}));
jest.mock('./ubicacion-publicacion', () => ({ actualizarZonaAutomatica: jest.fn(async () => undefined), UMBRAL_PUBLICACION_METROS: 50 }));
jest.mock('./auth-storage', () => ({ getItemAsync: jest.fn(), setItemAsync: jest.fn(), deleteItemAsync: jest.fn() }));
jest.mock('./auth', () => ({ readSession: jest.fn(), getSessionGeneration: () => 0 }));
jest.mock('./ubicacion-preferencia', () => ({
  UBICACION_KEY: 'napa.ubicacion-automatica', oyentesUbicacion: new Set(), mismaPreferencia: jest.fn(),
  leerUbicacionAutomatica: jest.fn(), desactivarSiVencida: jest.fn(async () => false), suscribirUbicacionAutomatica: jest.fn(),
}));
jest.mock('expo-location', () => ({
  Accuracy: { Balanced: 3 }, getForegroundPermissionsAsync: jest.fn(async () => ({ status: 'granted' })),
  watchPositionAsync: jest.fn(async () => ({ remove: mockRemove })),
}));

let estadoApp: (estado: string) => void = () => undefined;
beforeEach(() => {
  jest.clearAllMocks();
  Object.defineProperty(AppState, 'currentState', { value: 'active', configurable: true });
  jest.spyOn(AppState, 'addEventListener').mockImplementation((_tipo, oyente) => {
    estadoApp = oyente as (estado: string) => void;
    return { remove: jest.fn() } as never;
  });
});

it('watches movement with a distance filter instead of polling the GPS', async () => {
  jest.mocked(leerUbicacionAutomatica).mockResolvedValue({ usuarioId: 'A', emprendimientoId: 'negocio' });
  const { unmount } = await renderHook(() => useUbicacionAutomatica());
  await waitFor(() => expect(Location.watchPositionAsync).toHaveBeenCalledTimes(1));
  expect(jest.mocked(Location.watchPositionAsync).mock.calls[0][0]).toEqual({
    accuracy: 3, distanceInterval: 50, timeInterval: 60_000,
  });
  Object.defineProperty(AppState, 'currentState', { value: 'background', configurable: true });
  estadoApp('background');
  expect(mockRemove).toHaveBeenCalledTimes(1);
  await unmount();
});

it('does not watch the GPS without an active foreground preference', async () => {
  jest.mocked(leerUbicacionAutomatica).mockResolvedValue({ usuarioId: 'A', emprendimientoId: 'negocio', segundoPlano: true });
  await renderHook(() => useUbicacionAutomatica());
  await waitFor(() => expect(leerUbicacionAutomatica).toHaveBeenCalled());
  await new Promise((resolver) => setTimeout(resolver, 0));
  expect(Location.watchPositionAsync).not.toHaveBeenCalled();
});
