import * as Location from 'expo-location';
import * as TaskManager from 'expo-task-manager';
import { Platform } from 'react-native';
import { ApiError } from './api';
import { readSession } from './auth';
import { leerUbicacionAutomatica, mismaPreferencia, deshabilitarSegundoPlano, desactivarSiVencida, registrarParadaUbicacion, type Preferencia } from './ubicacion-preferencia';
import { actualizarZonaAutomatica, UMBRAL_PUBLICACION_METROS } from './ubicacion-publicacion';

export const TAREA_UBICACION = 'napa-ubicacion-ambulante-v1';
/** El sistema entrega posiciones agrupadas como máximo cada minuto: menos despertares de la radio y del GPS. */
export const INTERVALO_SEGUNDO_PLANO_MS = 60_000;
let recibiendo = false;

/** Se define al importar el módulo, antes de montar React o navegar. */
if (!TaskManager.isTaskDefined(TAREA_UBICACION)) {
  TaskManager.defineTask<{ locations: Location.LocationObject[] }>(TAREA_UBICACION, async ({ data, error }) => {
    if (error || !data?.locations?.length || recibiendo) return;
    recibiendo = true;
    let preferencia: Preferencia | null = null;
    try {
      preferencia = await leerUbicacionAutomatica();
      const sesion = await readSession();
      if (!preferencia?.segundoPlano || sesion?.usuarioId !== preferencia.usuarioId || sesion.rol !== 'VENDEDOR_AMBULANTE') {
        await detenerSegundoPlano();
        return;
      }
      if (await desactivarSiVencida(preferencia)) {
        await detenerSegundoPlano();
        return;
      }
      if ((await Location.getBackgroundPermissionsAsync()).status !== 'granted') {
        await deshabilitarSegundoPlano(preferencia);
        await detenerSegundoPlano();
        return;
      }
      const punto = data.locations.reduce((a, b) => a.timestamp > b.timestamp ? a : b);
      const antiguedad = Date.now() - punto.timestamp;
      if (!Number.isFinite(antiguedad) || antiguedad > 120_000 || antiguedad < -30_000
        || punto.timestamp < (preferencia.iniciadaAt ?? 0)) return;
      await actualizarZonaAutomatica(() => true, punto);
    } catch (cause) {
      if (cause instanceof ApiError && [401, 403, 404].includes(cause.status) && preferencia) {
        await deshabilitarSegundoPlano(preferencia).catch(() => undefined);
        await detenerSegundoPlano().catch(() => undefined);
      }
      // Sin conexión no se guardan ni reenvían recorridos antiguos.
    }
    finally { recibiendo = false; }
  });
}

export async function solicitarPermisoSegundoPlano(): Promise<void> {
  if (Platform.OS === 'web' || !(await TaskManager.isAvailableAsync())) {
    throw new Error('El seguimiento en segundo plano necesita el nuevo build de Ñapa; no funciona en Expo Go ni en web.');
  }
  const permiso = await Location.requestBackgroundPermissionsAsync();
  if (permiso.status !== 'granted') throw new Error('Activa el permiso de ubicación «Siempre» para compartir cuando no usas Ñapa.');
}

export async function iniciarSegundoPlano(): Promise<void> {
  if (!(await TaskManager.isAvailableAsync())) throw new Error('Instala el nuevo build para usar ubicación en segundo plano.');
  if (await Location.hasStartedLocationUpdatesAsync(TAREA_UBICACION)) return;
  const preferencia = await leerUbicacionAutomatica();
  const sesion = await readSession();
  if (!preferencia?.segundoPlano || sesion?.usuarioId !== preferencia.usuarioId || sesion.rol !== 'VENDEDOR_AMBULANTE') {
    throw new Error('Se canceló la activación de ubicación.');
  }
  await Location.startLocationUpdatesAsync(TAREA_UBICACION, {
    // Balanced (~100 m) evita el GPS fino; la zona del negocio no necesita más precisión.
    accuracy: Location.Accuracy.Balanced,
    distanceInterval: UMBRAL_PUBLICACION_METROS,
    timeInterval: INTERVALO_SEGUNDO_PLANO_MS,
    deferredUpdatesDistance: UMBRAL_PUBLICACION_METROS,
    deferredUpdatesInterval: INTERVALO_SEGUNDO_PLANO_MS,
    activityType: Location.ActivityType.Fitness,
    pausesUpdatesAutomatically: false,
    showsBackgroundLocationIndicator: true,
    foregroundService: {
      notificationTitle: 'Ñapa está compartiendo tu ubicación',
      notificationBody: 'Tu negocio móvil se actualiza. Puedes detenerlo en Mi emprendimiento.',
      notificationColor: '#205C46',
      killServiceOnDestroy: false,
    },
  });
}

export async function detenerSegundoPlano(): Promise<void> {
  if (!(await TaskManager.isAvailableAsync())) return;
  if (await Location.hasStartedLocationUpdatesAsync(TAREA_UBICACION)) {
    await Location.stopLocationUpdatesAsync(TAREA_UBICACION);
  }
}

/** Reanuda una preferencia vigente; nunca vuelve a pedir permisos automáticamente. */
export async function reconciliarSegundoPlano(): Promise<void> {
  const preferencia = await leerUbicacionAutomatica();
  const sesion = await readSession();
  if (!preferencia?.segundoPlano || sesion?.usuarioId !== preferencia.usuarioId || sesion.rol !== 'VENDEDOR_AMBULANTE') {
    await detenerSegundoPlano();
    return;
  }
  if (await desactivarSiVencida(preferencia)) {
    await detenerSegundoPlano();
    return;
  }
  if ((await Location.getBackgroundPermissionsAsync()).status !== 'granted') {
    await deshabilitarSegundoPlano(preferencia);
    await detenerSegundoPlano();
    return;
  }
  if (!mismaPreferencia(await leerUbicacionAutomatica(), preferencia)) return;
  await iniciarSegundoPlano();
  if (!mismaPreferencia(await leerUbicacionAutomatica(), preferencia)) await detenerSegundoPlano();
}

registrarParadaUbicacion(detenerSegundoPlano);
