import { useEffect } from 'react';
import { AppState } from 'react-native';
import * as Location from 'expo-location';

import * as storage from './auth-storage';
import { getSessionGeneration, readSession } from './auth';
import { randomUUID } from './uuid';
import { leerUbicacionAutomatica, mismaPreferencia, desactivarSiVencida, UBICACION_KEY, oyentesUbicacion, type Preferencia } from './ubicacion-preferencia';
import { actualizarZonaAutomatica, UMBRAL_PUBLICACION_METROS } from './ubicacion-publicacion';
import { solicitarPermisoSegundoPlano, iniciarSegundoPlano, detenerSegundoPlano, reconciliarSegundoPlano } from './ubicacion-segundo-plano';
export { leerUbicacionAutomatica, suscribirUbicacionAutomatica } from './ubicacion-preferencia';
export { actualizarZonaAutomatica } from './ubicacion-publicacion';

const KEY = UBICACION_KEY;
/** Con la app abierta, Android entrega como mucho una posición por minuto y solo tras moverse el umbral. */
export const INTERVALO_UBICACION_MS = 60_000;
const oyentes = oyentesUbicacion;

/** Desactiva primero la preferencia, de modo que una tarea pendiente no pueda volver a publicar. */
export async function desactivarUbicacionCompartida(): Promise<void> {
  try { await storage.deleteItemAsync(KEY); }
  finally {
    oyentes.forEach((oyente) => oyente());
    await detenerSegundoPlano();
  }
}

export async function configurarUbicacionAutomatica(
  usuarioId: string, emprendimientoId: string, activar: boolean, segundoPlano = false,
): Promise<void> {
  const generacion = getSessionGeneration();
  const session = await readSession();
  if (session?.usuarioId !== usuarioId || session.rol !== 'VENDEDOR_AMBULANTE') {
    throw new Error('Inicia sesión con la cuenta del vendedor ambulante para continuar.');
  }
  if (!activar) {
    const actual = await leerUbicacionAutomatica();
    if (actual?.usuarioId === usuarioId && actual.emprendimientoId === emprendimientoId) {
      await desactivarUbicacionCompartida();
    }
    return;
  }
  const permiso = await Location.requestForegroundPermissionsAsync();
  if (permiso.status !== 'granted') throw new Error('Permite la ubicación para actualizar tu negocio móvil.');
  if (segundoPlano) await solicitarPermisoSegundoPlano();
  const actual = await readSession();
  if (generacion !== getSessionGeneration() || actual?.usuarioId !== usuarioId || actual.rol !== 'VENDEDOR_AMBULANTE') {
    throw new Error('La sesión cambió. Vuelve a abrir tu emprendimiento.');
  }
  if (segundoPlano) await storage.prepararSesionSegundoPlano();
  const anterior = await leerUbicacionAutomatica();
  const preferencia: Preferencia = { usuarioId, emprendimientoId, segundoPlano,
    activacionId: randomUUID(), iniciadaAt: Date.now() };
  if (generacion !== getSessionGeneration()) throw new Error('La sesión cambió.');
  await storage.setItemAsync(KEY, JSON.stringify(preferencia));
  try {
    if (generacion !== getSessionGeneration()) throw new Error('La sesión cambió.');
    if (segundoPlano) await iniciarSegundoPlano();
    else await detenerSegundoPlano();
    if (generacion !== getSessionGeneration() || !mismaPreferencia(await leerUbicacionAutomatica(), preferencia)) {
      await detenerSegundoPlano();
      throw new Error('Se canceló la activación de ubicación.');
    }
  } catch (error) {
    if (mismaPreferencia(await leerUbicacionAutomatica(), preferencia)) {
      if (anterior && generacion === getSessionGeneration()) await storage.setItemAsync(KEY, JSON.stringify(anterior));
      else await storage.deleteItemAsync(KEY);
    }
    throw error;
  } finally { oyentes.forEach((oyente) => oyente()); }
}

/**
 * Con la app abierta y sin segundo plano, el sistema avisa al moverse (watchPositionAsync con filtro de
 * distancia), en lugar de leer el GPS cada pocos segundos. Quieto, no hay lecturas ni peticiones.
 */
export function useUbicacionAutomatica() {
  useEffect(() => {
    let montado = true;
    let ejecutando = false;
    let vigilancia: Location.LocationSubscription | null = null;
    let sincronizando = false;
    let pendiente = false;
    const vigente = () => montado && AppState.currentState === 'active';

    function soltarVigilancia() { vigilancia?.remove(); vigilancia = null; }

    async function debeVigilar(): Promise<boolean> {
      if (!vigente()) return false;
      const preferencia = await leerUbicacionAutomatica();
      if (!preferencia || preferencia.segundoPlano) return false;
      if (await desactivarSiVencida(preferencia)) return false;
      return (await Location.getForegroundPermissionsAsync()).status === 'granted';
    }

    async function sincronizarVigilancia() {
      if (sincronizando) { pendiente = true; return; }
      sincronizando = true;
      try {
        do {
          pendiente = false;
          if (!(await debeVigilar())) { soltarVigilancia(); continue; }
          if (vigilancia) continue;
          const nueva = await Location.watchPositionAsync({
            accuracy: Location.Accuracy.Balanced,
            distanceInterval: UMBRAL_PUBLICACION_METROS,
            timeInterval: INTERVALO_UBICACION_MS,
          }, (posicion) => { void actualizar(posicion); }, () => undefined);
          if (vigente()) vigilancia = nueva; else nueva.remove();
        } while (pendiente);
      } catch { soltarVigilancia(); }
      finally { sincronizando = false; }
    }

    async function actualizar(punto?: Location.LocationObject) {
      if (!vigente() || ejecutando) return;
      ejecutando = true;
      try {
        if (!(await leerUbicacionAutomatica())?.segundoPlano) await actualizarZonaAutomatica(vigente, punto, false);
      }
      catch { /* La siguiente posición vigente se intentará al recuperar conexión. */ }
      finally { ejecutando = false; }
    }
    const notificar = () => { void sincronizarVigilancia(); void actualizar(); };
    oyentes.add(notificar);
    const subscription = AppState.addEventListener('change', (estado) => {
      if (estado === 'active') {
        void reconciliarSegundoPlano().catch(() => undefined);
        notificar();
      } else soltarVigilancia();
    });
    void reconciliarSegundoPlano().catch(() => undefined);
    notificar();
    return () => { montado = false; oyentes.delete(notificar); soltarVigilancia(); subscription.remove(); };
  }, []);
}
