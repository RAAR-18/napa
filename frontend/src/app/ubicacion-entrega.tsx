import { useEffect, useState } from 'react';
import * as Location from 'expo-location';
import { router, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, Linking, StyleSheet, Text, View } from 'react-native';

import { AuthPage, ErrorBanner, HelperText, PageHeading, PrimaryButton, TextButton, palette } from '../components/AuthUI';
import MapaPunto from '../components/MapaPunto';
import { ApiError } from '../lib/api';
import { clearSession, getValidSession } from '../lib/auth';
import { obtenerPedidoVendedor, type PedidoVendedorDetalle } from '../lib/pedidos';

type Posicion = { lat: number; lng: number };
const NO_DISPONIBLE = 'El pedido no está disponible.';

/** Distancia en línea recta, en metros. */
function distanciaMetros(a: Posicion, b: Posicion) {
  const rad = (grados: number) => (grados * Math.PI) / 180;
  const dLat = rad(b.lat - a.lat);
  const dLng = rad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * 6_371_000 * Math.asin(Math.sqrt(h));
}

function textoDistancia(metros: number) {
  return metros < 1000 ? `${Math.round(metros)} m` : `${(metros / 1000).toLocaleString('es-CO', { maximumFractionDigits: 1 })} km`;
}

async function leerPosicion(): Promise<{ posicion: Posicion | null; aviso: string | null }> {
  try {
    const permission = await Location.requestForegroundPermissionsAsync();
    if (permission.status !== 'granted') {
      return { posicion: null, aviso: 'Sin permiso de ubicación no vemos dónde estás. Guíate por la dirección.' };
    }
    const location = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
    return { posicion: { lat: location.coords.latitude, lng: location.coords.longitude }, aviso: null };
  } catch {
    return { posicion: null, aviso: 'No se pudo obtener tu ubicación. Guíate por la dirección.' };
  }
}

/**
 * RF-35: el vendedor ambulante ve dónde está el cliente de un pedido de entrega directa aceptado o en camino.
 * La ubicación viene del detalle del pedido (RF-51), que la oculta al terminar el pedido.
 */
export default function UbicacionEntregaScreen() {
  const { pedidoId: raw } = useLocalSearchParams<{ pedidoId?: string | string[] }>();
  const pedidoId = Array.isArray(raw) ? raw[0] : raw;
  const [pedido, setPedido] = useState<PedidoVendedorDetalle | null>(null);
  const [posicion, setPosicion] = useState<Posicion | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setError(null);
      if (!pedidoId) {
        setError('No se indicó el pedido.');
        setLoading(false);
        return;
      }
      try {
        const session = await getValidSession();
        if (!active) return;
        if (session.rol !== 'VENDEDOR_AMBULANTE') {
          setError('Solo el vendedor ambulante del pedido puede ver la ubicación del cliente.');
          return;
        }
        const [detalle, lectura] = await Promise.all([obtenerPedidoVendedor(pedidoId), leerPosicion()]);
        if (!active) return;
        const activo = detalle.estado === 'ACEPTADO' || detalle.estado === 'EN_CAMINO';
        if (detalle.modalidad !== 'ENTREGA_DIRECTA' || !activo || !detalle.ubicacionEntrega) {
          setError(NO_DISPONIBLE);
          return;
        }
        setPedido(detalle);
        setPosicion(lectura.posicion);
        setAviso(lectura.aviso);
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 401) {
          await clearSession();
          router.replace('/login');
          return;
        }
        if (active) setError(cause instanceof Error ? cause.message : 'No se pudo cargar la ubicación.');
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [pedidoId, attempt]);

  const ubicacion = pedido?.ubicacionEntrega ?? null;
  const distancia =
    ubicacion && posicion ? distanciaMetros(posicion, { lat: ubicacion.latitud, lng: ubicacion.longitud }) : null;

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Entrega directa"
        title={pedido ? `Ubicación de ${pedido.cliente.nombre}` : 'Ubicación del cliente'}
        description="Es el punto que indicó el cliente al hacer el pedido."
      />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando ubicación" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {!loading && error && error !== NO_DISPONIBLE && !pedido ? (
        <PrimaryButton title="Reintentar" onPress={() => setAttempt((value) => value + 1)} />
      ) : null}
      {pedido && ubicacion ? (
        <>
          <MapaPunto punto={ubicacion} titulo="Cliente" etiqueta="Mapa con la ubicación del cliente" posicion={posicion} />
          {aviso ? <HelperText>{aviso}</HelperText> : null}
          <View style={styles.card}>
            <Text style={styles.address}>{ubicacion.direccion}</Text>
            {ubicacion.barrio ? <Text style={styles.text}>Barrio: {ubicacion.barrio}</Text> : null}
            {ubicacion.referencia ? <Text style={styles.text}>Referencia: {ubicacion.referencia}</Text> : null}
            {distancia !== null ? (
              <Text style={styles.distance}>Estás a {textoDistancia(distancia)} en línea recta.</Text>
            ) : null}
            {pedido.reserva ? (
              <Text style={styles.text}>
                Es una reserva: confirma con el cliente que sigue en este lugar antes de salir.
              </Text>
            ) : null}
          </View>
          <TextButton
            title="Abrir en Google Maps"
            onPress={() =>
              void Linking.openURL(
                `https://www.google.com/maps/dir/?api=1&destination=${ubicacion.latitud},${ubicacion.longitud}`,
              )
            }
          />
          <TextButton title="Actualizar mi posición" disabled={loading} onPress={() => setAttempt((value) => value + 1)} />
        </>
      ) : null}
      <TextButton
        title="Volver al pedido"
        onPress={() =>
          pedidoId ? router.replace({ pathname: '/pedido-vendedor', params: { pedidoId } }) : router.back()
        }
      />
    </AuthPage>
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
  address: { color: palette.dark, fontSize: 16, fontWeight: '800' },
  text: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 6 },
  distance: { color: palette.green, fontSize: 15, fontWeight: '700', marginTop: 8 },
});
