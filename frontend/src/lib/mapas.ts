import Constants from 'expo-constants';
import { Platform } from 'react-native';

/**
 * En Android, Google Maps cierra la app si el build no trae clave (`GOOGLE_MAPS_ANDROID_API_KEY` en
 * app.config.js). Sin clave se muestran solo las direcciones en texto. iOS usa Apple Maps y no la necesita.
 */
export function mapasDisponibles() {
  if (Platform.OS !== 'android') return true;
  return Constants.expoConfig?.extra?.mapasAndroidHabilitados === true;
}

export type PuntoGoogleMaps = { latitud: number; longitud: number; aproximado?: boolean };

/** El origen se deja al dispositivo; nunca se publica un destino aproximado como si fuera exacto. */
export function urlRutaGoogleMaps(destino: PuntoGoogleMaps, parada?: PuntoGoogleMaps): string {
  const coordenadas = (punto: PuntoGoogleMaps) => {
    if (punto.aproximado || !Number.isFinite(punto.latitud) || !Number.isFinite(punto.longitud) ||
        Math.abs(punto.latitud) > 90 || Math.abs(punto.longitud) > 180) {
      throw new Error('La ubicación exacta no está disponible.');
    }
    return `${punto.latitud},${punto.longitud}`;
  };
  const parametros = new URLSearchParams({ api: '1', destination: coordenadas(destino), dir_action: 'navigate' });
  if (parada) parametros.set('waypoints', coordenadas(parada));
  return `https://www.google.com/maps/dir/?${parametros.toString()}`;
}
