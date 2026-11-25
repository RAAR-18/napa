import { useCallback, useEffect, useState } from 'react';
import { router, useFocusEffect } from 'expo-router';
import * as Location from 'expo-location';
import { ActivityIndicator, AppState, Pressable, StyleSheet, Text, View } from 'react-native';

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
import {
  CardGrid,
  DetailLine,
  EmptyState,
  FilterPanel,
  FilterRow,
  GridCard,
  Icon,
  Illustration,
  RefreshButton,
} from '../components/NapaUI';
import { art, fonts } from '../components/theme';
import { ApiError } from '../lib/api';
import { clearSession, getValidSession } from '../lib/auth';
import {
  formatearDistancia,
  listarDomiciliosDisponibles,
  type DomicilioDisponible,
  type DomiciliosDisponiblesPagina,
  type OrdenDisponibles,
} from '../lib/domicilios';

/** La lista se refresca sola para que un domicilio tomado por otro desaparezca pronto (RF-16 SC-003). */
const REFRESCO_MS = 15_000;
const ordenes: { value: OrdenDisponibles; label: string }[] = [
  { value: 'recientes', label: 'Recientes' },
  { value: 'ganancia', label: 'Mayor ganancia' },
  { value: 'cercania', label: 'Más cercanos' },
];

function formatPrice(value: number) {
  return `$${value.toLocaleString('es-CO')} COP`;
}

function DisponibleRow({ item }: { item: DomicilioDisponible }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`Ver domicilio con ganancia de ${formatPrice(item.ganancia)}`}
      onPress={() =>
        router.push({ pathname: '/domicilio-domiciliario', params: { domicilioId: item.id } })
      }
      style={({ pressed }) => [styles.card, pressed && styles.pressed]}
    >
      <Illustration source={art.paquete} style={{ height: 125 }} />
      <View style={styles.cardBody}>
        <Text style={styles.gain}>{formatPrice(item.ganancia)}</Text>
        <View style={styles.gainChip}>
          <Text style={styles.gainLabel}>Tu ganancia</Text>
        </View>
        <DetailLine icon="map-pin">
          Recorrido: {formatearDistancia(item.distanciaMetros)}
        </DetailLine>
        <View style={styles.divider} />
        <View style={styles.destinationRow}>
          <View style={[styles.pointBadge, { backgroundColor: palette.peach }]}>
            <Text style={styles.pointText}>A</Text>
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.destinationLabel}>Recoge en</Text>
            <Text style={styles.destinationText}>
              {item.origen.direccion ?? 'Punto del vendedor'}
            </Text>
          </View>
        </View>
        <View style={styles.destinationRow}>
          <View style={[styles.pointBadge, { backgroundColor: palette.sage }]}>
            <Text style={styles.pointText}>B</Text>
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.destinationLabel}>Entrega en</Text>
            <Text style={styles.destinationText}>
              {item.destino.barrio ? `barrio ${item.destino.barrio}` : 'zona aproximada'}
            </Text>
            <Text style={styles.privacyNote}>Dirección exacta al aceptar</Text>
          </View>
        </View>
        <View style={styles.divider} />
        {item.distanciaAlOrigenMetros !== null ? (
          <DetailLine icon="navigation">
            Estás a {formatearDistancia(item.distanciaAlOrigenMetros)} del origen
          </DetailLine>
        ) : null}
        <View style={styles.viewDelivery}>
          <Text style={styles.viewDeliveryText}>Ver domicilio</Text>
          <Icon name="arrow-right" color="#FFFFFF" size={22} />
        </View>
      </View>
    </Pressable>
  );
}

