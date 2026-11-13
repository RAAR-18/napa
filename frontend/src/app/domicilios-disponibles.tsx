import { useEffect, useState } from 'react';
import { router } from 'expo-router';
import * as Location from 'expo-location';
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
      onPress={() => router.push({ pathname: '/domicilio-domiciliario', params: { domicilioId: item.id } })}
      style={({ pressed }) => [styles.card, pressed && styles.pressed]}
    >
      <View style={styles.row}>
        <Text style={styles.gain}>{formatPrice(item.ganancia)}</Text>
        <Text style={styles.distance}>{formatearDistancia(item.distanciaMetros)}</Text>
      </View>
      <Text style={styles.details}>Recoge en: {item.origen.direccion ?? 'Punto del vendedor'}</Text>
      <Text style={styles.details}>
        Entrega: {item.destino.barrio ? `barrio ${item.destino.barrio}` : 'zona aproximada'} (verás la dirección al
        aceptar)
      </Text>
      {item.distanciaAlOrigenMetros !== null ? (
        <Text style={styles.details}>Estás a {formatearDistancia(item.distanciaAlOrigenMetros)} del origen</Text>
      ) : null}
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
        const location = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
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

  useEffect(() => {
    if (!posicion) return undefined;
    const here = posicion;
    let active = true;
    async function load(silent: boolean) {
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
        const pageData = await listarDomiciliosDisponibles({ orden, posicion: here, page, size: 20 });
        if (active) setData(pageData);
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 401) {
          await clearSession();
          router.replace('/login');
          return;
        }
        if (active && !silent) {
          setError(cause instanceof Error ? cause.message : 'No se pudieron cargar los domicilios.');
        }
      } finally {
        if (active && !silent) setLoading(false);
      }
    }
    void load(false);
    const timer = setInterval(() => void load(true), REFRESCO_MS);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [orden, posicion, page, loadAttempt]);

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
      />
      <Text style={styles.filterTitle}>Ordenar por</Text>
      <View style={styles.options}>
        {ordenes.map((item) => (
          <OptionButton
            key={item.value}
            label={item.label}
            selected={orden === item.value}
            onPress={() => changeOrden(item.value)}
          />
        ))}
      </View>
      {notice ? <HelperText>{notice}</HelperText> : null}
      {notice && !posicion ? (
        <PrimaryButton title="Usar mi ubicación" onPress={() => setLocationAttempt((attempt) => attempt + 1)} />
      ) : null}
      {data ? (
        <HelperText>Domicilios que se recogen a {formatearDistancia(data.radioMetros)} o menos de ti.</HelperText>
      ) : null}
      <ErrorBanner message={error} />
      {loading ? (
        <ActivityIndicator accessibilityLabel="Cargando domicilios" color={palette.green} />
      ) : null}
      {!loading && data?.content.length === 0 ? (
        <HelperText>No hay domicilios disponibles cerca de ti en este momento.</HelperText>
      ) : null}
      {!loading ? data?.content.map((item) => <DisponibleRow key={item.id} item={item} />) : null}
      {data && data.totalElements > data.size ? (
        <View style={styles.pagination}>
          <TextButton title="Página anterior" disabled={page === 0} onPress={() => changePage(page - 1)} />
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
      <PrimaryButton
        title="Actualizar"
        disabled={loading || !posicion}
        onPress={() => setLoadAttempt((attempt) => attempt + 1)}
      />
      <TextButton title="Mis domicilios en curso" onPress={() => router.push('/domicilios-en-curso')} />
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
  gain: { color: palette.green, flex: 1, fontSize: 18, fontWeight: '800' },
  distance: { color: palette.dark, fontWeight: '700', marginLeft: 8 },
  details: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 5 },
  pagination: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 10,
  },
  pageText: { color: palette.muted, fontSize: 14 },
});
