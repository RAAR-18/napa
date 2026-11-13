import { useEffect, useState } from 'react';
import { router } from 'expo-router';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';

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
import {
  listarPedidosVendedor,
  type EstadoPedido,
  type FiltrosPedidosVendedor,
  type ModalidadPedido,
  type PedidoVendedorListadoItem,
  type PedidosVendedorPagina,
} from '../lib/pedidos';

const sellerRoles = ['VENDEDOR_AMBULANTE', 'VENDEDOR_PUNTO_FIJO'];
const statuses: { value: EstadoPedido | ''; label: string }[] = [
  { value: '', label: 'Todos' },
  { value: 'PENDIENTE', label: 'Pendientes' },
  { value: 'ACEPTADO', label: 'Aceptados' },
  { value: 'RECHAZADO', label: 'Rechazados' },
  { value: 'LISTO_PARA_RECOGER', label: 'Listos para recoger' },
  { value: 'EN_CAMINO', label: 'En camino' },
  { value: 'ENTREGADO', label: 'Entregados' },
  { value: 'CANCELADO', label: 'Cancelados' },
];
const modalities: { value: ModalidadPedido | ''; label: string }[] = [
  { value: '', label: 'Todas' },
  { value: 'ENTREGA_DIRECTA', label: 'Entrega directa' },
  { value: 'RECOGIDA', label: 'Recogida' },
  { value: 'DOMICILIO', label: 'Domicilio' },
];
const reservationOptions: { value: '' | 'true' | 'false'; label: string }[] = [
  { value: '', label: 'Todos' },
  { value: 'true', label: 'Reservas' },
  { value: 'false', label: 'Inmediatos' },
];
const statusNames: Record<EstadoPedido, string> = {
  PENDIENTE: 'Pendiente',
  ACEPTADO: 'Aceptado',
  RECHAZADO: 'Rechazado',
  LISTO_PARA_RECOGER: 'Listo para recoger',
  EN_CAMINO: 'En camino',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
};
const modalityNames: Record<ModalidadPedido, string> = {
  ENTREGA_DIRECTA: 'Entrega directa',
  RECOGIDA: 'Recogida',
  DOMICILIO: 'Domicilio',
};

function formatDate(value: string) {
  return new Date(value).toLocaleString('es-CO', {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
}

function PedidoRow({ item }: { item: PedidoVendedorListadoItem }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`Abrir pedido de ${item.cliente.nombre}, ${statusNames[item.estado]}`}
      onPress={() => router.push({ pathname: '/pedido-vendedor', params: { pedidoId: item.id } })}
      style={({ pressed }) => [styles.card, pressed && styles.pressed]}
    >
      <View style={styles.row}>
        <Text style={styles.customer}>{item.cliente.nombre}</Text>
        <Text style={styles.total}>${item.total.toLocaleString('es-CO')} COP</Text>
      </View>
      <Text style={styles.details}>
        {statusNames[item.estado]} · {modalityNames[item.modalidad]}
      </Text>
      <Text style={styles.details}>Recibido: {formatDate(item.createdAt)}</Text>
      {item.reserva ? (
        <Text style={styles.reservation}>Reserva: {formatDate(item.reserva)}</Text>
      ) : null}
    </Pressable>
  );
}

