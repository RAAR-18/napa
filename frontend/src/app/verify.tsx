import { useLocalSearchParams, router } from 'expo-router';
import { useState } from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';

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
  confirmRegistration,
  requestVerification,
  resetPassword,
  verificationCodeImageUrl,
  type VerificationPurpose,
} from '../lib/auth';

export default function VerifyScreen() {
  const params = useLocalSearchParams<{
    celular?: string;
    proposito?: string;
    mensajeId?: string;
  }>();
  const celular = typeof params.celular === 'string' ? params.celular : '';
  const proposito: VerificationPurpose =
    params.proposito === 'RECUPERACION_CONTRASENA'
      ? 'RECUPERACION_CONTRASENA'
      : 'REGISTRO';
  const isRecovery = proposito === 'RECUPERACION_CONTRASENA';
  const [mensajeId, setMensajeId] = useState<string | null>(
    typeof params.mensajeId === 'string' && params.mensajeId ? params.mensajeId : null,
  );
  const [imagenDisponible, setImagenDisponible] = useState(true);
  const [codigo, setCodigo] = useState('');
  const [contrasenaNueva, setContrasenaNueva] = useState('');
  const [busy, setBusy] = useState(false);
  const [resending, setResending] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

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
      const nuevoMensajeId = await requestVerification(celular, proposito);
      if (nuevoMensajeId) {
        setMensajeId(nuevoMensajeId);
        setImagenDisponible(true);
      }
      setMessage('Solicitamos un nuevo código para tu celular.');
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

  return (
    <AuthPage>
      <PageHeading
        eyebrow={isRecovery ? 'Recuperación segura' : 'Verifica tu celular'}
        title={isRecovery ? 'Crea una contraseña nueva' : 'Ingresa tu código'}
        description={
          isRecovery
            ? `Confirma el código enviado a ${celular} y elige una nueva contraseña.`
            : `La cuenta quedará activa cuando confirmes el código enviado a ${celular}.`
        }
      />
      <ErrorBanner message={error} />
      {message ? <HelperText>{message}</HelperText> : null}
      {!isRecovery && mensajeId && imagenDisponible ? (
        <View style={styles.codigoImagen}>
          <Text style={styles.codigoTitulo}>Digita este código para activar tu cuenta</Text>
          <Image
            accessibilityLabel="Imagen con el código de verificación"
            key={mensajeId}
            onError={() => setImagenDisponible(false)}
            resizeMode="contain"
            source={{ uri: verificationCodeImageUrl(mensajeId) }}
            style={styles.imagen}
          />
        </View>
      ) : null}
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
      ) : (
        <HelperText>
          Si no recibiste el mensaje, puedes solicitar otro código. La verificación
          está limitada para proteger tu cuenta.
        </HelperText>
      )}
      <PrimaryButton
        busy={busy}
        disabled={
          codigo.length !== 6 ||
          (isRecovery && contrasenaNueva.length < 8)
        }
        title={isRecovery ? 'Actualizar contraseña' : 'Verificar y continuar'}
        onPress={submit}
      />
      <FormFooter>
        <TextButton
          disabled={resending || busy}
          title={resending ? 'Solicitando código…' : 'Solicitar otro código'}
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

const styles = StyleSheet.create({
  codigoImagen: {
    alignItems: 'center',
    borderColor: '#D0D7E2',
    borderRadius: 12,
    borderWidth: 1,
    gap: 8,
    marginBottom: 12,
    padding: 12,
  },
  codigoTitulo: {
    fontSize: 15,
    fontWeight: '600',
    textAlign: 'center',
  },
  imagen: {
    aspectRatio: 3,
    maxWidth: 300,
    width: '100%',
  },
});
