import { useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  Field,
  HelperText,
  PageHeading,
  PrimaryButton,
} from '../components/AuthUI';
import { AuthAction as LoginAction } from '../components/AuthAction';
import { ApiError } from '../lib/api';
import { login } from '../lib/auth';
import { replaceAuthHistory } from '../lib/auth-navigation';
import { homeForRole } from '../components/AppShell';

export default function LoginScreen() {
  const params = useLocalSearchParams<{ passwordReset?: string }>();
  const [identificador, setIdentificador] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    setError(null);
    setBusy(true);
    try {
      const session = await login(identificador.trim(), password);
      replaceAuthHistory(homeForRole(session.rol));
    } catch (cause) {
      if (
        cause instanceof ApiError &&
        cause.code === 'ACCOUNT_NOT_VERIFIED' &&
        typeof cause.details.celular === 'string'
      ) {
        // Cuenta creada pero sin verificar: se lleva a elegir SMS o correo y verificarla.
        router.push({
          pathname: '/verify',
          params: {
            celular: cause.details.celular,
            proposito: 'REGISTRO',
            tieneCorreo: cause.details.tieneCorreo === true ? '1' : '0',
          },
        });
        return;
      }
      setError(
        cause instanceof Error
          ? cause.message
          : 'No se pudo iniciar sesión. Inténtalo de nuevo.',
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Qué bueno verte"
        title="Inicia sesión"
        description="Ingresa con el celular o correo y la contraseña de tu cuenta."
      />
      {params.passwordReset === 'true' ? (
        <HelperText>Tu contraseña fue actualizada. Ya puedes iniciar sesión.</HelperText>
      ) : null}
      <ErrorBanner message={error} />
      <Field
        autoCapitalize="none"
        autoCorrect={false}
        keyboardType="email-address"
        label="Celular o correo"
        onChangeText={setIdentificador}
        placeholder="+573001234567 o nombre@correo.com"
        returnKeyType="next"
        value={identificador}
      />
      <Field
        autoCapitalize="none"
        label="Contraseña"
        onChangeText={setPassword}
        onSubmitEditing={submit}
        placeholder="Tu contraseña"
        returnKeyType="go"
        secureTextEntry
        showPasswordToggle
        value={password}
      />
      <View style={styles.recovery}>
        <LoginAction
          title="Olvidé mi contraseña"
          icon="key"
          onPress={() => router.push('/recover')}
        />
      </View>
      <PrimaryButton
        busy={busy}
        disabled={!identificador.trim() || !password}
        title="Iniciar sesión"
        onPress={submit}
      />
      <View style={styles.secondaryActions}>
        <LoginAction
          title="¿Tienes problemas para iniciar sesión? Verifica tu cuenta aquí"
          icon="shield"
          onPress={() => router.push({ pathname: '/verify', params: { proposito: 'REGISTRO' } })}
        />
        <LoginAction
          title="Crear cuenta"
          icon="user-plus"
          onPress={() => router.push('/register')}
        />
      </View>
    </AuthPage>
  );
}

const styles = StyleSheet.create({
  recovery: { alignItems: 'center', marginBottom: 18 },
  secondaryActions: { alignItems: 'center', marginTop: 21, gap: 18 },
});
