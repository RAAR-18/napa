import { useState } from 'react';
import { router } from 'expo-router';

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
import {
  changePassword,
  clearSession,
} from '../lib/auth';

export default function ChangePasswordScreen() {
  const [contrasenaActual, setContrasenaActual] = useState('');
  const [contrasenaNueva, setContrasenaNueva] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  async function submit() {
    setError(null);
    setMessage(null);
    setBusy(true);
    try {
      await changePassword(contrasenaActual, contrasenaNueva);
      setMessage('Tu contraseña se actualizó correctamente.');
      setContrasenaActual('');
      setContrasenaNueva('');
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        await clearSession();
        router.replace('/login');
        return;
      }
      setError(
        cause instanceof Error
          ? cause.message
          : 'No se pudo cambiar la contraseña.',
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Seguridad"
        title="Cambia tu contraseña"
        description="Confirma la contraseña actual y elige una nueva."
      />
      <ErrorBanner message={error} />
      {message ? <HelperText>{message}</HelperText> : null}
      <Field
        autoCapitalize="none"
        label="Contraseña actual"
        onChangeText={setContrasenaActual}
        placeholder="Tu contraseña actual"
        secureTextEntry
        value={contrasenaActual}
      />
      <Field
        autoCapitalize="none"
        label="Nueva contraseña"
        maxLength={128}
        onChangeText={setContrasenaNueva}
        onSubmitEditing={submit}
        placeholder="Mínimo 8 caracteres"
        returnKeyType="go"
        secureTextEntry
        value={contrasenaNueva}
      />
      <HelperText>Debe tener entre 8 y 128 caracteres.</HelperText>
      <PrimaryButton
        busy={busy}
        disabled={!contrasenaActual || contrasenaNueva.length < 8}
        title="Actualizar contraseña"
        onPress={submit}
      />
      <FormFooter>
        <TextButton
          title="Volver a mi sesión"
          onPress={() => router.replace('/session')}
        />
      </FormFooter>
    </AuthPage>
  );
}
