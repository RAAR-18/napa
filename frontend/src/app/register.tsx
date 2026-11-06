import { useState } from 'react';
import { router } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  Field,
  FormFooter,
  HelperText,
  OptionButton,
  PageHeading,
  PrimaryButton,
  TextButton,
  palette,
} from '../components/AuthUI';
import { register, type UserRole } from '../lib/auth';

const roles: { value: UserRole; label: string }[] = [
  { value: 'CLIENTE', label: 'Cliente' },
  { value: 'VENDEDOR_AMBULANTE', label: 'Vendedor ambulante' },
  { value: 'VENDEDOR_PUNTO_FIJO', label: 'Vendedor de punto fijo' },
  { value: 'DOMICILIARIO', label: 'Domiciliario' },
];

export default function RegisterScreen() {
  const [nombre, setNombre] = useState('');
  const [celular, setCelular] = useState('');
  const [correo, setCorreo] = useState('');
  const [password, setPassword] = useState('');
  const [confirmacionPassword, setConfirmacionPassword] = useState('');
  const [rol, setRol] = useState<UserRole>('CLIENTE');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    setError(null);
    setBusy(true);
    try {
      await register({
        nombre: nombre.trim(),
        celular: celular.trim(),
        correo: correo.trim() || null,
        password,
        confirmacionPassword,
        rol,
      });
      router.push({
        pathname: '/verify',
        params: { celular: celular.trim(), proposito: 'REGISTRO' },
      });
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : 'No se pudo crear la cuenta. Revisa los datos e inténtalo de nuevo.',
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Únete a Ñapa"
        title="Crea tu cuenta"
        description="Completa tus datos. Verificaremos tu celular antes de activar la cuenta."
      />
      <ErrorBanner message={error} />
      <Field
        autoCapitalize="words"
        label="Nombre"
        maxLength={120}
        onChangeText={setNombre}
        placeholder="Tu nombre"
        value={nombre}
      />
      <Field
        autoCapitalize="none"
        keyboardType="phone-pad"
        label="Celular"
        onChangeText={setCelular}
        placeholder="+573001234567"
        value={celular}
      />
      <HelperText>
        Usa tu número de Colombia en formato internacional, por ejemplo
        +573001234567.
      </HelperText>
      <Field
        autoCapitalize="none"
        keyboardType="email-address"
        label="Correo (opcional)"
        onChangeText={setCorreo}
        placeholder="nombre@correo.com"
        value={correo}
      />
      <Text style={styles.label}>¿Cómo usarás Ñapa?</Text>
      <View style={styles.roles}>
        {roles.map((option) => (
          <OptionButton
            key={option.value}
            label={option.label}
            onPress={() => setRol(option.value)}
            selected={rol === option.value}
          />
        ))}
      </View>
      <Field
        autoCapitalize="none"
        label="Contraseña"
        maxLength={128}
        onChangeText={setPassword}
        placeholder="Mínimo 8 caracteres"
        secureTextEntry
        value={password}
      />
      <Field
        autoCapitalize="none"
        label="Confirma tu contraseña"
        maxLength={128}
        onChangeText={setConfirmacionPassword}
        onSubmitEditing={submit}
        placeholder="Escribe de nuevo tu contraseña"
        returnKeyType="go"
        secureTextEntry
        value={confirmacionPassword}
      />
      <HelperText>La contraseña debe tener entre 8 y 128 caracteres.</HelperText>
      <PrimaryButton
        busy={busy}
        disabled={
          !nombre.trim() ||
          !celular.trim() ||
          password.length < 8 ||
          !confirmacionPassword
        }
        title="Crear cuenta y verificar celular"
        onPress={submit}
      />
      <FormFooter>
        <TextButton
          title="¿Ya tienes cuenta? Inicia sesión"
          onPress={() => router.replace('/login')}
        />
        <TextButton title="Volver" onPress={() => router.replace('/')} />
      </FormFooter>
    </AuthPage>
  );
}

const styles = StyleSheet.create({
  label: {
    color: palette.dark,
    fontSize: 14,
    fontWeight: '700',
    marginBottom: 8,
  },
  roles: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginBottom: 12,
  },
});
