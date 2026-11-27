import { useAutoRefresh } from '../hooks/useAutoRefresh';
import { useEffect, useState } from 'react';
import { router } from 'expo-router';
import { ActivityIndicator, Image, Pressable, StyleSheet, Text, View } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  HelperText,
  OptionButton,
  PageHeading,
  TextButton,
  palette,
} from '../components/AuthUI';
import {
  DetailLine,
  EmptyState,
  FilterPanel,
  FilterRow,
  Icon,
  RefreshButton,
  StatusBadge,
  useContentWidth,
} from '../components/NapaUI';
import { art, fonts } from '../components/theme';
import { FilterSelect } from '../components/FilterSelect';
import { guardarConsultaPedidos, leerConsultaPedidos } from '../lib/pedidos-cliente-cache';
import { ApiError, NetworkError } from '../lib/api';
import { clearSession, getValidSession, readSession } from '../lib/auth';
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
  const wide = useContentWidth() >= 950;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`Abrir pedido de ${item.cliente.nombre}, ${statusNames[item.estado]}`}
      onPress={() => router.push({ pathname: '/pedido-vendedor', params: { pedidoId: item.id } })}
      style={({ pressed, hovered }: { pressed: boolean; hovered?: boolean }) => [
        styles.card,
        wide && styles.cardWide,
        hovered && { borderColor: '#BCA08F' },
        pressed && styles.pressed,
      ]}
    >
      <View style={[styles.personBlock, wide && { flex: 1.1 }]}>
        <Image
          source={item.modalidad === 'DOMICILIO' ? art.paquete : art.comida}
          resizeMode="cover"
          accessible={false}
          style={styles.orderArt}
        />
        <View style={{ flex: 1 }}>
          <Text style={styles.customer}>{item.cliente.nombre}</Text>
          <StatusBadge
            label={statusNames[item.estado]}
            tone={
              item.estado === 'PENDIENTE'
                ? 'peach'
                : item.estado === 'RECHAZADO' || item.estado === 'CANCELADO'
                  ? 'muted'
                  : 'sage'
            }
          />
        </View>
      </View>
      <View style={[styles.orderDetails, wide && styles.orderDetailsWide]}>
        <DetailLine icon="map-pin">
          {modalityNames[item.modalidad]} · {item.reserva ? 'Reserva' : 'Inmediato'}
        </DetailLine>
        <DetailLine icon="calendar">Recibido: {formatDate(item.createdAt)}</DetailLine>
        {item.reserva ? (
          <DetailLine icon="clock">Reserva: {formatDate(item.reserva)}</DetailLine>
        ) : null}
      </View>
      <View
        style={[styles.orderAction, wide && { flexDirection: 'column', alignItems: 'flex-end' }]}
      >
        <Text style={styles.total}>${item.total.toLocaleString('es-CO')} COP</Text>
        <View style={styles.viewOrder}>
          <Text style={styles.viewOrderText}>Ver pedido</Text>
          <Icon name="arrow-right" color="#FFFFFF" size={19} />
        </View>
      </View>
    </Pressable>
  );
}

