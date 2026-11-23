import { useLocalSearchParams, router } from 'expo-router';
import { useState } from 'react';

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
import {
  CanalVerificacionSelector,
  describirCanal,
} from '../components/CanalVerificacionSelector';
import { ApiError } from '../lib/api';
import {
  confirmRegistration,
  requestVerification,
  resetPassword,
  type VerificationChannel,
  type VerificationPurpose,
} from '../lib/auth';

export default function VerifyScreen() {
  const params = useLocalSearchParams<{
    celular?: string;
    proposito?: string;
    canal?: string;
    envio?: string;
  }>();
  const celular = typeof params.celular === 'string' ? params.celular : '';
  const proposito: VerificationPurpose =
    params.proposito === 'RECUPERACION_CONTRASENA'
      ? 'RECUPERACION_CONTRASENA'
      : 'REGISTRO';
  const isRecovery = proposito === 'RECUPERACION_CONTRASENA';
  const [canal, setCanal] = useState<VerificationChannel>(
    params.canal === 'CORREO' ? 'CORREO' : 'SMS',
  );
  const [canalEnviado, setCanalEnviado] = useState<VerificationChannel>(canal);
  const [codigo, setCodigo] = useState('');
  const [contrasenaNueva, setContrasenaNueva] = useState('');
  const [busy, setBusy] = useState(false);
  const [resending, setResending] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(
    params.envio === 'FALLIDO'
      ? `No pudimos enviar el código por ${describirCanal(canal)}. Solicítalo de nuevo o elige el otro medio.`
      : null,
  );

  async function submit() {
    setError(null);
    setMessage(null);
    setBusy(true);
    try {
      if (isRecovery) {
        await resetPassword(celular, codigo, contrasenaNueva);
        router.replace({ pathname: '/login', params: { passwordReset: 'true' } });
      } else {
        await confirmRegistration(celular, codigo);
        router.replace('/session');
      }
    } catch (cause) {
      const attempts =
        cause instanceof ApiError && cause.code === 'VALIDATION_ERROR'
          ? ' Revisa el código de seis dígitos o solicita otro código.'
          : '';
      setError(
        cause instanceof Error
          ? `${cause.message}${attempts}`
          : 'No se pudo verificar el código. Inténtalo de nuevo.',
      );
    } finally {
      setBusy(false);
    }
  }

  async function resend() {
    setError(null);
    setMessage(null);
    setResending(true);
    try {
      await requestVerification(celular, proposito, canal);
      setCanalEnviado(canal);
      setMessage(`Te enviamos un código nuevo por ${describirCanal(canal)}.`);
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : 'No se pudo solicitar otro código.',
      );
    } finally {
      setResending(false);
    }
  }

  if (!celular) {
    return (
      <AuthPage>
        <PageHeading
          eyebrow="Verificación"
          title="No encontramos el celular"
          description="Vuelve al registro o a la recuperación para solicitar un código."
        />
        <TextButton title="Volver al inicio" onPress={() => router.replace('/')} />
      </AuthPage>
    );
  }

  const destino =
    canalEnviado === 'CORREO' ? 'al correo de tu cuenta' : `por SMS al ${celular}`;

  return (
    <AuthPage>
      <PageHeading
        eyebrow={isRecovery ? 'Recuperación segura' : 'Verifica tu cuenta'}
        title={isRecovery ? 'Crea una contraseña nueva' : 'Ingresa tu código'}
        description={
          isRecovery
            ? `Escribe el código que enviamos ${destino} y elige una nueva contraseña.`
            : `Tu cuenta quedará activa cuando escribas el código que enviamos ${destino}.`
        }
      />
      <ErrorBanner message={error} />
      {message ? <HelperText>{message}</HelperText> : null}
      <Field
        autoCapitalize="none"
        keyboardType="number-pad"
        label="Código de verificación"
        maxLength={6}
        onChangeText={(value) => setCodigo(value.replace(/[^0-9]/g, ''))}
        placeholder="Código de 6 dígitos"
        value={codigo}
      />
      {isRecovery ? (
        <>
          <Field
            autoCapitalize="none"
            label="Nueva contraseña"
            maxLength={128}
            onChangeText={setContrasenaNueva}
            placeholder="Mínimo 8 caracteres"
            secureTextEntry
            value={contrasenaNueva}
          />
          <HelperText>Debe tener entre 8 y 128 caracteres.</HelperText>
        </>
      ) : null}
      <PrimaryButton
        busy={busy}
        disabled={
          codigo.length !== 6 ||
          (isRecovery && contrasenaNueva.length < 8)
        }
        title={isRecovery ? 'Actualizar contraseña' : 'Verificar y continuar'}
        onPress={submit}
      />
      <CanalVerificacionSelector
        label="¿No te llegó? Pide otro código"
        onChange={setCanal}
        value={canal}
      />
      <HelperText>
        Si eliges correo, el código llega al correo registrado en tu cuenta.
      </HelperText>
      <FormFooter>
        <TextButton
          disabled={resending || busy}
          title={resending ? 'Solicitando código…' : `Enviar otro código por ${describirCanal(canal)}`}
          onPress={resend}
        />
        <TextButton
          title="Volver al inicio de sesión"
          onPress={() => router.replace('/login')}
        />
      </FormFooter>
    </AuthPage>
  );
}
