import { useEffect, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, Image, StyleSheet, Text, View } from 'react-native';

import { AuthPage, ErrorBanner, PageHeading, PrimaryButton, TextButton, palette } from '../components/AuthUI';
import MapaPunto from '../components/MapaPunto';
import { ApiError } from '../lib/api';
import { clearSession, getValidSession } from '../lib/auth';
import {
  categoriaNombres,
  obtenerEmprendimiento,
  obtenerVendedorEmprendimiento,
  type EmprendimientoDetalle,
  type VendedorPublico,
} from '../lib/emprendimientos';

const modalidadNombres = {
  ENTREGA_DIRECTA: 'Entrega directa: el vendedor te lo lleva',
  RECOGIDA: 'Recogida en el punto',
  DOMICILIO: 'Domicilio',
} as const;

function formatMonth(value: string) {
  return new Date(`${value}T12:00:00`).toLocaleDateString('es-CO', { month: 'long', year: 'numeric' });
}

/** RF-32: detalle del emprendimiento; RF-34: quién lo atiende, solo con datos públicos. */
export default function EmprendimientoDetalleScreen() {
  const { emprendimientoId: raw } = useLocalSearchParams<{ emprendimientoId?: string | string[] }>();
  const emprendimientoId = Array.isArray(raw) ? raw[0] : raw;
  const [detalle, setDetalle] = useState<EmprendimientoDetalle | null>(null);
  const [vendedor, setVendedor] = useState<VendedorPublico | null>(null);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [noDisponible, setNoDisponible] = useState(false);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setError(null);
      if (!emprendimientoId) {
        setError('No se indicó el emprendimiento.');
        setLoading(false);
        return;
      }
      try {
        await getValidSession();
        const response = await obtenerEmprendimiento(emprendimientoId);
        if (!active) return;
        setDetalle(response);
        // La tarjeta del vendedor es secundaria: si falla, el detalle se muestra igual.
        obtenerVendedorEmprendimiento(emprendimientoId)
          .then((value) => {
            if (active) setVendedor(value);
          })
          .catch(() => undefined);
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 401) {
          await clearSession();
          router.replace('/login');
          return;
        }
        if (!active) return;
        setNoDisponible(cause instanceof ApiError && cause.status === 404);
        setError(cause instanceof Error ? cause.message : 'No se pudo cargar el emprendimiento.');
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [emprendimientoId, attempt]);

  const puntoFijo = detalle?.tipoVendedor === 'PUNTO_FIJO';

  return (
    <AuthPage>
      <PageHeading
        eyebrow={detalle ? categoriaNombres[detalle.categoria] : 'Emprendimiento'}
        title={detalle?.nombre ?? 'Detalle del emprendimiento'}
        description={detalle?.descripcion ?? 'Consulta dónde está, su horario y cómo entrega.'}
      />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando emprendimiento" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {!loading && error && !noDisponible && !detalle ? (
        <PrimaryButton title="Reintentar" onPress={() => setAttempt((value) => value + 1)} />
      ) : null}
      {detalle ? (
        <>
          <View style={styles.card}>
            <Text style={[styles.status, !detalle.abierto && styles.closed]}>
              {detalle.abierto ? 'Abierto ahora' : 'Cerrado'}
            </Text>
            <Text style={styles.text}>
              Horario: {detalle.horaApertura.slice(0, 5)} a {detalle.horaCierre.slice(0, 5)}
            </Text>
            <Text style={styles.text}>{puntoFijo ? 'Vendedor de punto fijo' : 'Vendedor ambulante'}</Text>
          </View>
          <View style={styles.card}>
            <Text style={styles.title}>Cómo te entrega</Text>
            {detalle.modalidadesEntrega.map((modalidad) => (
              <Text key={modalidad} style={styles.text}>• {modalidadNombres[modalidad]}</Text>
            ))}
            <Text style={styles.text}>Cualquier pedido puede hacerse como reserva.</Text>
          </View>
          {puntoFijo ? (
            <View style={styles.card}>
              <Text style={styles.title}>Dónde está</Text>
              <MapaPunto punto={detalle.ubicacion} titulo={detalle.nombre} etiqueta="Mapa del punto de venta" />
              {detalle.ubicacion.direccion ? <Text style={styles.address}>{detalle.ubicacion.direccion}</Text> : null}
              {detalle.ubicacion.referencia ? (
                <Text style={styles.text}>Referencia: {detalle.ubicacion.referencia}</Text>
              ) : null}
            </View>
          ) : detalle.ubicacion.direccion || detalle.ubicacion.referencia ? (
            <View style={styles.card}>
              <Text style={styles.title}>Zona habitual</Text>
              <Text style={styles.text}>
                {[detalle.ubicacion.direccion, detalle.ubicacion.referencia].filter(Boolean).join(' · ')}
              </Text>
            </View>
          ) : null}
          {vendedor ? (
            <View style={[styles.card, styles.seller]}>
              {vendedor.fotoUrl ? (
                <Image accessibilityLabel={`Foto de ${vendedor.nombre}`} source={{ uri: vendedor.fotoUrl }} style={styles.photo} />
              ) : (
                <View style={[styles.photo, styles.initials]}>
                  <Text style={styles.initialsText}>{vendedor.nombre.charAt(0).toUpperCase()}</Text>
                </View>
              )}
              <View style={styles.sellerText}>
                <Text style={styles.title}>Te atiende {vendedor.nombre}</Text>
                {vendedor.miembroDesde ? (
                  <Text style={styles.text}>En Ñapa desde {formatMonth(vendedor.miembroDesde)}</Text>
                ) : null}
              </View>
            </View>
          ) : null}
          <PrimaryButton
            title="Ver productos"
            onPress={() =>
              router.push({
                pathname: '/catalogo',
                params: { emprendimientoId: detalle.id, tipoVendedor: detalle.tipoVendedor },
              })
            }
          />
        </>
      ) : null}
      <TextButton title="Volver a emprendimientos" onPress={() => router.replace('/emprendimientos')} />
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
  status: { color: palette.green, fontSize: 17, fontWeight: '800' },
  closed: { color: palette.muted },
  title: { color: palette.dark, fontSize: 16, fontWeight: '800' },
  address: { color: palette.dark, fontSize: 15, fontWeight: '700', marginTop: 6 },
  text: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 6 },
  seller: { alignItems: 'center', flexDirection: 'row' },
  sellerText: { flex: 1, marginLeft: 12 },
  photo: { borderRadius: 28, height: 56, width: 56 },
  initials: { alignItems: 'center', backgroundColor: '#E3F0E8', justifyContent: 'center' },
  initialsText: { color: palette.green, fontSize: 22, fontWeight: '800' },
});