export default function PedidosVendedorScreen() {
  const [data, setData] = useState<PedidosVendedorPagina | null>(null);
  const [fechaGuardada, setFechaGuardada] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadAttempt, setLoadAttempt] = useState(0);
  useAutoRefresh(() => setLoadAttempt((value) => value + 1), !loading);
  const [page, setPage] = useState(0);
  const [estado, setEstado] = useState<EstadoPedido | ''>('');
  const [modalidad, setModalidad] = useState<ModalidadPedido | ''>('');
  const [reserva, setReserva] = useState<'' | 'true' | 'false'>('');
  const [openFilter, setOpenFilter] = useState<'estado' | 'modalidad' | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setData(null);
      setError(null);
      setFechaGuardada(null);
      const clave = JSON.stringify([page, estado, modalidad, reserva]);
      let local: Awaited<ReturnType<typeof readSession>> = null;
      try {
        local = typeof readSession === 'function' ? await readSession() : null;
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
        if (!active) return;
        setData(pageData);
        if (session.usuarioId) {
          void guardarConsultaPedidos(session.usuarioId, 'listado-vendedor', clave, pageData, () => active)
            .catch(() => undefined);
        }
      } catch (cause) {
        if (cause instanceof NetworkError && local?.usuarioId && sellerRoles.includes(local.rol)) {
          const copia = await leerConsultaPedidos<PedidosVendedorPagina>(
            local.usuarioId, 'listado-vendedor', clave,
          ).catch(() => null);
          const vigente = await readSession();
          if (!active || vigente?.usuarioId !== local.usuarioId) return;
          if (copia) {
            setData(copia.data);
            setFechaGuardada(copia.fecha);
          } else {
            setError('No hay una página de pedidos guardada con estos filtros.');
          }
          return;
        }
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
        action={
          <RefreshButton
            label="Actualizar pedidos"
            disabled={loading}
            onPress={() => setLoadAttempt((attempt) => attempt + 1)}
          />
        }
      />
      <FilterPanel>
        <FilterRow label="Estado">
          <FilterSelect
            label="Estado"
            value={estado}
            options={statuses}
            expanded={openFilter === 'estado'}
            onToggle={() => setOpenFilter((current) => current === 'estado' ? null : 'estado')}
            onSelect={(value) => { changeFilter(() => setEstado(value)); setOpenFilter(null); }}
          />
        </FilterRow>
        <FilterRow label="Modalidad">
          <FilterSelect
            label="Modalidad"
            value={modalidad}
            options={modalities}
            expanded={openFilter === 'modalidad'}
            onToggle={() => setOpenFilter((current) => current === 'modalidad' ? null : 'modalidad')}
            onSelect={(value) => { changeFilter(() => setModalidad(value)); setOpenFilter(null); }}
          />
        </FilterRow>
        <FilterRow label="Tipo de pedido">
          {reservationOptions.map((item) => (
            <OptionButton
              key={item.value}
              label={item.label}
              selected={reserva === item.value}
              onPress={() => changeFilter(() => setReserva(item.value))}
            />
          ))}
        </FilterRow>
      </FilterPanel>
      <ErrorBanner message={error} />
      {fechaGuardada !== null ? (
        <HelperText>Datos guardados. Última actualización {formatDate(new Date(fechaGuardada).toISOString())}.</HelperText>
      ) : null}
      {loading ? (
        <ActivityIndicator accessibilityLabel="Cargando pedidos" color={palette.green} />
      ) : null}
      {!loading && data?.content.length === 0 ? (
        <EmptyState
          title="No hay pedidos que coincidan con estos filtros."
          description="Cuando llegue un nuevo pedido, podrás gestionarlo desde aquí."
          icon="shopping-bag"
        />
      ) : null}
      {!loading ? data?.content.map((item) => <PedidoRow key={item.id} item={item} />) : null}
      {data && data.totalElements > 0 ? (
        <View style={styles.pagination}>
          <TextButton icon="chevron-left"
            title="Página anterior"
            disabled={loading || page === 0}
            onPress={() => changePage(page - 1)}
          />
          <Text style={styles.pageText}>
            {page + 1} de {Math.ceil(data.totalElements / data.size)}
          </Text>
          <TextButton icon="chevron-right"
            title="Página siguiente"
            disabled={loading || (page + 1) * data.size >= data.totalElements}
            onPress={() => changePage(page + 1)}
          />
        </View>
      ) : null}
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
    padding: 18,
    gap: 15,
  },
  cardWide: { flexDirection: 'row', alignItems: 'center', paddingVertical: 15 },
  personBlock: { flexDirection: 'row', alignItems: 'center', gap: 15 },
  orderArt: { width: 74, height: 78, borderRadius: 12, backgroundColor: palette.peach },
  orderDetails: { paddingVertical: 3 },
  orderDetailsWide: {
    flex: 1.15,
    borderLeftWidth: 1,
    borderColor: palette.border,
    paddingLeft: 20,
  },
  orderAction: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: 12,
  },
  viewOrder: {
    borderRadius: 13,
    backgroundColor: palette.coral,
    paddingHorizontal: 16,
    paddingVertical: 10,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  viewOrderText: { fontFamily: fonts.sans, color: '#FFFFFF', fontSize: 13, fontWeight: '700' },
  pressed: { opacity: 0.72 },
  row: { alignItems: 'flex-start', flexDirection: 'row', justifyContent: 'space-between' },
  customer: {
    color: palette.dark,
    fontFamily: fonts.display,
    fontSize: 23,
    fontWeight: '700',
    marginBottom: 8,
  },
  total: { color: palette.dark, fontFamily: fonts.display, fontSize: 21, fontWeight: '700' },
  details: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 5 },
  reservation: { color: '#86510F', fontSize: 14, fontWeight: '700', marginTop: 5 },
  pagination: {
    alignItems: 'center',
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    justifyContent: 'space-between',
    marginTop: 10,
  },
  pageText: { color: palette.muted, fontSize: 14 },
});
