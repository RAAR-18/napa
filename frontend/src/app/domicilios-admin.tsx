import { useEffect, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  Field,
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
  const estado = estadoDomicilioNombres[item.estado];
  const demorado = item.minutosSinTomar !== null && item.minutosSinTomar >= MINUTOS_SIN_TOMAR_RESALTE;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`Ver domicilio ${estado} de ${item.vendedor.nombre ?? 'vendedor'}`}
      onPress={() => router.push({ pathname: '/domicilio-admin', params: { domicilioId: item.id } })}
      style={({ pressed }) => [styles.card, demorado && styles.cardDelayed, pressed && styles.pressed]}
    >
      <View style={styles.row}>
        <Text style={styles.status}>{estado}</Text>
        <Text style={styles.fee}>${item.tarifa.toLocaleString('es-CO')} COP</Text>
      </View>
      {item.minutosSinTomar !== null ? (
        <Text style={demorado ? styles.delayed : styles.details}>{textoSinTomar(item.minutosSinTomar)}</Text>
      ) : null}
      <Text style={styles.details}>De: {item.origen.direccion ?? 'Sin dirección'}</Text>
      <Text style={styles.details}>
        A: {item.destino.direccion ?? 'Sin dirección'}
        {item.destino.barrio ? ` (${item.destino.barrio})` : ''}
      </Text>
      <Text style={styles.details}>Vendedor: {item.vendedor.nombre ?? '—'}</Text>
      <Text style={styles.details}>Cliente: {item.cliente.nombre ?? '—'}</Text>
      {item.domiciliario ? (
        <Text style={styles.details}>Domiciliario: {item.domiciliario.nombre ?? '—'}</Text>
      ) : null}
      {item.motivoCancelacion ? (
        <Text style={styles.cancelled}>{motivoCancelacionNombres[item.motivoCancelacion]}</Text>
      ) : null}
      <Text style={styles.details}>Publicado: {formatDate(item.createdAt)}</Text>
    </Pressable>
  );
}

/** RF-12: el administrador supervisa todos los domicilios de la plataforma. Exige conexión. */
export default function DomiciliosAdminScreen() {
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
        if (active) setError(cause instanceof Error ? cause.message : 'No se pudieron cargar los domicilios.');
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
        title="Domicilios"
        description="Todos los domicilios de la plataforma, en cualquier estado."
      />
      {vendedorId || domiciliarioId ? (
        <View style={styles.personFilter}>
          <Text style={styles.details}>
            {vendedorId ? 'Solo los domicilios del vendedor' : 'Solo los domicilios del domiciliario'}
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
      <Text style={styles.filterTitle}>Estado</Text>
      <View style={styles.options}>
        {statuses.map((item) => (
          <OptionButton
            key={item.value}
            label={item.label}
            selected={estado === item.value}
            onPress={() => {
              setPage(0);
              setEstado(item.value);
            }}
          />
        ))}
      </View>
      <Field
        label="Desde (AAAA-MM-DD)"
        value={desdeInput}
        onChangeText={setDesdeInput}
        placeholder="2026-11-01"
        autoCapitalize="none"
      />
      <Field
        label="Hasta (AAAA-MM-DD)"
        value={hastaInput}
        onChangeText={setHastaInput}
        placeholder="2026-11-30"
        autoCapitalize="none"
      />
      <ErrorBanner message={dateError} />
      <TextButton title="Aplicar fechas" disabled={loading} onPress={applyDates} />
      <ErrorBanner message={error} />
      {error ? <PrimaryButton title="Reintentar" onPress={() => setAttempt((value) => value + 1)} /> : null}
      {loading ? <ActivityIndicator accessibilityLabel="Cargando domicilios" color={palette.green} /> : null}
      {!loading && data?.content.length === 0 ? (
        filtered ? (
          <>
            <HelperText>No hay domicilios con esos filtros.</HelperText>
            <TextButton title="Limpiar filtros" onPress={clearFilters} />
          </>
        ) : (
          <HelperText>No hay domicilios registrados.</HelperText>
        )
      ) : null}
      {data?.content.map((item) => <FilaDomicilio key={item.id} item={item} />)}
      {data && data.totalElements > PAGE_SIZE ? (
        <View style={styles.pagination}>
          <TextButton title="Anterior" disabled={loading || page === 0} onPress={() => setPage(page - 1)} />
          <Text style={styles.pageText}>
            Página {page + 1} de {lastPage + 1}
          </Text>
          <TextButton title="Siguiente" disabled={loading || page >= lastPage} onPress={() => setPage(page + 1)} />
        </View>
      ) : null}
      {data ? (
        <TextButton title="Actualizar" disabled={loading} onPress={() => setAttempt((value) => value + 1)} />
      ) : null}
      <TextButton title="Volver a mi cuenta" onPress={() => router.replace('/session')} />
    </AuthPage>
  );
}

const styles = StyleSheet.create({
  filterTitle: { color: palette.dark, fontSize: 14, fontWeight: '800', marginTop: 4 },
  options: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 10 },
  personFilter: {
    backgroundColor: '#EEF6F1',
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
    padding: 16,
  },
  cardDelayed: { backgroundColor: '#FFF8E8', borderColor: '#E8C77A' },
  pressed: { opacity: 0.72 },
  row: { alignItems: 'flex-start', flexDirection: 'row', justifyContent: 'space-between' },
  status: { color: palette.dark, flex: 1, fontSize: 17, fontWeight: '800' },
  fee: { color: palette.green, fontWeight: '800', marginLeft: 8 },
  details: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 5 },
  delayed: { color: '#86510F', fontSize: 14, fontWeight: '800', marginTop: 5 },
  cancelled: { color: '#86510F', fontSize: 14, fontWeight: '700', marginTop: 5 },
  pagination: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between', marginTop: 10 },
  pageText: { color: palette.muted, fontSize: 14 },
});
