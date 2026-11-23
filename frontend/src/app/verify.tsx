import { useLocalSearchParams, router } from 'expo-router';
import { useEffect, useState } from 'react';

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

function formatoEspera(segundos: number): string {
  const minutos = Math.floor(segundos / 60);
  const resto = segundos % 60;
  return `${minutos}:${String(resto).padStart(2, '0')}`;
}

export default function VerifyScreen() {
  const params = useLocalSearchParams<{
    celular?: string;
    proposito?: string;
    canal?: string;
    espera?: string;
    tieneCorreo?: string;
  }>();
  // Sin celular en la ruta (botón "¿Tienes problemas para iniciar sesión?") se le pide aquí.
  const celularRuta = typeof params.celular === 'string' ? params.celular : '';
  const [celularEscrito, setCelularEscrito] = useState('');
  const celular = celularRuta || celularEscrito.trim();
  const proposito: VerificationPurpose =
    params.proposito === 'RECUPERACION_CONTRASENA'
      ? 'RECUPERACION_CONTRASENA'
      : 'REGISTRO';
  const isRecovery = proposito === 'RECUPERACION_CONTRASENA';
  // Si no se sabe, se ofrece el correo; el backend avisa si la cuenta no tiene uno.
  const tieneCorreo = params.tieneCorreo !== '0';
  const esperaInicial = Number(params.espera);
  // En la recuperación el código ya salió; en el registro primero se pregunta el medio.
  const [canalEnviado, setCanalEnviado] = useState<VerificationChannel | null>(
    isRecovery ? (params.canal === 'CORREO' ? 'CORREO' : 'SMS') : null,
  );
  const [canal, setCanal] = useState<VerificationChannel>(
    params.canal === 'CORREO' ? 'CORREO' : 'SMS',
  );
  const [espera, setEspera] = useState(
    Number.isFinite(esperaInicial) && esperaInicial > 0 ? Math.round(esperaInicial) : 0,
  );
  const [codigo, setCodigo] = useState('');
  const [contrasenaNueva, setContrasenaNueva] = useState('');
  const [busy, setBusy] = useState(false);
  const [sending, setSending] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (espera <= 0) {
      return undefined;
    }
    const timer = setTimeout(() => setEspera((actual) => Math.max(0, actual - 1)), 1000);
    return () => clearTimeout(timer);
  }, [espera]);

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

  async function send() {
    if (canal === 'CORREO' && !tieneCorreo) {
      setError('Tu cuenta no tiene correo. Elige recibir el código por SMS.');
      return;
    }
    setError(null);
    setMessage(null);
    setSending(true);
    try {
      const segundos = await requestVerification(celular, proposito, canal);
      setCanalEnviado(canal);
      setEspera(segundos);
      setMessage(`Te enviamos un código por ${describirCanal(canal)}.`);
    } catch (cause) {
      const retry =
        cause instanceof ApiError && cause.code === 'RATE_LIMITED'
          ? Number(cause.details.retryAfter)
          : 0;
      if (retry > 0) {
        setEspera(Math.round(retry));
      }
      setError(
        cause instanceof Error ? cause.message : 'No se pudo enviar el código.',
      );
    } finally {
      setSending(false);
    }
  }

  const selector = (
    <>
      <CanalVerificacionSelector
        label={
          canalEnviado
            ? '¿No te llegó? Pide otro código'
            : '¿Prefieres verificar tu cuenta por SMS o por correo?'
        }
        onChange={setCanal}
        value={canal}
      />
      {canal === 'CORREO' && !tieneCorreo ? (
        <HelperText>
          No registraste un correo, así que el código solo puede llegar por SMS.
        </HelperText>
      ) : null}
    </>
  );

  if (!canalEnviado) {
    return (
      <AuthPage>
        <PageHeading
          eyebrow="Verifica tu cuenta"
          title="¿Cómo quieres recibir tu código?"
          description={
            celularRuta
              ? `Tu cuenta quedó creada. Para activarla te enviaremos un código de 6 dígitos por SMS al ${celular} o a tu correo.`
              : 'Escribe el celular con el que creaste tu cuenta y elige cómo recibir el código para activarla.'
          }
        />
        <ErrorBanner message={error} />
        {celularRuta ? null : (
          <Field
            autoCapitalize="none"
            keyboardType="phone-pad"
            label="Celular de tu cuenta"
            maxLength={16}
            onChangeText={(value) => setCelularEscrito(value.replace(/[^0-9+]/g, ''))}
            placeholder="3001234567"
            value={celularEscrito}
          />
        )}
        {selector}
        <PrimaryButton
          busy={sending}
          disabled={
            espera > 0 || celular.length < 10 || (canal === 'CORREO' && !tieneCorreo)
          }
          title={
            espera > 0
              ? `Espera ${formatoEspera(espera)}`
              : `Enviar código por ${describirCanal(canal)}`
          }
          onPress={send}
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

  const destino =
    canalEnviado === 'CORREO' ? 'a tu correo' : `por SMS al ${celular}`;

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
      {selector}
      {espera > 0 ? (
        <HelperText>
          {`Podrás pedir otro código en ${formatoEspera(espera)}. Cada nuevo envío duplica la espera.`}
        </HelperText>
      ) : null}
      <FormFooter>
        <TextButton
          disabled={sending || busy || espera > 0 || (canal === 'CORREO' && !tieneCorreo)}
          title={
            sending
              ? 'Enviando código…'
              : `Enviar otro código por ${describirCanal(canal)}`
          }
          onPress={send}
        />
        <TextButton
          title="Volver al inicio de sesión"
          onPress={() => router.replace('/login')}
        />
      </FormFooter>
    </AuthPage>
  );
}
