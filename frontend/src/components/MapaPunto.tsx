import { StyleSheet, Text } from 'react-native';
import MapView, { Marker } from 'react-native-maps';

import { mapasDisponibles } from '../lib/mapas';
import { HelperText, palette } from './AuthUI';

type Props = {
  punto: { latitud: number; longitud: number; direccion: string | null };
  titulo: string;
  etiqueta: string;
  posicion?: { lat: number; lng: number } | null;
};

/**
 * Un punto en el mapa y, si se conoce, la posición del usuario: la ubicación del cliente para el vendedor
 * ambulante (RF-35) o el punto fijo para el cliente (RF-32).
 */
export default function MapaPunto({ punto, titulo, etiqueta, posicion = null }: Props) {
  if (!mapasDisponibles()) {
    return (
      <HelperText>
        <Text>El mapa no está disponible en esta versión de la app. Usa la dirección de abajo.</Text>
      </HelperText>
    );
  }
  const destino = { latitude: punto.latitud, longitude: punto.longitud };
  const puntos = posicion ? [destino, { latitude: posicion.lat, longitude: posicion.lng }] : [destino];
  const lats = puntos.map((item) => item.latitude);
  const lngs = puntos.map((item) => item.longitude);
  const region = {
    latitude: (Math.min(...lats) + Math.max(...lats)) / 2,
    longitude: (Math.min(...lngs) + Math.max(...lngs)) / 2,
    latitudeDelta: Math.max(0.01, (Math.max(...lats) - Math.min(...lats)) * 1.6),
    longitudeDelta: Math.max(0.01, (Math.max(...lngs) - Math.min(...lngs)) * 1.6),
  };

  return (
    <MapView accessibilityLabel={etiqueta} initialRegion={region} showsUserLocation={Boolean(posicion)} style={styles.map}>
      <Marker coordinate={destino} pinColor={palette.green} title={titulo} description={punto.direccion ?? undefined} />
    </MapView>
  );
}

const styles = StyleSheet.create({
  map: { borderRadius: 16, height: 320, marginVertical: 8, overflow: 'hidden', width: '100%' },
});
