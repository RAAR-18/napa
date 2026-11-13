import { useEffect, useState } from 'react';
import { router } from 'expo-router';
import { ActivityIndicator, Pressable, StyleSheet, Text } from 'react-native';

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
  MAXIMO_DOMICILIOS_EN_CURSO,
  estadoDomicilioNombres,
  listarDomiciliosEnCurso,
  type DomicilioEnCurso,
} from '../lib/domicilios';

export default function DomiciliosEnCursoScreen() {
  const [items, setItems] = useState<DomicilioEnCurso[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const session = await getValidSession();
        if (!active) return;
        if (session.rol !== 'DOMICILIARIO') {
          setError('Solo las cuentas de domiciliario tienen domicilios en curso.');
          return;
        }
        const response = await listarDomiciliosEnCurso();
        if (active) setItems(response);
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 401) {
          await clearSession();
          router.replace('/login');
          return;
        }
        if (active) setError(cause instanceof Error ? cause.message : 'No se pudieron cargar tus domicilios.');
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [loadAttempt]);

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Domiciliario"
        title="Mis domicilios en curso"
        description={`Puedes tener hasta ${MAXIMO_DOMICILIOS_EN_CURSO} domicilios en curso al mismo tiempo.`}
      />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando domicilios" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {!loading && items?.length === 0 ? <HelperText>No tienes domicilios en curso.</HelperText> : null}
      {items && items.length > 0 ? (
        <HelperText>{items.length} de {MAXIMO_DOMICILIOS_EN_CURSO} domicilios en curso.</HelperText>
      ) : null}
      {!loading
        ? items?.map((item) => (
            <Pressable
              key={item.id}
              accessibilityRole="button"
              accessibilityLabel={`Abrir domicilio ${estadoDomicilioNombres[item.estado]} hacia ${item.destino.direccion ?? 'el cliente'}`}
              onPress={() => router.push({ pathname: '/domicilio-domiciliario', params: { domicilioId: item.id } })}
              style={({ pressed }) => [styles.card, pressed && styles.pressed]}
            >
              <Text style={styles.status}>{estadoDomicilioNombres[item.estado]}</Text>
              <Text style={styles.details}>Recoge en: {item.origen.direccion ?? 'Punto del vendedor'}</Text>
              <Text style={styles.details}>Entrega en: {item.destino.direccion ?? 'Ubicación del cliente'}</Text>
              <Text style={styles.gain}>${item.ganancia.toLocaleString('es-CO')} COP</Text>
            </Pressable>
          ))
        : null}
      <PrimaryButton title="Actualizar" disabled={loading} onPress={() => setLoadAttempt((a) => a + 1)} />
      <TextButton title="Ver domicilios disponibles" onPress={() => router.replace('/domicilios-disponibles')} />
      <TextButton title="Volver a mi cuenta" onPress={() => router.replace('/session')} />
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
  pressed: { opacity: 0.72 },
  status: { color: palette.dark, fontSize: 17, fontWeight: '800' },
  details: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 5 },
  gain: { color: palette.green, fontWeight: '800', marginTop: 8 },
});
