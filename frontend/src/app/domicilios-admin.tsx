import { useEffect, useState } from 'react';
import { DateTimeField } from '../components/DateTimeField';
import { router, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, Image, Pressable, StyleSheet, Text, View } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  HelperText,
  PageHeading,
  PrimaryButton,
  TextButton,
  palette,
} from '../components/AuthUI';
import {
  DetailLine,
  FilterPanel,
  FilterRow,
  Icon,
  RefreshButton,
  StatusBadge,
  useContentWidth,
} from '../components/NapaUI';
import { art, fonts } from '../components/theme';
import { FilterSelect } from '../components/FilterSelect';
import { ApiError } from '../lib/api';
import { clearSession, getValidSession } from '../lib/auth';
import {
  MINUTOS_SIN_TOMAR_RESALTE,
  estadoDomicilioNombres,
  listarDomiciliosAdmin,
  motivoCancelacionNombres,
  textoSinTomar,
  type DomicilioAdminFila,
  type DomiciliosAdminPagina,
  type EstadoDomicilio,
  type FiltrosDomiciliosAdmin,
} from '../lib/domicilios';

const PAGE_SIZE = 20;
const statuses: { value: EstadoDomicilio | ''; label: string }[] = [
  { value: '', label: 'Todos' },
  { value: 'DISPONIBLE', label: 'Disponibles' },
  { value: 'ASIGNADO', label: 'Asignados' },
  { value: 'RECOGIDO', label: 'Recogidos' },
  { value: 'EN_CAMINO', label: 'En camino' },
  { value: 'ENTREGADO', label: 'Entregados' },
  { value: 'CANCELADO', label: 'Cancelados' },
];
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

function single(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
}

function validDate(value: string) {
  return DATE_PATTERN.test(value) && !Number.isNaN(new Date(`${value}T00:00:00`).getTime());
}

function FilaDomicilio({ item }: { item: DomicilioAdminFila }) {
  const wide = useContentWidth() >= 850;
  const estado = estadoDomicilioNombres[item.estado];
  const demorado =
    item.minutosSinTomar !== null && item.minutosSinTomar >= MINUTOS_SIN_TOMAR_RESALTE;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`Ver domicilio ${estado} de ${item.vendedor.nombre ?? 'vendedor'}`}
      onPress={() =>
        router.push({ pathname: '/domicilio-admin', params: { domicilioId: item.id } })
      }
      style={({ pressed, hovered }: { pressed: boolean; hovered?: boolean }) => [
        styles.card,
        wide && styles.cardWide,
        demorado && styles.cardDelayed,
        hovered && { borderColor: '#BDA28F' },
        pressed && styles.pressed,
      ]}
    >
      {wide ? (
        <Image
          source={item.estado === 'EN_CAMINO' ? art.domiciliario : art.paquete}
          resizeMode="contain"
          accessible={false}
          style={styles.deliveryArt}
        />
      ) : null}
      <View style={{ flex: 1 }}>
        <View style={styles.row}>
          <StatusBadge
            label={estado}
            tone={
              item.estado === 'CANCELADO'
                ? 'muted'
                : item.estado === 'ASIGNADO'
                  ? 'lavender'
                  : 'sage'
            }
          />
          {demorado ? (
            <View style={styles.delayedBadge}>
              <Icon name="alert-triangle" color={palette.coralDark} size={16} />
              <Text style={styles.delayed}>{textoSinTomar(item.minutosSinTomar!)}</Text>
            </View>
          ) : null}
          <Text style={styles.fee}>${item.tarifa.toLocaleString('es-CO')} COP</Text>
        </View>
        {item.minutosSinTomar !== null && !demorado ? (
          <Text style={styles.details}>{textoSinTomar(item.minutosSinTomar)}</Text>
        ) : null}
        <View style={[styles.deliveryInfo, wide && { flexDirection: 'row' }]}>
          <View style={{ flex: 1 }}>
            <DetailLine icon="map-pin">De: {item.origen.direccion ?? 'Sin dirección'}</DetailLine>
            <DetailLine icon="navigation">
              A: {item.destino.direccion ?? 'Sin dirección'}
              {item.destino.barrio ? ` (${item.destino.barrio})` : ''}
            </DetailLine>
          </View>
          <View style={[styles.people, wide && styles.peopleWide]}>
            <Text style={styles.details}>Vendedor: {item.vendedor.nombre ?? '—'}</Text>
            <Text style={styles.details}>Cliente: {item.cliente.nombre ?? '—'}</Text>
            {item.domiciliario ? (
              <Text style={styles.details}>Domiciliario: {item.domiciliario.nombre ?? '—'}</Text>
            ) : null}
            {item.motivoCancelacion ? (
              <Text style={styles.cancelled}>
                {motivoCancelacionNombres[item.motivoCancelacion]}
              </Text>
            ) : null}
            <Text style={styles.details}>Publicado: {formatDate(item.createdAt)}</Text>
          </View>
        </View>
        <View style={styles.viewDelivery}>
          <Text style={styles.viewDeliveryText}>Ver domicilio</Text>
          <Icon name="arrow-right" color="#FFFFFF" size={20} />
        </View>
      </View>
    </Pressable>
  );
}

