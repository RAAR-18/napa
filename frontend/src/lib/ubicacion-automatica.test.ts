import * as Location from 'expo-location';
import { configurarUbicacionAutomatica, actualizarZonaAutomatica } from './ubicacion-automatica';
import { readSession } from './auth';
import { editarMiEmprendimiento, obtenerMiEmprendimiento } from './emprendimientos';

const mockDatos = new Map<string, string>();
jest.mock('./auth-storage', () => ({
  getItemAsync: jest.fn(async (key: string) => mockDatos.get(key) ?? null),
  setItemAsync: jest.fn(async (key: string, value: string) => { mockDatos.set(key, value); }),
  deleteItemAsync: jest.fn(async (key: string) => { mockDatos.delete(key); }),
}));
jest.mock('./auth', () => ({ readSession: jest.fn(), getSessionGeneration: () => 0 }));
jest.mock('./emprendimientos', () => ({ obtenerMiEmprendimiento: jest.fn(), editarMiEmprendimiento: jest.fn() }));
jest.mock('expo-location', () => ({
  Accuracy: { Balanced: 3 }, requestForegroundPermissionsAsync: jest.fn(),
  getForegroundPermissionsAsync: jest.fn(), getCurrentPositionAsync: jest.fn(),
}));

beforeEach(() => {
  jest.clearAllMocks();
  mockDatos.clear();
  jest.mocked(readSession).mockResolvedValue({ usuarioId: 'A', rol: 'VENDEDOR_AMBULANTE' } as never);
  jest.mocked(Location.requestForegroundPermissionsAsync).mockResolvedValue({ status: 'granted' } as never);
  jest.mocked(Location.getForegroundPermissionsAsync).mockResolvedValue({ status: 'granted' } as never);
  jest.mocked(Location.getCurrentPositionAsync).mockResolvedValue({ coords: { latitude: 11.25, longitude: -74.20 } } as never);
  jest.mocked(obtenerMiEmprendimiento).mockResolvedValue({ id: 'negocio',
    ubicacion: { latitud: 11.24, longitud: -74.21, direccion: null, referencia: 'Frente al parque' } } as never);
});

it('does not read GPS without explicit activation or while the app is inactive', async () => {
  await actualizarZonaAutomatica();
  expect(Location.getCurrentPositionAsync).not.toHaveBeenCalled();
  await configurarUbicacionAutomatica('A', 'negocio', true);
  await actualizarZonaAutomatica(() => false);
  expect(Location.getCurrentPositionAsync).not.toHaveBeenCalled();
});

it('updates only the expected account and preserves the zone reference', async () => {
  await configurarUbicacionAutomatica('A', 'negocio', true);
  await actualizarZonaAutomatica();
  expect(editarMiEmprendimiento).toHaveBeenCalledWith({ ubicacion: {
    latitud: 11.25, longitud: -74.20, direccion: null, referencia: 'Frente al parque',
  } }, 'A');
  jest.mocked(readSession).mockResolvedValue({ usuarioId: 'B', rol: 'VENDEDOR_AMBULANTE' } as never);
  jest.mocked(editarMiEmprendimiento).mockClear();
  await actualizarZonaAutomatica();
  expect(editarMiEmprendimiento).not.toHaveBeenCalled();
});

it('stops after disabling and rejects activation for fixed vendors', async () => {
  await configurarUbicacionAutomatica('A', 'negocio', true);
  await configurarUbicacionAutomatica('A', 'negocio', false);
  await actualizarZonaAutomatica();
  expect(editarMiEmprendimiento).not.toHaveBeenCalled();
  jest.mocked(readSession).mockResolvedValue({ usuarioId: 'A', rol: 'VENDEDOR_PUNTO_FIJO' } as never);
  await expect(configurarUbicacionAutomatica('A', 'negocio', true)).rejects.toThrow('vendedor ambulante');
});
