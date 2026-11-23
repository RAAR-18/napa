import { useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';

import {
  AuthPage,
  ErrorBanner,
  Field,
  FormFooter,
  HelperText,
  PageHeading,
  PrimaryButton,
  TextButton,
} from '../components/AuthUI';
import { ApiError } from '../lib/api';
import { login } from '../lib/auth';
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
      router.replace(homeForRole(session.rol));
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
        value={password}
      />
      <TextButton
        title="Olvidé mi contraseña"
        onPress={() => router.push('/recover')}
      />
      <PrimaryButton
        busy={busy}
        disabled={!identificador.trim() || !password}
        title="Iniciar sesión"
        onPress={submit}
      />
      <FormFooter>
        <TextButton
          title="¿Tienes problemas para iniciar sesión? Verifica tu cuenta aquí"
          onPress={() => router.push({ pathname: '/verify', params: { proposito: 'REGISTRO' } })}
        />
        <TextButton
          title="¿Aún no tienes cuenta? Regístrate"
          onPress={() => router.push('/register')}
        />
        <TextButton title="Volver" onPress={() => router.replace('/')} />
      </FormFooter>
    </AuthPage>
  );
}
