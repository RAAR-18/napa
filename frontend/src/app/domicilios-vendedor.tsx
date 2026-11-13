import { useEffect, useState } from 'react';
import { router } from 'expo-router';
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
  estadoDomicilioNombres,
  listarDomiciliosVendedor,
  motivoCancelacionNombres,
  type DomicilioVendedorItem,
  type DomiciliosVendedorPagina,
  type EstadoDomicilio,
  type FiltrosDomiciliosVendedor,
} from '../lib/domicilios';

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

function formatDate(value: string) {
  return new Date(value).toLocaleString('es-CO', {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
}

function validDate(value: string) {
  return DATE_PATTERN.test(value) && !Number.isNaN(new Date(`${value}T00:00:00`).getTime());
}

function DomicilioRow({ item }: { item: DomicilioVendedorItem }) {
  const estado = estadoDomicilioNombres[item.estado];
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`Abrir pedido del domicilio ${estado}`}
      onPress={() => router.push({ pathname: '/pedido-vendedor', params: { pedidoId: item.pedidoId } })}
      style={({ pressed }) => [styles.card, pressed && styles.pressed]}
    >
      <View style={styles.row}>
        <Text style={styles.status}>{estado}</Text>
        <Text style={styles.fee}>${item.tarifa.toLocaleString('es-CO')} COP</Text>
      </View>
      <Text style={styles.details}>Publicado: {formatDate(item.createdAt)}</Text>
      {item.domiciliario ? (
        <Text style={styles.details}>Domiciliario: {item.domiciliario.nombre ?? 'No disponible'}</Text>
      ) : null}
      {item.motivoCancelacion ? (
        <Text style={styles.cancelled}>{motivoCancelacionNombres[item.motivoCancelacion]}</Text>
      ) : null}
      <Text style={styles.details}>Última actualización: {formatDate(item.updatedAt)}</Text>
    </Pressable>
  );
}

export default function DomiciliosVendedorScreen() {
  const [data, setData] = useState<DomiciliosVendedorPagina | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [page, setPage] = useState(0);
  const [estado, setEstado] = useState<EstadoDomicilio | ''>('');
  const [desdeInput, setDesdeInput] = useState('');
  const [hastaInput, setHastaInput] = useState('');
  const [desde, setDesde] = useState('');
  const [hasta, setHasta] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [dateError, setDateError] = useState<string | null>(null);
  const filtered = Boolean(estado || desde || hasta);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setData(null);
      setError(null);
      try {
        const session = await getValidSession();
        if (!active) return;
        if (session.rol !== 'VENDEDOR_PUNTO_FIJO') {
          setError('Solo los vendedores de punto fijo publican y consultan domicilios.');
          return;
        }
        const filters: FiltrosDomiciliosVendedor = {
          page,
          size: 20,
          ...(estado ? { estado } : {}),
          ...(desde ? { desde } : {}),
          ...(hasta ? { hasta } : {}),
        };
        const pageData = await listarDomiciliosVendedor(filters);
        if (active) setData(pageData);
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 401) {
          await clearSession();
          router.replace('/login');
          return;
        }
        if (active) {
          setError(cause instanceof Error ? cause.message : 'No se pudieron cargar los domicilios.');
        }
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [estado, desde, hasta, page, loadAttempt]);

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
    setDateError(null);
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
        title="Mis domicilios"
        description="Haz seguimiento a los domicilios que publicaste para tus pedidos."
      />
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
      {loading ? (
        <ActivityIndicator accessibilityLabel="Cargando domicilios" color={palette.green} />
      ) : null}
      {!loading && data?.content.length === 0 ? (
        filtered ? (
          <>
            <HelperText>Sin resultados.</HelperText>
            <TextButton title="Limpiar filtros" onPress={clearFilters} />
          </>
        ) : (
          <HelperText>Aún no tienes domicilios.</HelperText>
        )
      ) : null}
      {!loading ? data?.content.map((item) => <DomicilioRow key={item.id} item={item} />) : null}
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
        title="Actualizar domicilios"
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
  status: { color: palette.dark, flex: 1, fontSize: 17, fontWeight: '800' },
  fee: { color: palette.green, fontWeight: '800', marginLeft: 8 },
  details: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 5 },
  cancelled: { color: '#86510F', fontSize: 14, fontWeight: '700', marginTop: 5 },
  pagination: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 10,
  },
  pageText: { color: palette.muted, fontSize: 14 },
});
