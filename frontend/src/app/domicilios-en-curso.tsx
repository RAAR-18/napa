import { fonts } from '../components/theme';
import { useCallback, useState } from 'react';
import { router, useFocusEffect } from 'expo-router';
import { useAutoRefresh } from '../hooks/useAutoRefresh';
import { ActivityIndicator, Pressable, StyleSheet, Text } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  HelperText,
  PageHeading,
  PrimaryButton,
  palette,
} from '../components/AuthUI';
import { guardarConsultaPedidos, leerConsultaPedidos } from '../lib/pedidos-cliente-cache';
import { ApiError, NetworkError } from '../lib/api';
import { clearSession, getValidSession, readSession } from '../lib/auth';
import {
  MAXIMO_DOMICILIOS_EN_CURSO,
  estadoDomicilioNombres,
  listarDomiciliosEnCurso,
  type DomicilioEnCurso,
  type DomicilioEnCursoGuardado,
} from '../lib/domicilios';

export default function DomiciliosEnCursoScreen() {
  const [items, setItems] = useState<(DomicilioEnCurso | DomicilioEnCursoGuardado)[] | null>(null);
  const [fechaGuardada, setFechaGuardada] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useAutoRefresh(() => setLoadAttempt((intento) => intento + 1), !loading);

  useFocusEffect(useCallback(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setError(null);
      setItems(null);
      setFechaGuardada(null);
      let local: Awaited<ReturnType<typeof readSession>> = null;
      try {
        local = typeof readSession === 'function' ? await readSession() : null;
        const session = await getValidSession();
        if (!active) return;
        if (session.rol !== 'DOMICILIARIO') {
          setError('Solo las cuentas de domiciliario tienen domicilios en curso.');
          return;
        }
        const response = await listarDomiciliosEnCurso();
        if (!active) return;
        setItems(response);
        if (session.usuarioId) {
          const copia = response.map(({ id, estado, ganancia, asignadoAt }) => ({ id, estado, ganancia, asignadoAt }));
          void guardarConsultaPedidos(session.usuarioId, 'listado-domiciliario', 'en-curso', copia, () => active)
            .catch(() => undefined);
        }
      } catch (cause) {
        if (!active) return;
        if (cause instanceof NetworkError && local?.rol === 'DOMICILIARIO' && local.usuarioId) {
          const copia = await leerConsultaPedidos<DomicilioEnCursoGuardado[]>(
            local.usuarioId, 'listado-domiciliario', 'en-curso',
          ).catch(() => null);
          const vigente = await readSession();
          if (!active || vigente?.usuarioId !== local.usuarioId) return;
          if (copia) {
            setItems(copia.data);
            setFechaGuardada(copia.fecha);
          } else {
            setError('Conéctate para descargar tus domicilios en curso.');
          }
          return;
        }
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
  // eslint-disable-next-line react-hooks/exhaustive-deps -- loadAttempt invalida el ciclo al actualizar manual o automáticamente.
  }, [loadAttempt]));

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Domiciliario"
        title="Mis domicilios en curso"
        description={`Puedes tener hasta ${MAXIMO_DOMICILIOS_EN_CURSO} domicilios en curso al mismo tiempo.`}
      />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando domicilios" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {fechaGuardada !== null ? <HelperText>Datos guardados. Última actualización {new Date(fechaGuardada).toLocaleString('es-CO')}.</HelperText> : null}
      {!loading && items?.length === 0 ? <HelperText>No tienes domicilios en curso.</HelperText> : null}
      {items && items.length > 0 ? (
        <HelperText>{items.length} de {MAXIMO_DOMICILIOS_EN_CURSO} domicilios en curso.</HelperText>
      ) : null}
      {!loading
        ? items?.map((item) => (
            <Pressable
              key={item.id}
              accessibilityRole="button"
              accessibilityLabel={`Abrir domicilio ${estadoDomicilioNombres[item.estado]} hacia ${('destino' in item ? item.destino.direccion : null) ?? 'el cliente'}`}
              onPress={() => router.push({ pathname: '/domicilio-domiciliario', params: { domicilioId: item.id } })}
              style={({ pressed }) => [styles.card, pressed && styles.pressed]}
            >
              <Text style={styles.status}>{estadoDomicilioNombres[item.estado]}</Text>
              {'origen' in item ? <>
              <Text style={styles.details}>Recoge en: {item.origen.direccion ?? 'Punto del vendedor'}</Text>
              <Text style={styles.details}>Entrega en: {item.destino.direccion ?? 'Ubicación del cliente'}</Text>
              </> : null}
              <Text style={styles.gain}>${item.ganancia.toLocaleString('es-CO')} COP</Text>
            </Pressable>
          ))
        : null}
      <PrimaryButton title="Actualizar" disabled={loading} onPress={() => setLoadAttempt((a) => a + 1)} />
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
  status: { color: palette.dark, fontFamily: fonts.sans, fontSize: 17, fontWeight: '800' },
  details: { color: palette.muted, fontFamily: fonts.sans, fontSize: 14, lineHeight: 21, marginTop: 5 },
  gain: { color: palette.green, fontWeight: '800', marginTop: 8 },
});
