import { fonts } from '../components/theme';
import { useCallback, useEffect, useRef, useState } from 'react';
import { router, useFocusEffect } from 'expo-router';
import { ActivityIndicator, AppState, Platform, Pressable, StyleSheet, Text, View } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  HelperText,
  OptionButton,
  PageHeading,
  TextButton,
  palette,
} from '../components/AuthUI';
import { useAccionesPendientes } from '../hooks/useOutbox';
import { paramsRevisionPedido, pedidoPendiente } from '../lib/pedido-offline';
import { ApiError } from '../lib/api';
import { clearSession, getSessionGeneration } from '../lib/auth';
import { escucharNovedades } from '../lib/push';
import { consultarListadoCliente } from '../lib/pedidos-cliente-consulta';
import { FilterPanel, FilterRow, Icon, RefreshButton, StatusBadge } from '../components/NapaUI';
import {
  estadoPedidoNombres,
  modalidadPedidoNombres,
  type EstadoPedido,
  type PedidoClienteListadoItem,
  type PedidosClientePagina,
} from '../lib/pedidos';

const PAGE_SIZE = 20;
export const REFRESCO_MIS_PEDIDOS_MS = 15_000;
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
        <StatusBadge
          label={estadoPedidoNombres[item.estado]}
          tone={
            item.estado === 'PENDIENTE'
              ? 'peach'
              : item.estado === 'RECHAZADO' || item.estado === 'CANCELADO'
                ? 'muted'
                : 'sage'
          }
        />
      </View>
      <Text style={styles.details}>
        {modalidadPedidoNombres[item.modalidad]} · {formatDate(item.createdAt)}
      </Text>
      {item.reserva ? (
        <Text style={styles.reservation}>Reserva: {formatDate(item.reserva)}</Text>
      ) : null}
      <View style={styles.orderFooter}>
        <Text style={styles.total}>{formatPrice(item.total)}</Text>
        <View style={styles.orderLink}>
          <Text style={styles.orderLinkText}>Ver pedido</Text>
          <Icon name="arrow-right" color={palette.coralDark} size={19} />
        </View>
      </View>
    </Pressable>
  );
}

