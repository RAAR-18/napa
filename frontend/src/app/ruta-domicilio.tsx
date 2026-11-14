import { useEffect, useState } from 'react';
import * as Location from 'expo-location';
import { router, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  HelperText,
  PageHeading,
  PrimaryButton,
  TextButton,
  palette,
} from '../components/AuthUI';
import MapaRutaDomicilio from '../components/MapaRutaDomicilio';
import { ApiError } from '../lib/api';
import { clearSession } from '../lib/auth';
import {
  estadoDomicilioNombres,
  formatearDistancia,
  formatearDuracion,
  obtenerRutaDomicilio,
  type PuntoDomiciliario,
  type RutaDomicilio,
  type TramoRuta,
} from '../lib/domicilios';
import { borrarRuta, guardarRuta, leerRuta } from '../lib/ruta-cache';

type Posicion = { lat: number; lng: number };

const nombresTramo: Record<TramoRuta['tipo'], string> = {
  HACIA_ORIGEN: 'Hacia la recogida (A)',
  HACIA_DESTINO: 'Hacia la entrega (B)',
};

function formatTime(value: string) {
  return new Date(value).toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' });
}

async function leerPosicion(): Promise<{ posicion: Posicion | null; aviso: string | null }> {
  try {
    const permission = await Location.requestForegroundPermissionsAsync();
    if (permission.status !== 'granted') {
      return {
        posicion: null,
        aviso: 'Sin permiso de ubicación no vemos dónde estás. Puedes guiarte por los puntos y las direcciones.',
      };
    }
    const location = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
    return { posicion: { lat: location.coords.latitude, lng: location.coords.longitude }, aviso: null };
  } catch {
    return { posicion: null, aviso: 'No se pudo obtener tu ubicación. Puedes guiarte por las direcciones.' };
  }
}

/** RF-22: mapa con la posición del domiciliario, los puntos A y B y el tramo pendiente; las direcciones en texto. */
export default function RutaDomicilioScreen() {
  const { domicilioId: rawId } = useLocalSearchParams<{ domicilioId?: string | string[] }>();
  const domicilioId = Array.isArray(rawId) ? rawId[0] : rawId;
  const [ruta, setRuta] = useState<RutaDomicilio | null>(null);
  const [posicion, setPosicion] = useState<Posicion | null>(null);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [guardadaAt, setGuardadaAt] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setError(null);
      if (!domicilioId) {
        setError('No se indicó el domicilio.');
        setLoading(false);
        return;
      }
      const lectura = await leerPosicion();
      if (!active) return;
      setPosicion(lectura.posicion);
      setAviso(lectura.aviso);
      try {
        const response = await obtenerRutaDomicilio(domicilioId, lectura.posicion);
        if (!active) return;
        setRuta(response);
        setGuardadaAt(null);
        void guardarRuta(response).catch(() => undefined);
      } catch (cause) {
        if (!active) return;
        if (cause instanceof ApiError) {
          if (cause.status === 401) {
            await clearSession();
            router.replace('/login');
            return;
          }
          if ([403, 404, 409].includes(cause.status)) {
            void borrarRuta(domicilioId).catch(() => undefined);
            setRuta(null);
            setError(
              cause.status === 409
                ? 'Este domicilio ya terminó; no hay ruta que seguir.'
                : cause.status === 403
                  ? 'No tienes acceso a la ruta de este domicilio.'
                  : 'El domicilio no está disponible.',
            );
            return;
          }
        }
        // Sin conexión o error del servidor: la última ruta guardada sigue sirviendo.
        const guardada = await leerRuta(domicilioId).catch(() => null);
        if (!active) return;
        if (guardada) {
          setRuta(guardada.ruta);
          setGuardadaAt(guardada.updatedAt);
        } else {
          setError(cause instanceof Error ? cause.message : 'No se pudo cargar la ruta.');
        }
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [domicilioId, attempt]);

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Domiciliario"
        title="Ruta del domicilio"
        description={ruta ? `Estado: ${estadoDomicilioNombres[ruta.estado]}` : 'Tu camino a la recogida y a la entrega.'}
      />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando ruta" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {aviso ? <HelperText>{aviso}</HelperText> : null}
      {guardadaAt ? (
        <HelperText>Sin conexión. Mostramos la ruta guardada a las {formatTime(guardadaAt)}.</HelperText>
      ) : null}
      {ruta ? (
        <>
          <MapaRutaDomicilio ruta={ruta} posicion={posicion} />
          <View style={styles.card}>
            {ruta.tramos.map((tramo) => (
              <Text key={tramo.tipo} style={styles.detailText}>
                {nombresTramo[tramo.tipo]}: {formatearDistancia(tramo.distanciaMetros)}
                {tramo.duracionSegundos !== null ? ` · ${formatearDuracion(tramo.duracionSegundos)}` : ''}
                {tramo.aproximado ? ' (aproximado, en línea recta)' : ''}
              </Text>
            ))}
          </View>
          <Direccion titulo="Recogida (A)" punto={ruta.origen} />
          <Direccion titulo="Entrega (B)" punto={ruta.destino} />
        </>
      ) : null}
      <PrimaryButton
        title="Actualizar mi posición"
        disabled={loading}
        onPress={() => setAttempt((value) => value + 1)}
      />
      {domicilioId ? (
        <TextButton
          title="Volver al domicilio"
          onPress={() => router.replace({ pathname: '/domicilio-domiciliario', params: { domicilioId } })}
        />
      ) : null}
    </AuthPage>
  );
}

/** Respaldo en texto (FR-003): sirve aunque el mapa no cargue. */
function Direccion({ titulo, punto }: { titulo: string; punto: PuntoDomiciliario }) {
  return (
    <View style={styles.card}>
      <Text style={styles.sectionTitle}>{titulo}</Text>
      <Text style={styles.detailText}>{punto.direccion ?? 'Sin dirección escrita'}</Text>
      {punto.barrio ? <Text style={styles.detailText}>Barrio: {punto.barrio}</Text> : null}
      {punto.referencia ? <Text style={styles.detailText}>Referencia: {punto.referencia}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: palette.white,
    borderColor: palette.border,
    borderRadius: 16,
    borderWidth: 1,
    marginVertical: 7,
    padding: 16,
  },
  detailText: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 6 },
  sectionTitle: { color: palette.dark, fontSize: 16, fontWeight: '800' },
});
