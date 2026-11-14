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