export default function MisPedidosScreen() {
  const acciones = useAccionesPendientes();
  const pendientes = acciones.flatMap((accion) => {
    const pedido = pedidoPendiente(accion);
    return pedido ? [{ accion, pedido }] : [];
  });
  const pendientesActivos = pendientes.filter(({ accion }) => accion.status === 'PENDIENTE').length;
  const anteriores = useRef(pendientesActivos);
  const [data, setData] = useState<PedidosClientePagina | null>(null);
  const [fechaCopia, setFechaCopia] = useState<number | null>(null);
  const [paginasGuardadas, setPaginasGuardadas] = useState<number[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [page, setPage] = useState(0);
  const [estado, setEstado] = useState<EstadoPedido | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (pendientesActivos < anteriores.current) setLoadAttempt((intento) => intento + 1);
    anteriores.current = pendientesActivos;
  }, [pendientesActivos]);

  useFocusEffect(useCallback(() => {
    let active = true;
    const generation = getSessionGeneration();
    const vigente = () => active && generation === getSessionGeneration();
    let consultando = false;
    async function load(silencioso = false) {
      if (consultando || !vigente()) return;
      consultando = true;
      if (!silencioso) {
        setLoading(true);
        setError(null);
        setData(null);
        setFechaCopia(null);
        setPaginasGuardadas([]);
      }
      try {
        const resultado = await consultarListadoCliente(
          estado ?? undefined,
          page,
          PAGE_SIZE,
          vigente,
        );
        if (!vigente() || !resultado) return;
        if (silencioso && resultado.origen !== 'servidor') return;
        if (resultado.origen === 'sin-copia') {
          setPaginasGuardadas(resultado.paginas);
          setError('No hay datos descargados para esta consulta.');
          return;
        }
        setData(resultado.data);
        setFechaCopia(resultado.origen === 'copia' ? resultado.fecha : null);
        setError(null);
        setPaginasGuardadas([]);
      } catch (cause) {
        if (!vigente()) return;
        if (cause instanceof ApiError && cause.status === 401) {
          await clearSession();
          router.replace('/login');
          return;
        }
        if (!silencioso)
          setError(cause instanceof Error ? cause.message : 'No se pudieron cargar tus pedidos.');
      } finally {
        consultando = false;
        if (active && !silencioso) setLoading(false);
      }
    }
    void load();
    const actualizar = () => {
      if (AppState.currentState === 'background' || AppState.currentState === 'inactive') return;
      if (Platform.OS === 'web' && typeof document !== 'undefined' && document.visibilityState === 'hidden') return;
      void load(true);
    };
    const timer = setInterval(actualizar, REFRESCO_MIS_PEDIDOS_MS);
    const subscription = AppState.addEventListener('change', (estado) => {
      if (estado === 'active') actualizar();
    });
    const dejarDeEscuchar = escucharNovedades(actualizar);
    if (Platform.OS === 'web' && typeof window !== 'undefined') window.addEventListener('focus', actualizar);
    return () => {
      active = false;
      clearInterval(timer);
      subscription.remove();
      dejarDeEscuchar();
      if (Platform.OS === 'web' && typeof window !== 'undefined') window.removeEventListener('focus', actualizar);
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps -- loadAttempt invalida el ciclo al actualizar manual o automáticamente.
  }, [estado, page, loadAttempt]));

  const totalPages = data ? Math.ceil(data.totalElements / data.size) : 0;
  const anterior = paginasGuardadas.filter((numero) => numero < page).at(-1);
  const siguiente = paginasGuardadas.find((numero) => numero > page);

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Cliente"
        title="Mis pedidos"
        description="Del más reciente al más antiguo. Toca uno para ver su estado."
        action={<RefreshButton disabled={loading} onPress={() => setLoadAttempt((a) => a + 1)} />}
      />
      {pendientes.map(({ accion, pedido }) => (
        <View key={accion.id} style={styles.card}>
          <Text style={styles.details}>{accion.descripcion} · {modalidadPedidoNombres[pedido.modalidad]}</Text>
          <Text style={styles.details}>{accion.status === 'FALLIDA' ? 'Requiere revisión' : 'Pendiente de envío'}</Text>
          {accion.status === 'FALLIDA' ? (
            <>
              <HelperText>{accion.lastError ?? 'El servidor rechazó el pedido.'}</HelperText>
              <TextButton title="Revisar pedido" onPress={() => router.push({
                pathname: '/catalogo', params: paramsRevisionPedido(accion, pedido),
              })} />
            </>
          ) : null}
        </View>
      ))}
      <FilterPanel>
        <FilterRow label="Estado">
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
        </FilterRow>
      </FilterPanel>
      <ErrorBanner message={error} />
      {fechaCopia !== null ? (
        <HelperText>
          Datos guardados. Última descarga: {formatDate(new Date(fechaCopia).toISOString())}. El
          estado de los pedidos puede haber cambiado. Solo se muestran las páginas descargadas.
        </HelperText>
      ) : null}
      {loading ? (
        <ActivityIndicator accessibilityLabel="Cargando pedidos" color={palette.green} />
      ) : null}
      {!loading && data?.content.length === 0 ? (
        <HelperText>
          {fechaCopia !== null
            ? 'No hay pedidos para este filtro en la página guardada.'
            : estado
              ? 'No tienes pedidos con ese estado.'
              : 'Aún no has hecho pedidos. Explora los emprendimientos.'}
        </HelperText>
      ) : null}
      {!loading ? data?.content.map((item) => <PedidoRow key={item.id} item={item} />) : null}
      {data && totalPages > 1 ? (
        <View style={styles.pagination}>
          <TextButton
            title="Página anterior"
            disabled={page === 0}
            onPress={() => setPage(page - 1)}
          />
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
      {!loading && !data && anterior !== undefined ? (
        <TextButton title="Página guardada anterior" onPress={() => setPage(anterior)} />
      ) : null}
      {!loading && !data && siguiente !== undefined ? (
        <TextButton title="Página guardada siguiente" onPress={() => setPage(siguiente)} />
      ) : null}
    </AuthPage>
  );
}

const styles = StyleSheet.create({
  filterTitle: {
    color: palette.dark,
    fontFamily: fonts.sans,
    fontSize: 14,
    fontWeight: '800',
    marginTop: 4,
  },
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
  orderFooter: {
    flexWrap: 'wrap',
    gap: 10,
    borderTopWidth: 1,
    borderColor: palette.border,
    paddingTop: 14,
    marginTop: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  orderLink: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  orderLinkText: {
    fontFamily: fonts.sans,
    color: palette.coralDark,
    fontWeight: '700',
    fontSize: 13,
  },
  row: { alignItems: 'flex-start', flexDirection: 'row', justifyContent: 'space-between' },
  name: {
    color: palette.dark,
    flex: 1,
    fontFamily: fonts.display,
    fontSize: 24,
    fontWeight: '700',
  },
  status: { color: palette.green, fontWeight: '700', marginLeft: 8 },
  details: {
    color: palette.muted,
    fontFamily: fonts.sans,
    fontSize: 14,
    lineHeight: 21,
    marginTop: 5,
  },
  reservation: { color: palette.dark, fontFamily: fonts.sans, fontSize: 14, marginTop: 5 },
  total: { color: palette.dark, fontWeight: '800', marginTop: 8 },
  pagination: {
    alignItems: 'center',
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    justifyContent: 'space-between',
    marginTop: 10,
  },
  pageText: { color: palette.muted, fontFamily: fonts.sans, fontSize: 14 },
});