/** RF-12: el administrador supervisa todos los domicilios de la plataforma. Exige conexión. */
export default function DomiciliosAdminScreen() {
  const wide = useContentWidth() > 750;
  const params = useLocalSearchParams<{
    vendedorId?: string | string[];
    domiciliarioId?: string | string[];
    persona?: string | string[];
  }>();
  const [vendedorId, setVendedorId] = useState(single(params.vendedorId) ?? '');
  const [domiciliarioId, setDomiciliarioId] = useState(single(params.domiciliarioId) ?? '');
  const persona = single(params.persona);
  const [data, setData] = useState<DomiciliosAdminPagina | null>(null);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  const [page, setPage] = useState(0);
  const [estado, setEstado] = useState<EstadoDomicilio | ''>('');
  const [estadoAbierto, setEstadoAbierto] = useState(false);
  const [desdeInput, setDesdeInput] = useState('');
  const [hastaInput, setHastaInput] = useState('');
  const [desde, setDesde] = useState('');
  const [hasta, setHasta] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [dateError, setDateError] = useState<string | null>(null);
  const filtered = Boolean(estado || desde || hasta || vendedorId || domiciliarioId);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const session = await getValidSession();
        if (!active) return;
        if (session.rol !== 'ADMINISTRADOR') {
          setError('Solo el administrador supervisa todos los domicilios.');
          setData(null);
          return;
        }
        const filters: FiltrosDomiciliosAdmin = {
          page,
          size: PAGE_SIZE,
          ...(estado ? { estado } : {}),
          ...(desde ? { desde } : {}),
          ...(hasta ? { hasta } : {}),
          ...(vendedorId ? { vendedorId } : {}),
          ...(domiciliarioId ? { domiciliarioId } : {}),
        };
        const response = await listarDomiciliosAdmin(filters);
        if (active) setData(response);
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 401) {
          await clearSession();
          router.replace('/login');
          return;
        }
        if (active)
          setError(
            cause instanceof Error ? cause.message : 'No se pudieron cargar los domicilios.',
          );
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [estado, desde, hasta, vendedorId, domiciliarioId, page, attempt]);

  function applyDates() {
    const from = desdeInput.trim();
    const until = hastaInput.trim();
    if ((from && !validDate(from)) || (until && !validDate(until))) {
      setDateError('Escribe las fechas con el formato AAAA-MM-DD.');
      return;
    }
    if (from && until && from > until) {
      setDateError('La fecha inicial no puede ser posterior a la final.');
      return;
    }
    setDateError(null);
    setPage(0);
    setDesde(from);
    setHasta(until);
  }

  function clearFilters() {
    setPage(0);
    setEstado('');
    setEstadoAbierto(false);
    setDesdeInput('');
    setHastaInput('');
    setDesde('');
    setHasta('');
    setVendedorId('');
    setDomiciliarioId('');
    setDateError(null);
  }

  const lastPage = data ? Math.max(0, Math.ceil(data.totalElements / PAGE_SIZE) - 1) : 0;

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Administración"
        title="Supervisar domicilios"
        description="Todos los domicilios de la plataforma, en cualquier estado."
        action={
          <RefreshButton disabled={loading} onPress={() => setAttempt((value) => value + 1)} />
        }
      />
      {vendedorId || domiciliarioId ? (
        <View style={styles.personFilter}>
          <Text style={styles.details}>
            {vendedorId
              ? 'Solo los domicilios del vendedor'
              : 'Solo los domicilios del domiciliario'}
            {persona ? ` ${persona}` : ''}.
          </Text>
          <TextButton
            title="Ver todos"
            onPress={() => {
              setPage(0);
              setVendedorId('');
              setDomiciliarioId('');
            }}
          />
        </View>
      ) : null}
      <FilterPanel>
        <FilterRow label="Estado">
          <FilterSelect
            label="Estado"
            value={estado}
            options={statuses}
            expanded={estadoAbierto}
            onToggle={() => setEstadoAbierto((abierto) => !abierto)}
            onSelect={(value) => {
              setPage(0);
              setEstado(value);
              setEstadoAbierto(false);
            }}
          />
        </FilterRow>
        <View
          style={[styles.dateFilters, wide && { flexDirection: 'row', alignItems: 'flex-end' }]}
        >
          <View style={{ flex: 1 }}>
            <DateTimeField
              label="Desde"
              mode="date"
              value={desdeInput}
              onChangeText={setDesdeInput}
            />
          </View>
          <View style={{ flex: 1 }}>
            <DateTimeField
              label="Hasta"
              mode="date"
              value={hastaInput}
              onChangeText={setHastaInput}
            />
          </View>
          <View style={{ marginBottom: 18 }}>
            <PrimaryButton title="Aplicar fechas" disabled={loading} onPress={applyDates} />
          </View>
        </View>
        <ErrorBanner message={dateError} />
      </FilterPanel>
      <ErrorBanner message={error} />
      {error ? (
        <PrimaryButton title="Reintentar" onPress={() => setAttempt((value) => value + 1)} />
      ) : null}
      {loading ? (
        <ActivityIndicator accessibilityLabel="Cargando domicilios" color={palette.green} />
      ) : null}
      {!loading && data?.content.length === 0 ? (
        filtered ? (
          <>
            <HelperText>No hay domicilios con esos filtros.</HelperText>
            <TextButton icon="x" title="Limpiar filtros" onPress={clearFilters} />
          </>
        ) : (
          <HelperText>No hay domicilios registrados.</HelperText>
        )
      ) : null}
      {data?.content.map((item) => (
        <FilaDomicilio key={item.id} item={item} />
      ))}
      {data && data.totalElements > PAGE_SIZE ? (
        <View style={styles.pagination}>
          <TextButton icon="chevron-left"
            title="Anterior"
            disabled={loading || page === 0}
            onPress={() => setPage(page - 1)}
          />
          <Text style={styles.pageText}>
            Página {page + 1} de {lastPage + 1}
          </Text>
          <TextButton icon="chevron-right"
            title="Siguiente"
            disabled={loading || page >= lastPage}
            onPress={() => setPage(page + 1)}
          />
        </View>
      ) : null}
    </AuthPage>
  );
}

