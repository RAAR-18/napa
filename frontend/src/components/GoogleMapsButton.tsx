import { useState } from 'react';
import { Linking } from 'react-native';

import { ErrorBanner, TextButton } from './AuthUI';
import { urlRutaGoogleMaps, type PuntoGoogleMaps } from '../lib/mapas';

export function GoogleMapsButton({ title, destino, parada, disabled = false }: {
  title: string;
  destino: PuntoGoogleMaps;
  parada?: PuntoGoogleMaps;
  disabled?: boolean;
}) {
  const [error, setError] = useState<string | null>(null);
  async function abrir() {
    setError(null);
    try {
      await Linking.openURL(urlRutaGoogleMaps(destino, parada));
    } catch {
      setError('No se pudo abrir Google Maps. Inténtalo de nuevo.');
    }
  }
  return (
    <>
      <TextButton icon="map-pin" title={title} variant="soft" disabled={disabled} onPress={() => void abrir()} />
      <ErrorBanner message={error} />
    </>
  );
}
