import { StyleSheet, Text } from 'react-native';
import MapView, { Marker, Polyline } from 'react-native-maps';

import { decodificarPolilinea, type RutaDomicilio } from '../lib/domicilios';
import { mapasDisponibles } from '../lib/mapas';
import { HelperText, palette } from './AuthUI';

type Coordenada = { latitude: number; longitude: number };

type Props = {
  ruta: RutaDomicilio;
  posicion: { lat: number; lng: number } | null;
};

/**
 * RF-22: posición del domiciliario, punto A (recogida), punto B (entrega) y el tramo pendiente. Un tramo aproximado
 * (sin polilínea) se dibuja como línea recta punteada.
 */
export default function MapaRutaDomicilio({ ruta, posicion }: Props) {
  if (!mapasDisponibles()) {
    return (
      <HelperText>
        <Text>El mapa no está disponible en esta versión de la app. Usa las direcciones de abajo.</Text>
      </HelperText>
    );
  }
  const origen = { latitude: ruta.origen.latitud, longitude: ruta.origen.longitud };
  const destino = { latitude: ruta.destino.latitud, longitude: ruta.destino.longitud };
  const aqui = posicion ? { latitude: posicion.lat, longitude: posicion.lng } : null;

  const lineas = ruta.tramos.map((tramo) => {
    const desde = tramo.tipo === 'HACIA_ORIGEN' ? aqui : ruta.estado === 'ASIGNADO' ? origen : (aqui ?? origen);
    const hasta = tramo.tipo === 'HACIA_ORIGEN' ? origen : destino;
    let coordenadas: Coordenada[] = desde ? [desde, hasta] : [hasta];
    if (tramo.polilinea) {
      try {
        coordenadas = decodificarPolilinea(tramo.polilinea);
      } catch {
        // Polilínea dañada: se dibuja la línea recta.
      }
    }
    return { tipo: tramo.tipo, coordenadas, aproximado: tramo.aproximado || !tramo.polilinea };
  });

  const todos = [origen, destino, ...(aqui ? [aqui] : [])];
  const lats = todos.map((punto) => punto.latitude);
  const lngs = todos.map((punto) => punto.longitude);
  const region = {
    latitude: (Math.min(...lats) + Math.max(...lats)) / 2,
    longitude: (Math.min(...lngs) + Math.max(...lngs)) / 2,
    latitudeDelta: Math.max(0.01, (Math.max(...lats) - Math.min(...lats)) * 1.6),
    longitudeDelta: Math.max(0.01, (Math.max(...lngs) - Math.min(...lngs)) * 1.6),
  };

  return (
    <MapView
      accessibilityLabel="Mapa de la ruta del domicilio"
      initialRegion={region}
      showsUserLocation={Boolean(aqui)}
      style={styles.map}
    >
      <Marker coordinate={origen} title="Recogida (A)" description={ruta.origen.direccion ?? undefined} />
      <Marker
        coordinate={destino}
        pinColor={palette.green}
        title="Entrega (B)"
        description={ruta.destino.direccion ?? undefined}
      />
      {lineas
        .filter((linea) => linea.coordenadas.length > 1)
        .map((linea) => (
          <Polyline
            key={linea.tipo}
            coordinates={linea.coordenadas}
            lineDashPattern={linea.aproximado ? [8, 8] : undefined}
            strokeColor={linea.tipo === 'HACIA_ORIGEN' ? palette.dark : palette.green}
            strokeWidth={4}
          />
        ))}
    </MapView>
  );
}

const styles = StyleSheet.create({
  map: { borderRadius: 16, height: 320, marginVertical: 8, overflow: 'hidden', width: '100%' },
});