const styles = StyleSheet.create({
  filterTitle: { color: palette.dark, fontSize: 14, fontWeight: '800', marginTop: 4 },
  options: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 10 },
  personFilter: {
    backgroundColor: '#F7EBDD',
    borderRadius: 12,
    marginBottom: 8,
    padding: 12,
  },
  card: {
    backgroundColor: palette.white,
    borderColor: palette.border,
    borderRadius: 16,
    borderWidth: 1,
    marginVertical: 7,
    padding: 21,
    gap: 20,
  },
  cardWide: { flexDirection: 'row', alignItems: 'center' },
  deliveryArt: { width: 135, height: 140 },
  deliveryInfo: { gap: 16, marginTop: 11 },
  people: { flex: 1 },
  peopleWide: { borderLeftWidth: 1, borderLeftColor: palette.border, paddingLeft: 24 },
  dateFilters: { gap: 18, paddingTop: 10, borderTopWidth: 1, borderColor: palette.border },
  delayedBadge: {
    backgroundColor: '#FFDCC5',
    borderRadius: 20,
    paddingHorizontal: 11,
    paddingVertical: 5,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  viewDelivery: {
    alignSelf: 'flex-end',
    backgroundColor: palette.coral,
    borderRadius: 14,
    paddingHorizontal: 20,
    paddingVertical: 11,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    marginTop: 16,
  },
  viewDeliveryText: { fontFamily: fonts.sans, color: '#FFFFFF', fontWeight: '700', fontSize: 14 },
  cardDelayed: {
    backgroundColor: '#FFF0E3',
    borderColor: '#F2BFA0',
    borderLeftWidth: 7,
    borderLeftColor: palette.coral,
  },
  pressed: { opacity: 0.72 },
  row: { alignItems: 'center', flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  status: { color: palette.dark, flex: 1, fontSize: 17, fontWeight: '800' },
  fee: {
    color: palette.dark,
    fontFamily: fonts.display,
    fontSize: 22,
    fontWeight: '700',
    marginLeft: 'auto',
    backgroundColor: '#F7E4D2',
    paddingHorizontal: 14,
    paddingVertical: 5,
    borderRadius: 16,
  },
  details: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 5 },
  delayed: { color: palette.coralDark, fontFamily: fonts.sans, fontSize: 12, fontWeight: '700' },
  cancelled: { color: '#86510F', fontSize: 14, fontWeight: '700', marginTop: 5 },
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
