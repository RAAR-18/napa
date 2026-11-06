import { useEffect, useState } from 'react';
import { router } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  PrimaryButton,
  TextButton,
} from '../components/AuthUI';
import WelcomeContent from '../components/WelcomeContent';
import { readSession } from '../lib/auth';

export default function WelcomeScreen() {
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    readSession()
      .then((session) => {
        if (active && session) {
          router.replace('/session');
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setError(
            cause instanceof Error
              ? cause.message
              : 'No se pudo leer la sesión guardada.',
          );
        }
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <AuthPage>
      <View style={styles.content}>
        <WelcomeContent />
        <ErrorBanner message={error} />
        <PrimaryButton
          title="Crear cuenta"
          onPress={() => router.push('/register')}
        />
        <TextButton
          title="Ya tengo una cuenta · Iniciar sesión"
          onPress={() => router.push('/login')}
        />
      </View>
    </AuthPage>
  );
}

const styles = StyleSheet.create({
  content: {
    flexGrow: 1,
    justifyContent: 'center',
  },
});
