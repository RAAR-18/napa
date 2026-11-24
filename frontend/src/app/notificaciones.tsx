import { useAutoRefresh } from '../hooks/useAutoRefresh';
import { fonts } from '../components/theme';
import { useEffect, useRef, useState } from 'react';
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
import { ApiError, NetworkError } from '../lib/api';
import { clearSession, getSessionGeneration, getValidSession, readSession } from '../lib/auth';
import {
  borrarNotificacionesGuardadas,
  guardarPaginaNotificaciones,
  leerPaginaNotificaciones,
  listarPaginasGuardadas,
  type NotificacionGuardada,
  type PaginaNotificacionesGuardada,
} from '../lib/notificaciones-cache';
import {
  abrirNotificacion,
  destinoNotificacion,
  listarNotificaciones,
  type NotificacionesPagina,
} from '../lib/notificaciones';

const PAGE_SIZE = 20;

function formatDate(value: string) {
  return new Date(value).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
}

/** RF-38: notificaciones de la cuenta, de la más reciente a la más antigua. */
export default function NotificacionesScreen() {
  const [role, setRole] = useState<string | null>(null);
  const [data, setData] = useState<NotificacionesPagina | PaginaNotificacionesGuardada | null>(null);
  const [fechaCopia, setFechaCopia] = useState<number | null>(null);
  const [paginasGuardadas, setPaginasGuardadas] = useState<number[]>([]);
  const solicitud = useRef(0);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  const automatico = useRef(false);
  useAutoRefresh(() => { automatico.current = true; setAttempt((value) => value + 1); }, !loading);
  const [soloNoLeidas, setSoloNoLeidas] = useState(false);
  const [page, setPage] = useState(0);
  const [abierta, setAbierta] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    const generation = getSessionGeneration();
    const vigente = () => active && generation === getSessionGeneration();
    solicitud.current += 1;
    async function load() {
      const silencioso = automatico.current;
      automatico.current = false;
      setLoading(true);
      setError(null);
      if (!silencioso) { setData(null); setAbierta(null); }
      setFechaCopia(null);
      setPaginasGuardadas([]);
      let cuenta: string | undefined;
      try {
        cuenta = (await readSession())?.usuarioId;
        const session = await getValidSession();
        if (!vigente()) return;
        setRole(session.rol);
        const response = await listarNotificaciones({
          page,
          size: PAGE_SIZE,
          ...(soloNoLeidas ? { leida: false } : {}),
        });
        if (!vigente()) return;
        if (session.usuarioId) {
          await guardarPaginaNotificaciones(session.usuarioId, soloNoLeidas, response, vigente)
            .catch((error: unknown) => console.warn('No se pudo guardar la bandeja.', error));
        }
        if (vigente()) setData(response);
      } catch (cause) {
        if (!vigente()) return;
        if (cuenta && cause instanceof NetworkError) {
          try {
            const copia = await leerPaginaNotificaciones(cuenta, soloNoLeidas, page);
            if (copia) {
              if (!vigente()) return;
              setData(copia.data);
              setFechaCopia(copia.fecha);
            } else {
              const paginas = await listarPaginasGuardadas(cuenta, soloNoLeidas);
              if (!vigente()) return;
              setPaginasGuardadas(paginas);
              setError('No hay notificaciones descargadas para esta página del filtro.');
            }
            return;
          } catch (cacheError) {
            console.warn('No se pudo leer la bandeja guardada.', cacheError);
          }
        }
        if (cuenta && cause instanceof ApiError && (cause.status === 401 || cause.status === 403)) {
          await borrarNotificacionesGuardadas(cuenta)
            .catch((error: unknown) => console.warn('No se pudo borrar la bandeja guardada.', error));
        }
        if (!vigente()) return;
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
      solicitud.current += 1;
    };
  }, [soloNoLeidas, page, attempt]);

  async function abrir(notificacion: NotificacionGuardada) {
    setAbierta(abierta === notificacion.id ? null : notificacion.id);
    if (fechaCopia !== null || notificacion.leida) return;
    const actual = solicitud.current;
    const generation = getSessionGeneration();
    const vigente = () => actual === solicitud.current && generation === getSessionGeneration();
    try {
      const leida = await abrirNotificacion(notificacion.id);
      if (!vigente()) return;
      setData((previous) =>
        previous
          ? { ...previous, content: previous.content.map((item) => (item.id === leida.id ? leida : item)) }
          : previous,
      );
    } catch (cause) {
      if (vigente()) setError(cause instanceof Error ? cause.message : 'No se pudo abrir la notificación.');
    }
  }

  const lastPage = data ? Math.max(0, Math.ceil(data.totalElements / PAGE_SIZE) - 1) : 0;

  const anterior = paginasGuardadas.filter((numero) => numero < page).at(-1);
  const siguiente = paginasGuardadas.find((numero) => numero > page);

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
      {fechaCopia !== null ? (
        <HelperText>
          Datos guardados. Última descarga: {formatDate(new Date(fechaCopia).toISOString())}.
          El estado de lectura puede haber cambiado. Solo se muestran las páginas descargadas.
        </HelperText>
      ) : null}
      {error && !data ? <PrimaryButton title="Reintentar" onPress={() => setAttempt((value) => value + 1)} /> : null}
      {!loading && data?.content.length === 0 ? (
        <HelperText>{soloNoLeidas ? 'No tienes notificaciones sin leer.' : 'No tienes notificaciones.'}</HelperText>
      ) : null}
      {data?.content.map((notificacion) => {
        const enLinea = fechaCopia === null && 'tipo' in notificacion ? notificacion : null;
        const destino = role && enLinea ? destinoNotificacion(enLinea, role) : null;
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
                    title={enLinea?.referencia?.tipo === 'DOMICILIO' ? 'Ver domicilio' : 'Ver pedido'}
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
      {!loading && !data && anterior !== undefined ? (
        <TextButton title="Página guardada anterior" onPress={() => setPage(anterior)} />
      ) : null}
      {!loading && !data && siguiente !== undefined ? (
        <TextButton title="Página guardada siguiente" onPress={() => setPage(siguiente)} />
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
  unread: { backgroundColor: '#F7EBDD', borderColor: '#B9D9C6' },
  row: { alignItems: 'center', flexDirection: 'row' },
  dot: { backgroundColor: palette.green, borderRadius: 5, height: 10, marginRight: 8, width: 10 },
  title: { color: palette.dark, flex: 1, fontFamily: fonts.sans, fontSize: 15, fontWeight: '600' },
  titleUnread: { fontWeight: '800' },
  date: { color: palette.muted, fontFamily: fonts.sans, fontSize: 13, marginTop: 4 },
  body: { color: palette.dark, fontFamily: fonts.sans, fontSize: 14, lineHeight: 21, marginTop: 8 },
  pager: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between', marginVertical: 8 },
});