export default function PedidosVendedorScreen() {
  const [data, setData] = useState<PedidosVendedorPagina | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [page, setPage] = useState(0);
  const [estado, setEstado] = useState<EstadoPedido | ''>('');
  const [modalidad, setModalidad] = useState<ModalidadPedido | ''>('');
  const [reserva, setReserva] = useState<'' | 'true' | 'false'>('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setData(null);
      setError(null);
      try {
        const session = await getValidSession();
        if (!active) return;
        if (!sellerRoles.includes(session.rol)) {
          setError('Solo las cuentas de vendedor pueden consultar los pedidos recibidos.');
          return;
        }
        const filters: FiltrosPedidosVendedor = {
          page,
          size: 20,
          ...(estado ? { estado } : {}),
          ...(modalidad ? { modalidad } : {}),
          ...(reserva ? { reserva: reserva === 'true' } : {}),
        };
        const pageData = await listarPedidosVendedor(filters);
        if (active) setData(pageData);
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 401) {
          await clearSession();
          router.replace('/login');
          return;
        }
        if (active) {
          setError(cause instanceof Error ? cause.message : 'No se pudieron cargar los pedidos.');
        }
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [estado, modalidad, reserva, page, loadAttempt]);

  function changeFilter(change: () => void) {
    setPage(0);
    change();
  }

  function changePage(nextPage: number) {
    if (nextPage >= 0 && data && nextPage * data.size < data.totalElements) {
      setPage(nextPage);
    }
  }

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Tu negocio"
        title="Pedidos recibidos"
        description="Consulta el estado, la modalidad y la fecha acordada de cada pedido."
      />
      <Text style={styles.filterTitle}>Estado</Text>
      <View style={styles.options}>
        {statuses.map((item) => (
          <OptionButton
            key={item.value}
            label={item.label}
            selected={estado === item.value}
            onPress={() => changeFilter(() => setEstado(item.value))}
          />
        ))}
      </View>
      <Text style={styles.filterTitle}>Modalidad</Text>
      <View style={styles.options}>
        {modalities.map((item) => (
          <OptionButton
            key={item.value}
            label={item.label}
            selected={modalidad === item.value}
            onPress={() => changeFilter(() => setModalidad(item.value))}
          />
        ))}
      </View>
      <Text style={styles.filterTitle}>Tipo de pedido</Text>
      <View style={styles.options}>
        {reservationOptions.map((item) => (
          <OptionButton
            key={item.value}
            label={item.label}
            selected={reserva === item.value}
            onPress={() => changeFilter(() => setReserva(item.value))}
          />
        ))}
      </View>
      <ErrorBanner message={error} />
      {loading ? (
        <ActivityIndicator accessibilityLabel="Cargando pedidos" color={palette.green} />
      ) : null}
      {!loading && data?.content.length === 0 ? (
        <HelperText>No hay pedidos que coincidan con estos filtros.</HelperText>
      ) : null}
      {!loading ? data?.content.map((item) => <PedidoRow key={item.id} item={item} />) : null}
      {data && data.totalElements > 0 ? (
        <View style={styles.pagination}>
          <TextButton
            title="Página anterior"
            disabled={loading || page === 0}
            onPress={() => changePage(page - 1)}
          />
          <Text style={styles.pageText}>
            {page + 1} de {Math.ceil(data.totalElements / data.size)}
          </Text>
          <TextButton
            title="Página siguiente"
            disabled={loading || (page + 1) * data.size >= data.totalElements}
            onPress={() => changePage(page + 1)}
          />
        </View>
      ) : null}
      <PrimaryButton
        title="Actualizar pedidos"
        disabled={loading}
        onPress={() => setLoadAttempt((attempt) => attempt + 1)}
      />
      <TextButton title="Volver a mi cuenta" onPress={() => router.replace('/session')} />
    </AuthPage>
  );
}

const styles = StyleSheet.create({
  filterTitle: { color: palette.dark, fontSize: 14, fontWeight: '800', marginTop: 4 },
  options: { flexDirection: 'row', flexWrap: 'wrap', marginBottom: 10 },
  card: {
    backgroundColor: palette.white,
    borderColor: palette.border,
    borderRadius: 16,
    borderWidth: 1,
    marginVertical: 7,
    padding: 16,
  },
  pressed: { opacity: 0.72 },
  row: { alignItems: 'flex-start', flexDirection: 'row', justifyContent: 'space-between' },
  customer: { color: palette.dark, flex: 1, fontSize: 17, fontWeight: '800' },
  total: { color: palette.green, fontWeight: '800', marginLeft: 8 },
  details: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 5 },
  reservation: { color: '#86510F', fontSize: 14, fontWeight: '700', marginTop: 5 },
  pagination: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 10,
  },
  pageText: { color: palette.muted, fontSize: 14 },
});
