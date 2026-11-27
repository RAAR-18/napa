import { useState } from 'react';
import { router } from 'expo-router';

import {
  AuthPage,
  ErrorBanner,
  PageHeading,
  PrimaryButton,
} from '../components/AuthUI';
import { CelularField } from '../components/CelularField';
import { CanalVerificacionSelector } from '../components/CanalVerificacionSelector';
import { requestVerification, type VerificationChannel } from '../lib/auth';

export default function RecoverScreen() {
  const [celular, setCelular] = useState('');
  const [canal, setCanal] = useState<VerificationChannel>('SMS');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    setError(null);
    setBusy(true);
    try {
      const espera = await requestVerification(celular.trim(), 'RECUPERACION_CONTRASENA', canal);
      router.push({
        pathname: '/verify',
        params: {
          celular: celular.trim(),
          proposito: 'RECUPERACION_CONTRASENA',
          canal,
          espera: String(espera),
          tieneCorreo: '1',
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
        description="Te enviaremos un código por SMS o al correo de tu cuenta para crear una contraseña nueva."
      />
      <ErrorBanner message={error} />
      <CelularField label="Celular registrado" onChange={setCelular} />
      <CanalVerificacionSelector onChange={setCanal} value={canal} />
      <PrimaryButton
        busy={busy}
        disabled={!celular.trim()}
        title="Enviar código"
        onPress={submit}
      />
    </AuthPage>
  );
}
