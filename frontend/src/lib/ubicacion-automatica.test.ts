import * as Location from 'expo-location';
import { configurarUbicacionAutomatica, actualizarZonaAutomatica } from './ubicacion-automatica';
import { solicitarPermisoSegundoPlano, iniciarSegundoPlano, detenerSegundoPlano } from './ubicacion-segundo-plano';
import { readSession } from './auth';
import { editarMiEmprendimiento, obtenerMiEmprendimiento } from './emprendimientos';

const mockDatos = new Map<string, string>();
jest.mock('./ubicacion-segundo-plano', () => ({
  solicitarPermisoSegundoPlano: jest.fn(async () => undefined), iniciarSegundoPlano: jest.fn(async () => undefined),
  detenerSegundoPlano: jest.fn(async () => undefined), reconciliarSegundoPlano: jest.fn(async () => undefined),
}));
jest.mock('./auth-storage', () => ({
  prepararSesionSegundoPlano: jest.fn(async () => undefined),
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

it('updates only the expected account and clears fixed address data', async () => {
  await configurarUbicacionAutomatica('A', 'negocio', true);
  await actualizarZonaAutomatica();
  expect(editarMiEmprendimiento).toHaveBeenCalledWith({ ubicacion: {
    latitud: 11.25, longitud: -74.20, direccion: null, referencia: null,
  } }, 'A', expect.any(Function));
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

it('starts background only with explicit consent and clears preference before stopping', async () => {
  await configurarUbicacionAutomatica('A', 'negocio', true);
  expect(solicitarPermisoSegundoPlano).not.toHaveBeenCalled();
  await configurarUbicacionAutomatica('A', 'negocio', true, true);
  expect(solicitarPermisoSegundoPlano).toHaveBeenCalledTimes(1);
  expect(iniciarSegundoPlano).toHaveBeenCalledTimes(1);
  jest.mocked(detenerSegundoPlano).mockImplementationOnce(async () => {
    expect(mockDatos.get('napa.ubicacion-automatica')).toBeUndefined();
  });
  await configurarUbicacionAutomatica('A', 'negocio', false);
});
it('preserves foreground mode if background permission is denied', async () => {
  await configurarUbicacionAutomatica('A', 'negocio', true);
  const anterior = mockDatos.get('napa.ubicacion-automatica');
  jest.mocked(solicitarPermisoSegundoPlano).mockRejectedValueOnce(new Error('Permiso denegado'));
  await expect(configurarUbicacionAutomatica('A', 'negocio', true, true)).rejects.toThrow('Permiso denegado');
  expect(mockDatos.get('napa.ubicacion-automatica')).toBe(anterior);
  expect(iniciarSegundoPlano).not.toHaveBeenCalled();
});
it('invalidates the final HTTP guard immediately after deactivation', async () => {
  await configurarUbicacionAutomatica('A', 'negocio', true);
  await actualizarZonaAutomatica();
  const validar = jest.mocked(editarMiEmprendimiento).mock.calls[0][2]!;
  expect(await validar()).toBe(true);
  await configurarUbicacionAutomatica('A', 'negocio', false);
  expect(await validar()).toBe(false);
});
