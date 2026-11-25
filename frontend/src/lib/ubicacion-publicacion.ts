import * as Location from 'expo-location';
import { getSessionGeneration, readSession } from './auth';
import { editarMiEmprendimiento, obtenerMiEmprendimiento } from './emprendimientos';
import { leerUbicacionAutomatica, mismaPreferencia } from './ubicacion-preferencia';
let ejecutando = false;
export async function actualizarZonaAutomatica(vigente: () => boolean = () => true, punto?: Location.LocationObject): Promise<void> {
  if (ejecutando) return;
  ejecutando = true;
  try { await publicar(vigente, punto); }
  finally { ejecutando = false; }
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
async function publicar(vigente: () => boolean, punto?: Location.LocationObject): Promise<void> {
  const generacion = getSessionGeneration();
  const session = await readSession();
  const preferencia = await leerUbicacionAutomatica();
  if (!vigente() || !session?.usuarioId || session.rol !== 'VENDEDOR_AMBULANTE'
    || preferencia?.usuarioId !== session.usuarioId || (punto && !preferencia.segundoPlano)) return;
  const permiso = await Location.getForegroundPermissionsAsync();
  if (!vigente() || permiso.status !== 'granted' || generacion !== getSessionGeneration()) return;
  const negocio = await obtenerMiEmprendimiento(preferencia.usuarioId);
  if (!vigente() || generacion !== getSessionGeneration() || negocio.id !== preferencia.emprendimientoId) return;
  const antes = await leerUbicacionAutomatica();
  if (!mismaPreferencia(antes, preferencia)) return;
  const posicion = punto ?? await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
  const { latitude, longitude } = posicion.coords;
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)
    || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return;
  if (!vigente() || generacion !== getSessionGeneration()) return;
  const actual = await leerUbicacionAutomatica();
  if (!vigente() || generacion !== getSessionGeneration() || negocio.id !== preferencia.emprendimientoId
    || !mismaPreferencia(actual, preferencia)) return;
  if (distanciaMetros(negocio.ubicacion, posicion.coords) < 25
    && !negocio.ubicacion.direccion && !negocio.ubicacion.referencia) return;
  await editarMiEmprendimiento({ ubicacion: {
    latitud: latitude, longitud: longitude,
    direccion: null, referencia: null,
  } }, preferencia.usuarioId, async () => {
    if (!vigente() || generacion !== getSessionGeneration()) return false;
    const cuenta = await readSession();
    const configuracion = await leerUbicacionAutomatica();
    return vigente() && generacion === getSessionGeneration() && cuenta?.usuarioId === preferencia.usuarioId
      && cuenta.rol === 'VENDEDOR_AMBULANTE' && mismaPreferencia(configuracion, preferencia)
      && (!punto || configuracion?.segundoPlano === true);
  });
}

