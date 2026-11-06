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
import { ApiError } from '../lib/api';
import {
  clearSession,
  getValidSession,
  logout,
  type StoredSession,
} from '../lib/auth';

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

  const handleLoadError = useCallback(async (cause: unknown) => {
    if (cause instanceof ApiError && cause.status === 401) {
      await clearSession();
      router.replace('/login');
      return;
    }
    setError(
      cause instanceof Error
        ? cause.message
        : 'No se pudo recuperar la sesión.',
    );
  }, []);

  useEffect(() => {
    let active = true;
    getValidSession()
      .then((validSession) => {
        if (active) {
          setSession(validSession);
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
      const validSession = await getValidSession();
      await logout(validSession);
      setSession(null);
      router.replace('/');
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        await clearSession();
        router.replace('/login');
        return;
      }
      setError(
        cause instanceof Error
          ? cause.message
          : 'No se pudo cerrar la sesión.',
      );
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

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Sesión activa"
        title="¡Bienvenido a Ñapa!"
        description="Tu cuenta ya está conectada con el servicio."
      />
      {session ? (
        <View style={styles.card}>
          <Text style={styles.label}>Perfil</Text>
          <Text style={styles.role}>
            {roleNames[session.rol] ?? session.rol}
          </Text>
          <Text style={styles.note}>
            La aplicación ya puede autenticarse con la API. Las demás funciones
            se irán habilitando en las próximas pantallas.
          </Text>
        </View>
      ) : null}
      <ErrorBanner message={error} />
      {error ? (
        <PrimaryButton busy={busy} title="Reintentar" onPress={() => void retry()} />
      ) : null}
      <TextButton
        title="Cambiar contraseña"
        onPress={() => router.push('/change-password')}
      />
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
    borderColor: '#D9E2DC',
    borderRadius: 16,
    borderWidth: 1,
    marginBottom: 20,
    padding: 20,
  },
  label: {
    color: palette.muted,
    fontSize: 13,
    fontWeight: '700',
  },
  role: {
    color: palette.dark,
    fontSize: 22,
    fontWeight: '800',
    marginTop: 5,
  },
  note: {
    color: palette.muted,
    fontSize: 14,
    lineHeight: 21,
    marginTop: 12,
  },
});
