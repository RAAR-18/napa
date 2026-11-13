import { useEffect, useState } from 'react';
import { router } from 'expo-router';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  HelperText,
  OptionButton,
  PageHeading,
  PrimaryButton,
  TextButton,
  palette,
} from '../components/AuthUI';
import { ApiError } from '../lib/api';
import { clearSession, getValidSession } from '../lib/auth';
import { modalidadPedidoNombres } from '../lib/pedidos';
import {
  estadoPagoNombres,
  listarPagos,
  medioPagoNombres,
  tipoPagoNombres,
  type EstadoPago,
  type HistorialPagosPagina,
  type MedioPago,
  type PagoHistorial,
  type TipoPago,
} from '../lib/pagos';

const PAGE_SIZE = 20;
const precio = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });

const medios: { value: MedioPago | undefined; label: string }[] = [
  { value: undefined, label: 'Todos los medios' },
  { value: 'EFECTIVO', label: 'Efectivo' },
  { value: 'CUENTA_BANCARIA', label: 'Cuenta bancaria' },
];
const estados: { value: EstadoPago | undefined; label: string }[] = [
  { value: undefined, label: 'Todos los estados' },
  { value: 'PENDIENTE_CONFIRMACION', label: 'Pendientes' },
  { value: 'REGISTRADO', label: 'Registrados' },
  { value: 'CONFIRMADO', label: 'Confirmados' },
];
const tipos: { value: TipoPago | undefined; label: string }[] = [
  { value: undefined, label: 'Todos los tipos' },
  { value: 'PEDIDO', label: 'Pedidos' },
  { value: 'TARIFA_DOMICILIO', label: 'Tarifas de domicilio' },
];

function formatDate(value: string) {
  return new Date(value).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
}

/** RF-40 (cada usuario ve lo suyo) y RF-41 (el administrador ve todo, con los usuarios involucrados). */
export default function HistorialPagosScreen() {
  const [role, setRole] = useState<string | null>(null);
  const [data, setData] = useState<HistorialPagosPagina | null>(null);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [medio, setMedio] = useState<MedioPago | undefined>(undefined);
  const [estado, setEstado] = useState<EstadoPago | undefined>(undefined);
  const [tipo, setTipo] = useState<TipoPago | undefined>(undefined);
  const [page, setPage] = useState(0);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const session = await getValidSession();
        if (!active) return;
        setRole(session.rol);
        const response = await listarPagos({ medio, estado, tipo, page, size: PAGE_SIZE });
        if (active) setData(response);
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 401) {
          await clearSession();
          router.replace('/login');
          return;
        }
        if (active) setError(cause instanceof Error ? cause.message : 'No se pudo cargar el historial.');
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [medio, estado, tipo, page, attempt]);

  const admin = role === 'ADMINISTRADOR';
  const showTipo = admin || role === 'VENDEDOR_PUNTO_FIJO';
  const filtered = Boolean(medio || estado || tipo);
  const lastPage = data ? Math.max(0, Math.ceil(data.totalElements / PAGE_SIZE) - 1) : 0;

  function choose<T>(setter: (value: T) => void, value: T) {
    setPage(0);
    setter(value);
  }

  return (
    <AuthPage>
      <PageHeading
        eyebrow={admin ? 'Administración' : 'Mis pagos'}
        title={admin ? 'Historial general de pagos' : 'Historial de pagos'}
        description="Ñapa solo lleva el registro: los pagos se hacen directamente entre las personas."
      />
      <View style={styles.options}>
        {medios.map((option) => (
          <OptionButton
            key={option.label}
            label={option.label}
            selected={medio === option.value}
            onPress={() => choose(setMedio, option.value)}
          />
        ))}
      </View>
      <View style={styles.options}>
        {estados.map((option) => (
          <OptionButton
            key={option.label}
            label={option.label}
            selected={estado === option.value}
            onPress={() => choose(setEstado, option.value)}
          />
        ))}
      </View>
      {showTipo ? (
        <View style={styles.options}>
          {tipos.map((option) => (
            <OptionButton
              key={option.label}
              label={option.label}
              selected={tipo === option.value}
              onPress={() => choose(setTipo, option.value)}
            />
          ))}
        </View>
      ) : null}
      {loading ? <ActivityIndicator accessibilityLabel="Cargando pagos" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {error ? <PrimaryButton title="Reintentar" onPress={() => setAttempt((value) => value + 1)} /> : null}
      {!loading && data?.content.length === 0 ? (
        <HelperText>{filtered ? 'No hay pagos con esos filtros.' : 'Aún no tienes pagos registrados.'}</HelperText>
      ) : null}
      {data?.content.map((pago) => <FilaPago key={pago.id} pago={pago} admin={admin} />)}
      {data && data.totalElements > PAGE_SIZE ? (
        <View style={styles.pager}>
          <TextButton title="Anterior" disabled={page === 0 || loading} onPress={() => setPage(page - 1)} />
          <Text style={styles.detailText}>
            Página {page + 1} de {lastPage + 1}
          </Text>
          <TextButton title="Siguiente" disabled={page >= lastPage || loading} onPress={() => setPage(page + 1)} />
        </View>
      ) : null}
      <TextButton title="Volver" onPress={() => router.replace('/session')} />
    </AuthPage>
  );
}

function FilaPago({ pago, admin }: { pago: PagoHistorial; admin: boolean }) {
  return (
    <View style={styles.card}>
      <View style={styles.row}>
        <Text style={styles.sectionTitle}>{tipoPagoNombres[pago.tipo]}</Text>
        <Text style={styles.amount}>{precio.format(pago.monto)}</Text>
      </View>
      <Text style={styles.detailText}>
        {medioPagoNombres[pago.medio]} · {estadoPagoNombres[pago.estado]}
      </Text>
      <Text style={styles.detailText}>{formatDate(pago.createdAt)}</Text>
      {pago.confirmadoAt ? <Text style={styles.detailText}>Confirmado: {formatDate(pago.confirmadoAt)}</Text> : null}
      {pago.modalidad ? (
        <Text style={styles.detailText}>Modalidad: {modalidadPedidoNombres[pago.modalidad]}</Text>
      ) : null}
      {admin ? (
        <>
          {pago.comprador ? <Text style={styles.detailText}>Comprador: {pago.comprador.nombre ?? '—'}</Text> : null}
          {pago.vendedor ? <Text style={styles.detailText}>Vendedor: {pago.vendedor.nombre ?? '—'}</Text> : null}
          {pago.domiciliario ? (
            <Text style={styles.detailText}>Domiciliario: {pago.domiciliario.nombre ?? '—'}</Text>
          ) : null}
        </>
      ) : null}
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
  row: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  sectionTitle: { color: palette.dark, fontSize: 16, fontWeight: '800' },
  amount: { color: palette.green, fontSize: 16, fontWeight: '800' },
  detailText: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 4 },
  options: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginVertical: 6 },
  pager: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between', marginVertical: 8 },
});
