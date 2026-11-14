import { useEffect, useState } from 'react';
import { router } from 'expo-router';
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
  abrirNotificacion,
  destinoNotificacion,
  listarNotificaciones,
  type Notificacion,
  type NotificacionesPagina,
} from '../lib/notificaciones';

const PAGE_SIZE = 20;

function formatDate(value: string) {
  return new Date(value).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
}

/** RF-38: notificaciones de la cuenta, de la más reciente a la más antigua. Exige conexión. */
export default function NotificacionesScreen() {
  const [role, setRole] = useState<string | null>(null);
  const [data, setData] = useState<NotificacionesPagina | null>(null);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  const [soloNoLeidas, setSoloNoLeidas] = useState(false);
  const [page, setPage] = useState(0);
  const [abierta, setAbierta] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const session = await getValidSession();
        if (!active) return;
        setRole(session.rol);
        const response = await listarNotificaciones({
          page,
          size: PAGE_SIZE,
          ...(soloNoLeidas ? { leida: false } : {}),
        });
        if (active) setData(response);
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 401) {
          await clearSession();
          router.replace('/login');
          return;
        }
        if (active) setError(cause instanceof Error ? cause.message : 'No se pudieron cargar tus notificaciones.');
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [soloNoLeidas, page, attempt]);

  async function abrir(notificacion: Notificacion) {
    setAbierta(abierta === notificacion.id ? null : notificacion.id);
    if (notificacion.leida) return;
    try {
      const leida = await abrirNotificacion(notificacion.id);
      setData((previous) =>
        previous
          ? { ...previous, content: previous.content.map((item) => (item.id === leida.id ? leida : item)) }
          : previous,
      );
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'No se pudo abrir la notificación.');
    }
  }

  const lastPage = data ? Math.max(0, Math.ceil(data.totalElements / PAGE_SIZE) - 1) : 0;

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Tu cuenta"
        title="Notificaciones"
        description="Novedades de tus pedidos, domicilios, pagos y reportes. Se guardan durante 30 días."
      />
      <View style={styles.options}>
        <OptionButton
          label="Todas"
          selected={!soloNoLeidas}
          onPress={() => {
            setPage(0);
            setSoloNoLeidas(false);
          }}
        />
        <OptionButton
          label="No leídas"
          selected={soloNoLeidas}
          onPress={() => {
            setPage(0);
            setSoloNoLeidas(true);
          }}
        />
      </View>
      {loading ? <ActivityIndicator accessibilityLabel="Cargando notificaciones" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {error && !data ? <PrimaryButton title="Reintentar" onPress={() => setAttempt((value) => value + 1)} /> : null}
      {!loading && data?.content.length === 0 ? (
        <HelperText>{soloNoLeidas ? 'No tienes notificaciones sin leer.' : 'No tienes notificaciones.'}</HelperText>
      ) : null}
      {data?.content.map((notificacion) => {
        const destino = role ? destinoNotificacion(notificacion, role) : null;
        const expandida = abierta === notificacion.id;
        return (
          <Pressable
            key={notificacion.id}
            accessibilityRole="button"
            accessibilityLabel={`${notificacion.leida ? '' : 'Sin leer: '}${notificacion.titulo}`}
            onPress={() => void abrir(notificacion)}
            style={[styles.card, !notificacion.leida && styles.unread]}
          >
            <View style={styles.row}>
              {!notificacion.leida ? <View style={styles.dot} /> : null}
              <Text style={[styles.title, !notificacion.leida && styles.titleUnread]}>{notificacion.titulo}</Text>
            </View>
            <Text style={styles.date}>{formatDate(notificacion.createdAt)}</Text>
            {expandida ? (
              <>
                <Text style={styles.body}>{notificacion.cuerpo}</Text>
                {destino ? (
                  <TextButton
                    title={notificacion.referencia?.tipo === 'DOMICILIO' ? 'Ver domicilio' : 'Ver pedido'}
                    onPress={() => router.push(destino)}
                  />
                ) : null}
              </>
            ) : null}
          </Pressable>
        );
      })}
      {data && data.totalElements > PAGE_SIZE ? (
        <View style={styles.pager}>
          <TextButton title="Anterior" disabled={page === 0 || loading} onPress={() => setPage(page - 1)} />
          <Text style={styles.date}>
            Página {page + 1} de {lastPage + 1}
          </Text>
          <TextButton title="Siguiente" disabled={page >= lastPage || loading} onPress={() => setPage(page + 1)} />
        </View>
      ) : null}
      {data ? (
        <TextButton title="Actualizar" disabled={loading} onPress={() => setAttempt((value) => value + 1)} />
      ) : null}
      <TextButton
        title="Elegir qué notificaciones recibo"
        onPress={() => router.push('/preferencias-notificaciones')}
      />
      <TextButton title="Volver a mi cuenta" onPress={() => router.replace('/session')} />
    </AuthPage>
  );
}

const styles = StyleSheet.create({
  options: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginVertical: 6 },
  card: {
    backgroundColor: palette.white,
    borderColor: palette.border,
    borderRadius: 16,
    borderWidth: 1,
    marginVertical: 6,
    padding: 14,
  },
  unread: { backgroundColor: '#EEF6F1', borderColor: '#B9D9C6' },
  row: { alignItems: 'center', flexDirection: 'row' },
  dot: { backgroundColor: palette.green, borderRadius: 5, height: 10, marginRight: 8, width: 10 },
  title: { color: palette.dark, flex: 1, fontSize: 15, fontWeight: '600' },
  titleUnread: { fontWeight: '800' },
  date: { color: palette.muted, fontSize: 13, marginTop: 4 },
  body: { color: palette.dark, fontSize: 14, lineHeight: 21, marginTop: 8 },
  pager: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between', marginVertical: 8 },
});