export default function DomiciliosDisponiblesScreen() {
  const [data, setData] = useState<DomiciliosDisponiblesPagina | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [page, setPage] = useState(0);
  const [orden, setOrden] = useState<OrdenDisponibles>('recientes');
  const [posicion, setPosicion] = useState<{ lat: number; lng: number } | null>(null);
  const [locationAttempt, setLocationAttempt] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  // Sin la posición no hay lista: solo se muestran los domicilios con origen cerca del domiciliario.
  useEffect(() => {
    let active = true;
    async function locate() {
      setNotice(null);
      try {
        const permission = await Location.requestForegroundPermissionsAsync();
        if (permission.status !== 'granted') {
          if (active) {
            setLoading(false);
            setNotice('Activa el permiso de ubicación para ver los domicilios cerca de ti.');
          }
          return;
        }
        const location = await Location.getCurrentPositionAsync({
          accuracy: Location.Accuracy.Balanced,
        });
        if (active) setPosicion({ lat: location.coords.latitude, lng: location.coords.longitude });
      } catch {
        if (active) {
          setLoading(false);
          setNotice('No se pudo obtener tu ubicación. Inténtalo de nuevo.');
        }
      }
    }
    void locate();
    return () => {
      active = false;
    };
  }, [locationAttempt]);

  useFocusEffect(useCallback(() => {
    if (!posicion) return undefined;
    const here = posicion;
    let active = true;
    let cargando = false;
    async function load(silent: boolean) {
      if (!active || cargando || (AppState.currentState && AppState.currentState !== 'active')) return;
      cargando = true;
      if (!silent) {
        setLoading(true);
        setError(null);
      }
      try {
        const session = await getValidSession();
        if (!active) return;
        if (session.rol !== 'DOMICILIARIO') {
          setError('Solo las cuentas de domiciliario pueden ver los domicilios disponibles.');
          return;
        }
        const pageData = await listarDomiciliosDisponibles({
          orden,
          posicion: here,
          page,
          size: 20,
        });
        if (active) setData(pageData);
      } catch (cause) {
        if (!active) return;
        if (cause instanceof ApiError && cause.status === 401) {
          await clearSession();
          router.replace('/login');
          return;
        }
        if (active && !silent) {
          setError(
            cause instanceof Error ? cause.message : 'No se pudieron cargar los domicilios.',
          );
        }
      } finally {
        cargando = false;
        if (active && !silent) setLoading(false);
      }
    }
    void load(false);
    const timer = setInterval(() => void load(true), REFRESCO_MS);
    const listener = AppState.addEventListener('change', (estado) => {
      if (estado === 'active') void load(false);
    });
    return () => {
      active = false;
      clearInterval(timer);
      listener.remove();
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps -- loadAttempt invalida el ciclo al actualizar manual o automáticamente.
  }, [orden, posicion, page, loadAttempt]));

  function changeOrden(next: OrdenDisponibles) {
    setPage(0);
    setOrden(next);
  }

  function changePage(nextPage: number) {
    if (nextPage >= 0 && data && nextPage * data.size < data.totalElements) {
      setPage(nextPage);
    }
  }

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Domiciliario"
        title="Domicilios disponibles"
        description="Elige un domicilio para ver su información y decidir si lo tomas."
        action={
          <RefreshButton
            disabled={loading || !posicion}
            onPress={() => setLoadAttempt((attempt) => attempt + 1)}
          />
        }
      />
      <FilterPanel>
        <FilterRow label="Ordenar por">
          {ordenes.map((item) => (
            <OptionButton
              key={item.value}
              label={item.label}
              selected={orden === item.value}
              onPress={() => changeOrden(item.value)}
            />
          ))}
        </FilterRow>
      </FilterPanel>
      {notice ? <HelperText>{notice}</HelperText> : null}
      {notice && !posicion ? (
        <PrimaryButton
          title="Usar mi ubicación"
          onPress={() => setLocationAttempt((attempt) => attempt + 1)}
        />
      ) : null}
      {data ? (
        <HelperText>
          Domicilios que se recogen a {formatearDistancia(data.radioMetros)} o menos de ti.
        </HelperText>
      ) : null}
      <ErrorBanner message={error} />
      {loading ? (
        <ActivityIndicator accessibilityLabel="Cargando domicilios" color={palette.green} />
      ) : null}
      {!loading && data?.content.length === 0 ? (
        <EmptyState
          title="No hay domicilios disponibles cerca de ti en este momento."
          description="Vuelve a actualizar en unos minutos. Tu próxima entrega puede estar muy cerca."
          icon="navigation"
        />
      ) : null}
      <CardGrid>
        {!loading
          ? data?.content.map((item) => (
              <GridCard key={item.id}>
                <DisponibleRow item={item} />
              </GridCard>
            ))
          : null}
      </CardGrid>
      {data && data.totalElements > data.size ? (
        <View style={styles.pagination}>
          <TextButton
            title="Página anterior"
            disabled={page === 0}
            onPress={() => changePage(page - 1)}
          />
          <Text style={styles.pageText}>
            {page + 1} de {Math.ceil(data.totalElements / data.size)}
          </Text>
          <TextButton
            title="Página siguiente"
            disabled={(page + 1) * data.size >= data.totalElements}
            onPress={() => changePage(page + 1)}
          />
        </View>
      ) : null}
      <TextButton
        title="Mis domicilios en curso"
        onPress={() => router.push('/domicilios-en-curso')}
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
    overflow: 'hidden',
    flex: 1,
  },
  cardBody: { padding: 18 },
  gainChip: {
    backgroundColor: palette.sage,
    paddingHorizontal: 13,
    paddingVertical: 4,
    borderRadius: 20,
    alignSelf: 'flex-start',
    marginTop: 5,
    marginBottom: 5,
  },
  gainLabel: { color: palette.dark, fontFamily: fonts.sans, fontSize: 12, fontWeight: '700' },
  divider: { height: 1, backgroundColor: palette.border, marginVertical: 12 },
  destinationRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 12, marginBottom: 12 },
  pointBadge: {
    height: 30,
    width: 30,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 15,
  },
  pointText: { color: palette.dark, fontFamily: fonts.sans, fontWeight: '800', fontSize: 14 },
  destinationLabel: {
    fontFamily: fonts.sans,
    color: palette.dark,
    fontSize: 12,
    fontWeight: '800',
  },
  destinationText: {
    fontFamily: fonts.sans,
    color: palette.dark,
    fontSize: 14,
    lineHeight: 20,
    marginTop: 2,
  },
  privacyNote: {
    fontFamily: fonts.sans,
    color: palette.muted,
    fontSize: 11,
    fontStyle: 'italic',
    marginTop: 4,
  },
  viewDelivery: {
    backgroundColor: palette.coral,
    borderRadius: 15,
    minHeight: 44,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
    marginTop: 18,
  },
  viewDeliveryText: { color: '#FFFFFF', fontFamily: fonts.sans, fontWeight: '700', fontSize: 15 },
  pressed: { opacity: 0.72 },
  row: { alignItems: 'flex-start', flexDirection: 'row', justifyContent: 'space-between' },
  gain: {
    color: palette.dark,
    fontFamily: fonts.display,
    fontSize: 30,
    fontWeight: '700',
    letterSpacing: -0.5,
  },
  distance: { color: palette.dark, fontWeight: '700', marginLeft: 8 },
  details: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 5 },
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
