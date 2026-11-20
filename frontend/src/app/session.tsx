import { fonts } from '../components/theme';
import { useCallback, useEffect, useState } from 'react';
import { router } from 'expo-router';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  FormFooter,
  PageHeading,
  PrimaryButton,
  TextButton,
  palette,
} from '../components/AuthUI';
import { ApiError, NetworkError } from '../lib/api';
import {
  clearSession,
  getValidSession,
  logout,
  readSession,
  type StoredSession,
} from '../lib/auth';
import { useAccionesPendientes } from '../hooks/useOutbox';
import { contarNoLeidas } from '../lib/notificaciones';
import { darDeBajaPush, registrarPush } from '../lib/push';
import { borrarTodasLasRutas } from '../lib/ruta-cache';
import { CardGrid, ShortcutCard, StatusBadge, type IconName } from '../components/NapaUI';

const roleNames: Record<string, string> = {
  CLIENTE: 'Cliente',
  VENDEDOR_AMBULANTE: 'Vendedor ambulante',
  VENDEDOR_PUNTO_FIJO: 'Vendedor de punto fijo',
  DOMICILIARIO: 'Domiciliario',
  ADMINISTRADOR: 'Administrador',
};

export default function SessionScreen() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [noLeidas, setNoLeidas] = useState<number | null>(null);
  const pendientesEnvio = useAccionesPendientes();

  const handleLoadError = useCallback(async (cause: unknown) => {
    if (cause instanceof ApiError && cause.status === 401) {
      await clearSession();
      router.replace('/login');
      return;
    }
    setError(cause instanceof Error ? cause.message : 'No se pudo recuperar la sesión.');
  }, []);

  useEffect(() => {
    let active = true;
    getValidSession()
      .catch(async (cause: unknown) => {
        if (!(cause instanceof NetworkError)) throw cause;
        const local = await readSession();
        if (local) return local;
        throw cause;
      })
      .then((validSession) => {
        if (active) {
          setSession(validSession);
          // El push es opcional: si no está disponible, la lista de notificaciones sigue funcionando.
          void registrarPush();
          contarNoLeidas()
            .then((total) => {
              if (active) setNoLeidas(total);
            })
            .catch(() => undefined);
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          return handleLoadError(cause);
        }
        return undefined;
      })
      .finally(() => {
        if (active) {
          setBusy(false);
        }
      });
    return () => {
      active = false;
    };
  }, [handleLoadError]);

  const retry = useCallback(async () => {
    setError(null);
    setBusy(true);
    try {
      setSession(await getValidSession());
    } catch (cause) {
      await handleLoadError(cause);
    } finally {
      setBusy(false);
    }
  }, [handleLoadError]);

  async function signOut() {
    setError(null);
    setBusy(true);
    try {
      await darDeBajaPush();
      await logout();
      await borrarTodasLasRutas().catch(() => undefined);
      // Los pendientes se conservan por propietario; otra cuenta no puede enviarlos.
      setSession(null);
      router.replace('/');
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        await clearSession();
        router.replace('/login');
        return;
      }
      setError(cause instanceof Error ? cause.message : 'No se pudo cerrar la sesión.');
    } finally {
      setBusy(false);
    }
  }

  if (busy && !session) {
    return (
      <AuthPage>
        <PageHeading
          eyebrow="Sesión"
          title="Cargando tu cuenta"
          description="Estamos comprobando tu sesión segura."
        />
        <ActivityIndicator color={palette.green} />
      </AuthPage>
    );
  }

  const shortcuts: { title: string; description: string; icon: IconName; route: string }[] = [];
  const add = (title: string, description: string, icon: IconName, route: string) =>
    shortcuts.push({ title, description, icon, route });
  if (session?.rol.startsWith('VENDEDOR')) {
    add(
      'Pedidos recibidos',
      'Recibe, organiza y acompaña cada venta.',
      'shopping-bag',
      '/pedidos-vendedor',
    );
    add('Mis productos', 'Tu oferta, tus precios y tu inventario.', 'box', '/mis-productos');
    add(
      'Mi emprendimiento',
      'Comparte la historia y los datos de tu negocio.',
      'home',
      '/mi-emprendimiento',
    );
    add(
      'Configurar métodos de pago',
      'Elige cómo recibir el pago de tus pedidos.',
      'credit-card',
      '/configuracion-pagos',
    );
    if (session.rol === 'VENDEDOR_PUNTO_FIJO')
      add(
        'Mis domicilios',
        'Sigue las entregas de tu emprendimiento.',
        'truck',
        '/domicilios-vendedor',
      );
  } else if (session?.rol === 'CLIENTE') {
    add(
      'Explorar emprendimientos',
      'Encuentra lo mejor de las personas de tu barrio.',
      'compass',
      '/emprendimientos',
    );
    add('Mis pedidos', 'Consulta tus compras, reservas y entregas.', 'package', '/mis-pedidos');
  } else if (session?.rol === 'DOMICILIARIO') {
    add(
      'Domicilios disponibles',
      'Elige tu próxima entrega cerca de ti.',
      'navigation',
      '/domicilios-disponibles',
    );
    add(
      'Mis domicilios en curso',
      'Cada entrega, acompañada de principio a fin.',
      'package',
      '/domicilios-en-curso',
    );
    add(
      'Mi cuenta para cobrar',
      'Mantén al día la cuenta donde recibes tus pagos.',
      'credit-card',
      '/cuenta-domiciliario',
    );
  } else if (session?.rol === 'ADMINISTRADOR') {
    add(
      'Supervisar domicilios',
      'Una mirada completa a las entregas del barrio.',
      'truck',
      '/domicilios-admin',
    );
    add(
      'Reportes',
      'Escucha a la comunidad y resuelve sus inquietudes.',
      'bar-chart-2',
      '/reportes-admin',
    );
    add(
      'Usuarios',
      'Consulta y administra las cuentas de la comunidad.',
      'users',
      '/usuarios-admin',
    );
    add(
      'Calificaciones y comentarios',
      'Cuida las conversaciones y la confianza.',
      'star',
      '/moderacion-admin',
    );
  }
  if (session) {
    add(
      noLeidas ? `Notificaciones (${noLeidas} sin leer)` : 'Notificaciones',
      'Lo que pasa con tus pedidos y tu comunidad.',
      'bell',
      '/notificaciones',
    );
    add(
      session.rol === 'ADMINISTRADOR' ? 'Historial general de pagos' : 'Historial de pagos',
      'Consulta el registro de tus cobros y pagos.',
      'layers',
      '/historial-pagos',
    );
    if (session.rol !== 'ADMINISTRADOR')
      add(
        'Mis reportes',
        'Encuentra ayuda y consulta tus solicitudes.',
        'message-circle',
        '/mis-reportes',
      );
  }
  return (
    <AuthPage>
      <PageHeading
        eyebrow="Mi cuenta"
        title="¡Bienvenido a Ñapa!"
        description="Un lugar para tu negocio, tus compras y todo lo que compartes con tu barrio."
      />
      {session ? (
        <View style={styles.card}>
          <View>
            <Text style={styles.label}>TU ESPACIO EN LA COMUNIDAD</Text>
            <Text style={styles.role}>{roleNames[session.rol] ?? session.rol}</Text>
          </View>
          <StatusBadge label="Sesión activa" />
        </View>
      ) : null}
      {pendientesEnvio.length > 0 ? (
        <PrimaryButton
          title={`Pendientes de envío (${pendientesEnvio.length})`}
          onPress={() => router.push('/pendientes-envio')}
        />
      ) : null}
      <CardGrid>
        {shortcuts.map((item) => (
          <ShortcutCard
            key={item.route}
            {...item}
            onPress={() => router.push(item.route as never)}
          />
        ))}
      </CardGrid>
      <ErrorBanner message={error} />
      {error ? <PrimaryButton busy={busy} title="Reintentar" onPress={() => void retry()} /> : null}
      <View style={styles.accountActions}>
        <TextButton title="Mi perfil" onPress={() => router.push('/perfil')} />
        <TextButton title="Cambiar contraseña" onPress={() => router.push('/change-password')} />
      </View>
      <FormFooter>
        <TextButton
          disabled={busy}
          title={busy ? 'Cerrando sesión…' : 'Cerrar sesión'}
          onPress={signOut}
        />
      </FormFooter>
    </AuthPage>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#FFFFFF',
    borderColor: '#DBC9BD',
    borderRadius: 16,
    borderWidth: 1,
    marginBottom: 20,
    padding: 22,
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 16,
  },
  accountActions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    gap: 16,
    borderTopWidth: 1,
    borderColor: palette.border,
    marginTop: 30,
    paddingTop: 18,
  },
  label: {
    color: palette.muted,
    fontFamily: fonts.sans,
    fontSize: 13,
    fontWeight: '700',
  },
  role: {
    color: palette.dark,
    fontFamily: fonts.display,
    fontSize: 27,
    fontWeight: '800',
    marginTop: 5,
  },
  note: {
    color: palette.muted,
    fontFamily: fonts.sans,
    fontSize: 14,
    lineHeight: 21,
    marginTop: 12,
  },
});
