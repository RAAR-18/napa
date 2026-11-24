import { useEffect } from 'react';
import { AppState } from 'react-native';
import * as Location from 'expo-location';

import * as storage from './auth-storage';
import { getSessionGeneration, readSession } from './auth';
import { editarMiEmprendimiento, obtenerMiEmprendimiento } from './emprendimientos';

const KEY = 'napa.ubicacion-automatica';
export const INTERVALO_UBICACION_MS = 120_000;
type Preferencia = { usuarioId: string; emprendimientoId: string };
const oyentes = new Set<() => void>();

export async function leerUbicacionAutomatica(): Promise<Preferencia | null> {
  const raw = await storage.getItemAsync(KEY);
  if (!raw) return null;
  try {
    const dato = JSON.parse(raw) as Partial<Preferencia> | null;
    return dato && typeof dato.usuarioId === 'string' && typeof dato.emprendimientoId === 'string'
      ? dato as Preferencia : null;
  } catch { return null; }
}

export async function configurarUbicacionAutomatica(
  usuarioId: string, emprendimientoId: string, activar: boolean,
): Promise<void> {
  const generacion = getSessionGeneration();
  const session = await readSession();
  if (session?.usuarioId !== usuarioId || session.rol !== 'VENDEDOR_AMBULANTE') {
    throw new Error('Inicia sesión con la cuenta del vendedor ambulante para continuar.');
  }
  if (activar) {
    const permiso = await Location.requestForegroundPermissionsAsync();
    if (permiso.status !== 'granted') throw new Error('Permite la ubicación para actualizar tu zona automáticamente.');
    if (generacion !== getSessionGeneration()) throw new Error('La sesión cambió. Vuelve a abrir tu emprendimiento.');
    await storage.setItemAsync(KEY, JSON.stringify({ usuarioId, emprendimientoId }));
  } else {
    const actual = await leerUbicacionAutomatica();
    if (actual?.usuarioId === usuarioId && actual.emprendimientoId === emprendimientoId) {
      await storage.deleteItemAsync(KEY);
    }
  }
  oyentes.forEach((oyente) => oyente());
}

function distanciaMetros(a: { latitud: number; longitud: number }, b: { latitude: number; longitude: number }) {
  const rad = Math.PI / 180;
  const lat = (b.latitude - a.latitud) * rad;
  const lng = (b.longitude - a.longitud) * rad;
  const h = Math.sin(lat / 2) ** 2
    + Math.cos(a.latitud * rad) * Math.cos(b.latitude * rad) * Math.sin(lng / 2) ** 2;
  return 6_371_000 * 2 * Math.asin(Math.sqrt(Math.min(1, h)));
}

/** No solicita permisos por sí sola ni acumula posiciones para reenvío. */
export async function actualizarZonaAutomatica(vigente: () => boolean = () => true): Promise<void> {
  const generacion = getSessionGeneration();
  const session = await readSession();
  const preferencia = await leerUbicacionAutomatica();
  if (!vigente() || !session?.usuarioId || session.rol !== 'VENDEDOR_AMBULANTE'
    || preferencia?.usuarioId !== session.usuarioId) return;
  const permiso = await Location.getForegroundPermissionsAsync();
  if (!vigente() || permiso.status !== 'granted' || generacion !== getSessionGeneration()) return;
  const negocio = await obtenerMiEmprendimiento(preferencia.usuarioId);
  if (!vigente() || generacion !== getSessionGeneration() || negocio.id !== preferencia.emprendimientoId) return;
  const antes = await leerUbicacionAutomatica();
  if (antes?.usuarioId !== preferencia.usuarioId || antes.emprendimientoId !== preferencia.emprendimientoId) return;
  const posicion = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
  const { latitude, longitude } = posicion.coords;
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)
    || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return;
  if (!vigente() || generacion !== getSessionGeneration()) return;
  const actual = await leerUbicacionAutomatica();
  if (!vigente() || generacion !== getSessionGeneration() || negocio.id !== preferencia.emprendimientoId
    || actual?.usuarioId !== preferencia.usuarioId || actual.emprendimientoId !== preferencia.emprendimientoId) return;
  if (distanciaMetros(negocio.ubicacion, posicion.coords) < 100) return;
  await editarMiEmprendimiento({ ubicacion: {
    latitud: latitude, longitud: longitude,
    direccion: negocio.ubicacion.direccion, referencia: negocio.ubicacion.referencia,
  } }, preferencia.usuarioId);
}

export function useUbicacionAutomatica() {
  useEffect(() => {
    let montado = true;
    let ejecutando = false;
    const vigente = () => montado && AppState.currentState === 'active';
    async function actualizar() {
      if (!vigente() || ejecutando) return;
      ejecutando = true;
      try { await actualizarZonaAutomatica(vigente); }
      catch { /* La siguiente posición vigente se intentará al recuperar conexión. */ }
      finally { ejecutando = false; }
    }
    const notificar = () => { void actualizar(); };
    oyentes.add(notificar);
    const timer = setInterval(notificar, INTERVALO_UBICACION_MS);
    const subscription = AppState.addEventListener('change', (estado) => {
      if (estado === 'active') notificar();
    });
    notificar();
    return () => { montado = false; oyentes.delete(notificar); clearInterval(timer); subscription.remove(); };
  }, []);
}
