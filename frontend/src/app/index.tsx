import { useEffect, useState } from 'react';
import { router } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { AuthPage, ErrorBanner } from '../components/AuthUI';
import { Icon } from '../components/NapaUI';
import { fonts, palette } from '../components/theme';
import WelcomeContent from '../components/WelcomeContent';
import { readSession } from '../lib/auth';
import { homeForRole } from '../components/AppShell';

export default function WelcomeScreen() {
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    readSession()
      .then((session) => {
        if (active && session) {
          router.replace(homeForRole(session.rol));
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
        <View style={styles.actions}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Crear cuenta"
            onPress={() => router.push('/register')}
            style={({ pressed, hovered }: { pressed: boolean; hovered?: boolean }) => [
              styles.loginButton,
              hovered && styles.loginHovered,
              pressed && styles.loginPressed,
            ]}
          >
            <View style={styles.loginIcon}>
              <Icon name="user-plus" color={palette.plum} size={22} />
            </View>
            <View style={styles.loginCopy}>
              <Text style={styles.loginCaption}>Soy nuevo en Ñapa</Text>
              <Text style={styles.loginTitle}>Crear cuenta</Text>
            </View>
            <Icon name="arrow-right" color="#FFFFFF" size={22} />
          </Pressable>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Ya tengo una cuenta · Iniciar sesión"
            accessibilityHint="Abre el formulario para entrar a tu cuenta"
            onPress={() => router.push('/login')}
            style={({ pressed, hovered }: { pressed: boolean; hovered?: boolean }) => [
              styles.loginButton,
              hovered && styles.loginHovered,
              pressed && styles.loginPressed,
            ]}
          >
            <View style={styles.loginIcon}>
              <Icon name="log-in" color={palette.plum} size={22} />
            </View>
            <View style={styles.loginCopy}>
              <Text style={styles.loginCaption}>Ya tengo una cuenta</Text>
              <Text style={styles.loginTitle}>Iniciar sesión</Text>
            </View>
            <Icon name="arrow-right" color="#FFFFFF" size={22} />
          </Pressable>
        </View>
      </View>
    </AuthPage>
  );
}

const styles = StyleSheet.create({
  content: {
    flexGrow: 1,
    justifyContent: 'center',
  },
  actions: { gap: 14 },
  loginButton: {
    backgroundColor: palette.coral,
    borderWidth: 1,
    borderColor: palette.coral,
    borderBottomWidth: 3,
    borderBottomColor: palette.coralDark,
    borderRadius: 16,
    minHeight: 74,
    paddingHorizontal: 16,
    paddingVertical: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    overflow: 'hidden',
  },
  loginHovered: { backgroundColor: palette.coralDark, borderColor: palette.coralDark },
  loginPressed: { backgroundColor: palette.coralDark, transform: [{ scale: 0.985 }] },
  loginIcon: {
    width: 42,
    height: 42,
    backgroundColor: palette.peach,
    borderRadius: 13,
    alignItems: 'center',
    justifyContent: 'center',
  },
  loginCopy: { flex: 1 },
  loginCaption: { color: '#FFFFFF', fontFamily: fonts.sans, fontSize: 11, lineHeight: 16 },
  loginTitle: {
    color: '#FFFFFF',
    fontFamily: fonts.sans,
    fontSize: 17,
    fontWeight: '700',
    lineHeight: 23,
  },
});
