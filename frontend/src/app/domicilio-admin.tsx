import { useEffect, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

import { AuthPage, ErrorBanner, PageHeading, PrimaryButton, TextButton, palette } from '../components/AuthUI';
import CancelarDomicilioAdmin from '../components/CancelarDomicilioAdmin';
import { ApiError } from '../lib/api';
import { clearSession, getValidSession } from '../lib/auth';
import {
  MINUTOS_SIN_TOMAR_RESALTE,
  estadoDomicilioNombres,
  motivoCancelacionNombres,
  obtenerDomicilioAdmin,
  textoSinTomar,
  type DomicilioAdminDetalle,
  type PersonaDomicilio,
  type PuntoSupervision,
} from '../lib/domicilios';

const precio = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
const estadosPedido: Record<string, string> = {
  PENDIENTE: 'Pendiente',
  ACEPTADO: 'Aceptado',
  RECHAZADO: 'Rechazado',
  EN_CAMINO: 'En camino',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
};

function formatDate(value: string) {
  return new Date(value).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
}

function Punto({ titulo, punto }: { titulo: string; punto: PuntoSupervision }) {
  return (
    <Text style={styles.detailText}>
      {titulo}: {punto.direccion ?? 'Sin dirección'}
      {punto.barrio ? ` (${punto.barrio})` : ''}
    </Text>
  );
}

function PersonaFila({
  rol,
  persona,
  filtro,
}: {
  rol: string;
  persona: PersonaDomicilio;
  filtro?: 'vendedorId' | 'domiciliarioId';
}) {
  return (
    <View style={styles.personRow}>
      <Text style={styles.detailText}>
        {rol}: {persona.nombre ?? '—'}
      </Text>
      {filtro ? (
        <TextButton
          title={filtro === 'vendedorId' ? 'Ver domicilios del vendedor' : 'Ver domicilios del domiciliario'}
          onPress={() =>
            router.push({
              pathname: '/domicilios-admin',
              params: { [filtro]: persona.id, persona: persona.nombre ?? '' },
            })
          }
        />
      ) : null}
    </View>
  );
}

/** RF-12 (detalle completo) y RF-13 (cancelar). El código de confirmación nunca llega a esta pantalla. */
export default function DomicilioAdminScreen() {
  const { domicilioId: raw } = useLocalSearchParams<{ domicilioId?: string | string[] }>();
  const domicilioId = Array.isArray(raw) ? raw[0] : raw;
  const [data, setData] = useState<DomicilioAdminDetalle | null>(null);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [changed, setChanged] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    async function load() {
      if (!domicilioId) {
        setError('No encontramos el domicilio.');
        setLoading(false);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const session = await getValidSession();
        if (!active) return;
        if (session.rol !== 'ADMINISTRADOR') {
          setError('Solo el administrador ve el detalle completo de un domicilio.');
          return;
        }
        const response = await obtenerDomicilioAdmin(domicilioId);
        if (active) setData(response);
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 401) {
          await clearSession();
          router.replace('/login');
          return;
        }
        if (active) {
          setData(null);
          setError(
            cause instanceof ApiError && cause.status === 404
              ? 'El domicilio no existe.'
              : cause instanceof Error
                ? cause.message
                : 'No se pudo cargar el domicilio.',
          );
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

  const cancelable = data !== null && data.estado !== 'ENTREGADO' && data.estado !== 'CANCELADO';
  const demorado = data?.minutosSinTomar != null && data.minutosSinTomar >= MINUTOS_SIN_TOMAR_RESALTE;

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Administración"
        title="Detalle del domicilio"
        description="Información completa del domicilio, su pedido y su cronología."
      />
      {loading && !data ? <ActivityIndicator accessibilityLabel="Cargando domicilio" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {error && !data ? <PrimaryButton title="Reintentar" onPress={() => setAttempt((value) => value + 1)} /> : null}
      {notice ? <Text style={styles.success}>{notice}</Text> : null}
      <ErrorBanner message={changed} />
      {data ? (
        <>
          <View style={[styles.card, demorado && styles.cardDelayed]}>
            <View style={styles.row}>
              <Text style={styles.sectionTitle}>{estadoDomicilioNombres[data.estado]}</Text>
              <Text style={styles.amount}>{precio.format(data.tarifa)}</Text>
            </View>
            {data.minutosSinTomar !== null ? (
              <Text style={demorado ? styles.warning : styles.detailText}>{textoSinTomar(data.minutosSinTomar)}</Text>
            ) : null}
            <Punto titulo="Origen" punto={data.origen} />
            <Punto titulo="Destino" punto={data.destino} />
            <Text style={styles.detailText}>Publicado: {formatDate(data.createdAt)}</Text>
            <Text style={styles.detailText}>Última actualización: {formatDate(data.updatedAt)}</Text>
          </View>
          {data.estado === 'CANCELADO' && data.motivoCancelacion ? (
            <View style={styles.card}>
              <Text style={styles.sectionTitle}>Cancelación</Text>
              <Text style={styles.warning}>{motivoCancelacionNombres[data.motivoCancelacion]}</Text>
              {data.detalleCancelacion ? <Text style={styles.detailText}>Motivo: {data.detalleCancelacion}</Text> : null}
            </View>
          ) : null}
          <View style={styles.card}>
            <Text style={styles.sectionTitle}>Personas</Text>
            <PersonaFila rol="Vendedor" persona={data.vendedor} filtro="vendedorId" />
            <PersonaFila rol="Cliente" persona={data.cliente} />
            {data.domiciliario ? (
              <PersonaFila rol="Domiciliario" persona={data.domiciliario} filtro="domiciliarioId" />
            ) : (
              <Text style={styles.detailText}>Domiciliario: aún sin asignar</Text>
            )}
          </View>
          {data.pedido ? (
            <View style={styles.card}>
              <Text style={styles.sectionTitle}>Pedido</Text>
              <Text style={styles.detailText}>Estado: {estadosPedido[data.pedido.estado] ?? data.pedido.estado}</Text>
              <Text style={styles.detailText}>
                Total: {precio.format(data.pedido.total)} · {data.pedido.cantidadItems}{' '}
                {data.pedido.cantidadItems === 1 ? 'producto' : 'productos'}
              </Text>
            </View>
          ) : null}
          <View style={styles.card}>
            <Text style={styles.sectionTitle}>Pago de la tarifa</Text>
            <Text style={styles.detailText}>
              {data.pagoTarifa
                ? `${data.pagoTarifa.estado === 'CONFIRMADO' ? 'Confirmado' : 'Registrado'} · ${precio.format(data.pagoTarifa.monto)}`
                : 'Sin registrar'}
            </Text>
          </View>
          <View style={styles.card}>
            <Text style={styles.sectionTitle}>Cronología</Text>
            {data.trazabilidad.length === 0 ? <Text style={styles.detailText}>Sin eventos.</Text> : null}
            {data.trazabilidad.map((evento, index) => (
              <Text key={`${evento.estado}-${evento.ocurridoAt}-${index}`} style={styles.detailText}>
                {estadoDomicilioNombres[evento.estado]} · {formatDate(evento.ocurridoAt)} ·{' '}
                {evento.autor ? (evento.autor.nombre ?? 'Usuario') : 'Sistema'}
              </Text>
            ))}
          </View>
          {cancelable ? (
            <CancelarDomicilioAdmin
              domicilioId={data.id}
              estado={data.estado}
              conPagoTarifa={data.pagoTarifa !== null}
              onCancelado={() => {
                setChanged(null);
                setNotice('Domicilio cancelado. Avisamos al cliente, al vendedor y al domiciliario si lo había.');
                setAttempt((value) => value + 1);
              }}
              onCambio={(mensaje) => {
                setChanged(mensaje);
                setAttempt((value) => value + 1);
              }}
            />
          ) : null}
          <TextButton title="Actualizar" disabled={loading} onPress={() => setAttempt((value) => value + 1)} />
        </>
      ) : null}
      <TextButton title="Volver a los domicilios" onPress={() => router.replace('/domicilios-admin')} />
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
  cardDelayed: { backgroundColor: '#FFF8E8', borderColor: '#E8C77A' },
  row: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  personRow: { alignItems: 'center', flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between' },
  sectionTitle: { color: palette.dark, fontSize: 16, fontWeight: '800' },
  amount: { color: palette.green, fontSize: 18, fontWeight: '800' },
  detailText: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 5 },
  warning: { color: '#86510F', fontSize: 14, fontWeight: '800', marginTop: 5 },
  success: { color: palette.green, fontSize: 14, fontWeight: '700', marginVertical: 10 },
});
