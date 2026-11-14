import { useEffect, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  HelperText,
  PageHeading,
  PrimaryButton,
  TextButton,
  palette,
} from '../components/AuthUI';
import { ApiError } from '../lib/api';
import { clearSession, getValidSession } from '../lib/auth';
import {
  estadoDomicilioNombres,
  motivoCancelacionNombres,
  obtenerTrazabilidad,
  type TrazabilidadDomicilio,
} from '../lib/domicilios';

/** Mientras la pantalla está abierta se refresca para ver el avance sin tocar nada (RF-26). */
const REFRESCO_MS = 30_000;
const FINALES = ['ENTREGADO', 'CANCELADO'];

function formatDate(value: string) {
  return new Date(value).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
}

export default function TrazabilidadDomicilioScreen() {
  const { domicilioId: rawId } = useLocalSearchParams<{ domicilioId?: string | string[] }>();
  const domicilioId = Array.isArray(rawId) ? rawId[0] : rawId;
  const [data, setData] = useState<TrazabilidadDomicilio | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [role, setRole] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    async function load(silent: boolean) {
      if (!silent) {
        setLoading(true);
        setError(null);
      }
      if (!domicilioId) {
        setError('No se indicó el domicilio que quieres consultar.');
        setLoading(false);
        return;
      }
      try {
        const session = await getValidSession();
        if (!active) return;
        setRole(session.rol);
        const response = await obtenerTrazabilidad(domicilioId);
        if (active) setData(response);
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 401) {
          await clearSession();
          router.replace('/login');
          return;
        }
        if (active && !silent) {
          setError(
            cause instanceof ApiError && cause.status === 404
              ? 'El domicilio no está disponible.'
              : cause instanceof Error
                ? cause.message
                : 'No se pudo cargar la trazabilidad.',
          );
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
  }, [domicilioId, loadAttempt]);

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Domicilio"
        title="Trazabilidad"
        description="Cada cambio de estado del domicilio con su fecha y hora."
      />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando trazabilidad" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {data ? (
        <>
          <View style={styles.card}>
            <Text style={styles.status}>{estadoDomicilioNombres[data.estado]}</Text>
            {data.motivoCancelacion ? (
              <Text style={styles.detailText}>{motivoCancelacionNombres[data.motivoCancelacion]}</Text>
            ) : null}
            <Text style={styles.detailText}>Última actualización: {formatDate(data.updatedAt)}</Text>
            {data.estado === 'EN_CAMINO' && role === 'CLIENTE' && !data.ubicacionDomiciliario ? (
              <Text style={styles.detailText}>Sin ubicación reciente del domiciliario.</Text>
            ) : null}
          </View>
          <Text accessibilityRole="header" style={styles.sectionTitle}>Cronología</Text>
          {data.eventos.map((evento, index) => (
            <View
              key={`${evento.estado}-${evento.ocurridoAt}`}
              accessibilityLabel={`${estadoDomicilioNombres[evento.estado]}, ${formatDate(evento.ocurridoAt)}`}
              style={styles.step}
            >
              <View style={[styles.dot, index === data.eventos.length - 1 && styles.dotCurrent]} />
              <View style={styles.stepText}>
                <Text style={styles.stepTitle}>{estadoDomicilioNombres[evento.estado]}</Text>
                <Text style={styles.detailText}>{formatDate(evento.ocurridoAt)}</Text>
              </View>
            </View>
          ))}
          {!FINALES.includes(data.estado) ? (
            <HelperText>La información se actualiza sola cada 30 segundos.</HelperText>
          ) : null}
        </>
      ) : null}
      <PrimaryButton title="Actualizar" disabled={loading} onPress={() => setLoadAttempt((a) => a + 1)} />
      <TextButton title="Volver" onPress={() => router.back()} />
    </AuthPage>
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
  status: { color: palette.dark, fontSize: 18, fontWeight: '800' },
  sectionTitle: { color: palette.dark, fontSize: 16, fontWeight: '800', marginTop: 12 },
  detailText: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 4 },
  step: { alignItems: 'flex-start', flexDirection: 'row', marginTop: 10 },
  dot: {
    backgroundColor: palette.border,
    borderRadius: 6,
    height: 12,
    marginRight: 12,
    marginTop: 4,
    width: 12,
  },
  dotCurrent: { backgroundColor: palette.green },
  stepText: { flex: 1 },
  stepTitle: { color: palette.dark, fontSize: 15, fontWeight: '700' },
});
