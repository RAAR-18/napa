import * as Location from 'expo-location';
import * as TaskManager from 'expo-task-manager';
import { iniciarSegundoPlano, detenerSegundoPlano, solicitarPermisoSegundoPlano, reconciliarSegundoPlano } from './ubicacion-segundo-plano';
import { leerUbicacionAutomatica, deshabilitarSegundoPlano } from './ubicacion-preferencia';
import { actualizarZonaAutomatica } from './ubicacion-publicacion';
import { ApiError } from './api';
import { readSession } from './auth';

jest.mock('expo-task-manager', () => ({ isTaskDefined: jest.fn(() => false), defineTask: jest.fn(), isAvailableAsync: jest.fn(async () => true) }));
jest.mock('expo-location', () => ({ Accuracy: { Balanced: 3 }, ActivityType: { Fitness: 3 },
  requestBackgroundPermissionsAsync: jest.fn(), getBackgroundPermissionsAsync: jest.fn(),
  hasStartedLocationUpdatesAsync: jest.fn(), startLocationUpdatesAsync: jest.fn(async () => undefined),
  stopLocationUpdatesAsync: jest.fn(async () => undefined),
}));
jest.mock('./auth', () => ({ readSession: jest.fn() }));
jest.mock('./ubicacion-publicacion', () => ({ actualizarZonaAutomatica: jest.fn(async () => undefined), UMBRAL_PUBLICACION_METROS: 50 }));
jest.mock('./ubicacion-preferencia', () => ({ registrarParadaUbicacion: jest.fn(), leerUbicacionAutomatica: jest.fn(),
  deshabilitarSegundoPlano: jest.fn(async () => undefined),
  desactivarSiVencida: jest.fn(async (p: { iniciadaAt?: number }) => Date.now() - (p.iniciadaAt ?? Date.now()) > 12 * 60 * 60_000),
  mismaPreferencia: (a: { activacionId: string } | null, b: { activacionId: string }) => a?.activacionId === b.activacionId,
}));
const ejecutar = jest.mocked(TaskManager.defineTask).mock.calls[0][1];
const pref = { usuarioId: 'A', emprendimientoId: 'negocio', segundoPlano: true, activacionId: 'activacion', iniciadaAt: Date.now() - 1000 };
function punto(timestamp = Date.now()): Location.LocationObject {
  return { timestamp, coords: { latitude: 11.25, longitude: -74.20, accuracy: 10, altitude: null, altitudeAccuracy: null, heading: null, speed: null } };
}
beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(TaskManager.isAvailableAsync).mockResolvedValue(true);
  jest.mocked(readSession).mockResolvedValue({ usuarioId: 'A', rol: 'VENDEDOR_AMBULANTE' } as never);
  jest.mocked(leerUbicacionAutomatica).mockResolvedValue(pref);
  jest.mocked(Location.getBackgroundPermissionsAsync).mockResolvedValue({ status: 'granted' } as never);
  jest.mocked(Location.requestBackgroundPermissionsAsync).mockResolvedValue({ status: 'granted' } as never);
  jest.mocked(Location.hasStartedLocationUpdatesAsync).mockResolvedValue(true);
});
async function recibir(locations: Location.LocationObject[]) {
  await ejecutar({ data: { locations }, error: null, executionInfo: { eventId: 'evento', taskName: 'tarea' } });
}
it('publishes only the newest fresh point from a background batch', async () => {
  const nuevo = punto();
  await recibir([punto(Date.now() - 10000), nuevo]);
  expect(actualizarZonaAutomatica).toHaveBeenCalledWith(expect.any(Function), nuevo);
});
it('does not publish old points or points predating consent', async () => {
  await recibir([punto(Date.now() - 180000)]);
  expect(actualizarZonaAutomatica).not.toHaveBeenCalled();
});
it('stops after pausing and after switching accounts', async () => {
  jest.mocked(leerUbicacionAutomatica).mockResolvedValue(null);
  await recibir([punto()]);
  expect(Location.stopLocationUpdatesAsync).toHaveBeenCalledTimes(1);
  jest.mocked(leerUbicacionAutomatica).mockResolvedValue(pref);
  jest.mocked(readSession).mockResolvedValue({ usuarioId: 'B', rol: 'VENDEDOR_AMBULANTE' } as never);
  await recibir([punto()]);
  expect(actualizarZonaAutomatica).not.toHaveBeenCalled();
});
it('revokes background consent when the system permission is removed', async () => {
  jest.mocked(Location.getBackgroundPermissionsAsync).mockResolvedValue({ status: 'denied' } as never);
  await reconciliarSegundoPlano();
  expect(deshabilitarSegundoPlano).toHaveBeenCalledWith(pref);
  expect(Location.stopLocationUpdatesAsync).toHaveBeenCalled();
  expect(Location.requestBackgroundPermissionsAsync).not.toHaveBeenCalled();
});
it('starts a visible foreground service and can stop it', async () => {
  jest.mocked(Location.hasStartedLocationUpdatesAsync).mockResolvedValue(false);
  await iniciarSegundoPlano();
  expect(Location.startLocationUpdatesAsync).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({
    showsBackgroundLocationIndicator: true,
    foregroundService: expect.objectContaining({ notificationTitle: 'Ñapa está compartiendo tu ubicación' }),
  }));
  jest.mocked(Location.hasStartedLocationUpdatesAsync).mockResolvedValue(true);
  await detenerSegundoPlano();
  expect(Location.stopLocationUpdatesAsync).toHaveBeenCalled();
});
it('reports unsupported builds before requesting background permission', async () => {
  jest.mocked(TaskManager.isAvailableAsync).mockResolvedValue(false);
  await expect(solicitarPermisoSegundoPlano()).rejects.toThrow('nuevo build');
  expect(Location.requestBackgroundPermissionsAsync).not.toHaveBeenCalled();
});

it('stops background if the server no longer permits access to the business', async () => {
  jest.mocked(actualizarZonaAutomatica).mockRejectedValueOnce(new ApiError('No disponible', 404, 'NOT_FOUND'));
  await recibir([punto()]);
  expect(deshabilitarSegundoPlano).toHaveBeenCalledWith(pref);
  expect(Location.stopLocationUpdatesAsync).toHaveBeenCalled();
});

it('stops the native task once the activation exceeded the maximum duration', async () => {
  jest.mocked(leerUbicacionAutomatica).mockResolvedValue({ ...pref, iniciadaAt: Date.now() - 13 * 60 * 60_000 });
  await recibir([punto()]);
  expect(actualizarZonaAutomatica).not.toHaveBeenCalled();
  expect(Location.stopLocationUpdatesAsync).toHaveBeenCalled();
});

it('starts the native task with batched, low-power options', async () => {
  jest.mocked(Location.hasStartedLocationUpdatesAsync).mockResolvedValue(false);
  await iniciarSegundoPlano();
  expect(Location.startLocationUpdatesAsync).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({
    accuracy: 3, distanceInterval: 50, timeInterval: 60_000, deferredUpdatesDistance: 50, deferredUpdatesInterval: 60_000,
  }));
});
