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
  estadoPedidoNombres,
  listarPedidosCliente,
  modalidadPedidoNombres,
  type EstadoPedido,
  type PedidoClienteListadoItem,
  type PedidosClientePagina,
} from '../lib/pedidos';

const PAGE_SIZE = 20;
const filtrosEstado: { value: EstadoPedido | null; label: string }[] = [
  { value: null, label: 'Todos' },
  { value: 'PENDIENTE', label: 'Pendientes' },
  { value: 'ACEPTADO', label: 'Aceptados' },
  { value: 'EN_CAMINO', label: 'En camino' },
  { value: 'ENTREGADO', label: 'Entregados' },
  { value: 'CANCELADO', label: 'Cancelados' },
  { value: 'RECHAZADO', label: 'Rechazados' },
];

function formatDate(value: string) {
  return new Date(value).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
}

function formatPrice(value: number) {
  return `$${value.toLocaleString('es-CO')} COP`;
}

function PedidoRow({ item }: { item: PedidoClienteListadoItem }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`Ver pedido a ${item.emprendimiento.nombre}, ${estadoPedidoNombres[item.estado]}`}
      onPress={() => router.push({ pathname: '/pedido-cliente', params: { pedidoId: item.id } })}
      style={({ pressed }) => [styles.card, pressed && styles.pressed]}
    >
      <View style={styles.row}>
        <Text style={styles.name}>{item.emprendimiento.nombre}</Text>
        <Text style={styles.status}>{estadoPedidoNombres[item.estado]}</Text>
      </View>
      <Text style={styles.details}>
        {modalidadPedidoNombres[item.modalidad]} · {formatDate(item.createdAt)}
      </Text>
      {item.reserva ? <Text style={styles.reservation}>Reserva: {formatDate(item.reserva)}</Text> : null}
      <Text style={styles.total}>{formatPrice(item.total)}</Text>
    </Pressable>
  );
}

export default function MisPedidosScreen() {
  const [data, setData] = useState<PedidosClientePagina | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [page, setPage] = useState(0);
  const [estado, setEstado] = useState<EstadoPedido | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const session = await getValidSession();
        if (!active) return;
        if (session.rol !== 'CLIENTE') {
          setError('Solo las cuentas de cliente tienen pedidos realizados.');
          return;
        }
        const response = await listarPedidosCliente({ estado: estado ?? undefined, page, size: PAGE_SIZE });
        if (active) setData(response);
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 401) {
          await clearSession();
          router.replace('/login');
          return;
        }
        if (active) setError(cause instanceof Error ? cause.message : 'No se pudieron cargar tus pedidos.');
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [estado, page, loadAttempt]);

  const totalPages = data ? Math.ceil(data.totalElements / data.size) : 0;

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Cliente"
        title="Mis pedidos"
        description="Del más reciente al más antiguo. Toca uno para ver su estado."
      />
      <Text style={styles.filterTitle}>Estado</Text>
      <View style={styles.options}>
        {filtrosEstado.map((option) => (
          <OptionButton
            key={option.label}
            label={option.label}
            selected={estado === option.value}
            onPress={() => {
              setPage(0);
              setEstado(option.value);
            }}
          />
        ))}
      </View>
      <ErrorBanner message={error} />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando pedidos" color={palette.green} /> : null}
      {!loading && data?.content.length === 0 ? (
        <HelperText>
          {estado ? 'No tienes pedidos con ese estado.' : 'Aún no has hecho pedidos. Explora los emprendimientos.'}
        </HelperText>
      ) : null}
      {!loading ? data?.content.map((item) => <PedidoRow key={item.id} item={item} />) : null}
      {data && totalPages > 1 ? (
        <View style={styles.pagination}>
          <TextButton title="Página anterior" disabled={page === 0} onPress={() => setPage(page - 1)} />
          <Text style={styles.pageText}>
            {page + 1} de {totalPages}
          </Text>
          <TextButton
            title="Página siguiente"
            disabled={page + 1 >= totalPages}
            onPress={() => setPage(page + 1)}
          />
        </View>
      ) : null}
      <PrimaryButton title="Actualizar" disabled={loading} onPress={() => setLoadAttempt((a) => a + 1)} />
      <TextButton title="Explorar emprendimientos" onPress={() => router.push('/emprendimientos')} />
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
  name: { color: palette.dark, flex: 1, fontSize: 16, fontWeight: '800' },
  status: { color: palette.green, fontWeight: '700', marginLeft: 8 },
  details: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 5 },
  reservation: { color: palette.dark, fontSize: 14, marginTop: 5 },
  total: { color: palette.dark, fontWeight: '800', marginTop: 8 },
  pagination: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 10,
  },
  pageText: { color: palette.muted, fontSize: 14 },
});
