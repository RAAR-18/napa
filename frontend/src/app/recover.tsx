import { useState } from 'react';
import { router } from 'expo-router';

import {
  AuthPage,
  ErrorBanner,
  Field,
  FormFooter,
  PageHeading,
  PrimaryButton,
  TextButton,
} from '../components/AuthUI';
import { requestVerification } from '../lib/auth';

export default function RecoverScreen() {
  const [celular, setCelular] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    setError(null);
    setBusy(true);
    try {
      await requestVerification(celular.trim(), 'RECUPERACION_CONTRASENA');
      router.push({
        pathname: '/verify',
        params: {
          celular: celular.trim(),
          proposito: 'RECUPERACION_CONTRASENA',
        },
      });
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : 'No se pudo solicitar el código de recuperación.',
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Recupera tu cuenta"
        title="Restablece tu contraseña"
        description="Te enviaremos un código al celular registrado para crear una contraseña nueva."
      />
      <ErrorBanner message={error} />
      <Field
        autoCapitalize="none"
        keyboardType="phone-pad"
        label="Celular registrado"
        onChangeText={setCelular}
        placeholder="+573001234567"
        value={celular}
      />
      <PrimaryButton
        busy={busy}
        disabled={!celular.trim()}
        title="Enviar código"
        onPress={submit}
      />
      <FormFooter>
        <TextButton
          title="Volver al inicio de sesión"
          onPress={() => router.replace('/login')}
        />
      </FormFooter>
    </AuthPage>
  );
}
