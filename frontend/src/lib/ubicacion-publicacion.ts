import * as Location from 'expo-location';
import { getSessionGeneration, readSession } from './auth';
import { editarMiEmprendimiento, obtenerMiEmprendimiento } from './emprendimientos';
import { leerUbicacionAutomatica, mismaPreferencia, ubicacionVencida, type Preferencia } from './ubicacion-preferencia';

/** Movimiento mínimo para volver a publicar: al cliente le basta la zona, no el paso a paso. */
export const UMBRAL_PUBLICACION_METROS = 50;
/** Cada cuánto se vuelve a consultar el negocio aunque el vendedor no se mueva (por si lo editó en otro equipo). */
export const REVALIDAR_NEGOCIO_MS = 10 * 60_000;

/**
 * Último punto publicado en este runtime. Evita el GET previo a cada envío: si el vendedor no se movió lo
 * suficiente, no se usa la red. Solo vive en memoria, así que no guarda recorridos.
 */
type UltimoPublicado = {
  usuarioId: string; emprendimientoId: string; activacionId?: string; generacion: number;
  latitud: number; longitud: number; limpio: boolean; consultadoAt: number;
};
let ultimo: UltimoPublicado | null = null;

/** Solo para pruebas: olvida el último punto publicado. */
export function olvidarUltimaPublicacion() { ultimo = null; }

let ejecutando = false;
/**
 * `segundoPlano` indica que el punto viene de la tarea nativa (exige esa preferencia activa). Por defecto se
 * asume así cuando llega un punto, para conservar el contrato de la tarea.
 */
export async function actualizarZonaAutomatica(
  vigente: () => boolean = () => true, punto?: Location.LocationObject, segundoPlano = Boolean(punto),
): Promise<void> {
  if (ejecutando) return;
  ejecutando = true;
  try { await publicar(vigente, punto, segundoPlano); }
  finally { ejecutando = false; }
}

export function distanciaMetros(a: { latitud: number; longitud: number }, b: { latitude: number; longitude: number }) {
  const rad = Math.PI / 180;
  const lat = (b.latitude - a.latitud) * rad;
  const lng = (b.longitude - a.longitud) * rad;
  const h = Math.sin(lat / 2) ** 2
    + Math.cos(a.latitud * rad) * Math.cos(b.latitude * rad) * Math.sin(lng / 2) ** 2;
  return 6_371_000 * 2 * Math.asin(Math.sqrt(Math.min(1, h)));
}

function recordado(preferencia: Preferencia, generacion: number): UltimoPublicado | null {
  return ultimo && ultimo.usuarioId === preferencia.usuarioId && ultimo.emprendimientoId === preferencia.emprendimientoId
    && ultimo.activacionId === preferencia.activacionId && ultimo.generacion === generacion
    && Date.now() - ultimo.consultadoAt < REVALIDAR_NEGOCIO_MS ? ultimo : null;
}

/** No solicita permisos por sí sola ni acumula posiciones para reenvío. */
async function publicar(vigente: () => boolean, punto: Location.LocationObject | undefined, segundoPlano: boolean): Promise<void> {
  const generacion = getSessionGeneration();
  const session = await readSession();
  const preferencia = await leerUbicacionAutomatica();
  if (!vigente() || !session?.usuarioId || session.rol !== 'VENDEDOR_AMBULANTE'
    || preferencia?.usuarioId !== session.usuarioId || (segundoPlano && !preferencia.segundoPlano)
    || ubicacionVencida(preferencia)) return;
  const permiso = await Location.getForegroundPermissionsAsync();
  if (!vigente() || permiso.status !== 'granted' || generacion !== getSessionGeneration()) return;
  const posicion = punto ?? await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
  const { latitude, longitude } = posicion.coords;
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)
    || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return;
  if (!vigente() || generacion !== getSessionGeneration()) return;

  // Sin moverse lo suficiente no hay red: ni consulta ni envío.
  const previo = recordado(preferencia, generacion);
  if (previo?.limpio && distanciaMetros(previo, posicion.coords) < UMBRAL_PUBLICACION_METROS) return;

  let referencia = previo;
  if (!referencia) {
    const negocio = await obtenerMiEmprendimiento(preferencia.usuarioId);
    if (!vigente() || generacion !== getSessionGeneration() || negocio.id !== preferencia.emprendimientoId) return;
    referencia = { usuarioId: preferencia.usuarioId, emprendimientoId: negocio.id, activacionId: preferencia.activacionId,
      generacion, latitud: negocio.ubicacion.latitud, longitud: negocio.ubicacion.longitud,
      limpio: !negocio.ubicacion.direccion && !negocio.ubicacion.referencia, consultadoAt: Date.now() };
    ultimo = referencia;
    if (referencia.limpio && distanciaMetros(referencia, posicion.coords) < UMBRAL_PUBLICACION_METROS) return;
  }

  const actual = await leerUbicacionAutomatica();
  if (!vigente() || generacion !== getSessionGeneration() || !mismaPreferencia(actual, preferencia)) return;
  await editarMiEmprendimiento({ ubicacion: {
    latitud: latitude, longitud: longitude,
    direccion: null, referencia: null,
  } }, preferencia.usuarioId, async () => {
    if (!vigente() || generacion !== getSessionGeneration()) return false;
    const cuenta = await readSession();
    const configuracion = await leerUbicacionAutomatica();
    return vigente() && generacion === getSessionGeneration() && cuenta?.usuarioId === preferencia.usuarioId
      && cuenta.rol === 'VENDEDOR_AMBULANTE' && mismaPreferencia(configuracion, preferencia)
      && (!segundoPlano || configuracion?.segundoPlano === true);
  });
  ultimo = { ...referencia, latitud: latitude, longitud: longitude, limpio: true };
}
