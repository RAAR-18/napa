import { useEffect, useState } from 'react';
import { router } from 'expo-router';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

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
import { ApiError } from '../lib/api';
import {
  clearSession,
  getValidSession,
  type StoredSession,
} from '../lib/auth';
import {
  guardarConfiguracionPago,
  obtenerConfiguracionPago,
  type ConfiguracionPago,
  type TipoCuentaBancaria,
} from '../lib/pagos';

const sellerRoles = ['VENDEDOR_AMBULANTE', 'VENDEDOR_PUNTO_FIJO'];
const accountTypes: { value: TipoCuentaBancaria; label: string }[] = [
  { value: 'AHORROS', label: 'Ahorros' },
  { value: 'CORRIENTE', label: 'Corriente' },
];

export default function ConfiguracionPagosScreen() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [configuration, setConfiguration] = useState<ConfiguracionPago | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [canRetryLoad, setCanRetryLoad] = useState(false);
  const [saving, setSaving] = useState(false);
  const [acceptsCash, setAcceptsCash] = useState(false);
  const [bank, setBank] = useState('');
  const [accountType, setAccountType] = useState<TipoCuentaBancaria | null>(null);
  const [accountNumber, setAccountNumber] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        const validSession = await getValidSession();
        if (!active) return;
        if (!sellerRoles.includes(validSession.rol)) {
          setError('Solo las cuentas de vendedor pueden configurar sus métodos de pago.');
          return;
        }

        const current = await obtenerConfiguracionPago();
        if (!active) return;
        setSession(validSession);
        setConfiguration(current);
        setAcceptsCash(current.aceptaEfectivo);
        setBank(current.cuentaBancaria?.banco ?? '');
        setAccountType(current.cuentaBancaria?.tipoCuenta ?? null);
        setAccountNumber(current.cuentaBancaria?.numeroCuenta ?? '');
      } catch (cause) {
        if (active) {
          setCanRetryLoad(!(cause instanceof ApiError && cause.status === 401));
          await handleError(cause);
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, [loadAttempt]);

  async function handleError(cause: unknown) {
    if (cause instanceof ApiError && cause.status === 401) {
      setSession(null);
      await clearSession();
      router.replace('/login');
      return;
    }
    setError(
      cause instanceof Error
        ? cause.message
        : 'No se pudo completar la configuración de pago.',
    );
  }

  async function save() {
    setError(null);
    setNotice(null);
    if (!session) {
      setError('Inicia sesión con una cuenta de vendedor para continuar.');
      return;
    }

    const hasAccountDetails = Boolean(
      bank.trim() || accountType || accountNumber.trim(),
    );
    let account: ConfiguracionPago['cuentaBancaria'] = null;
    if (hasAccountDetails) {
      const cleanBank = bank.trim();
      const cleanNumber = accountNumber.trim();
      if (!cleanBank || cleanBank.length > 100 || !accountType
          || !/^\d{4,20}$/.test(cleanNumber)) {
        setError(
          'Completa el banco, el tipo de cuenta y un número de 4 a 20 dígitos, o quita la cuenta.',
        );
        return;
      }
      account = {
        banco: cleanBank,
        tipoCuenta: accountType,
        numeroCuenta: cleanNumber,
      };
    }

    setSaving(true);
    try {
      const saved = await guardarConfiguracionPago({
        aceptaEfectivo: acceptsCash,
        cuentaBancaria: account,
      });
      setConfiguration(saved);
      setNotice('Configuración guardada.');
    } catch (cause) {
      await handleError(cause);
    } finally {
      setSaving(false);
    }
  }

  function retryLoad() {
    setCanRetryLoad(false);
    setError(null);
    setLoading(true);
    setLoadAttempt((attempt) => attempt + 1);
  }

  if (loading) {
    return (
      <AuthPage>
        <PageHeading
          eyebrow="Métodos de pago"
          title="Cargando configuración"
          description="Estamos consultando tus opciones actuales."
        />
        <ActivityIndicator accessibilityLabel="Cargando métodos de pago" color={palette.green} />
      </AuthPage>
    );
  }

  return (
    <AuthPage>
      <PageHeading
        eyebrow="RF-63 · Tu negocio"
        title="Métodos de pago"
        description="Indica cómo pueden pagarte tus clientes. Ñapa no procesa ni retiene dinero."
      />
      <ErrorBanner message={error} />
      {canRetryLoad && !configuration ? (
        <PrimaryButton title="Reintentar carga" onPress={retryLoad} />
      ) : null}
      {session && configuration && sellerRoles.includes(session.rol) ? (
        <>
          <Text style={styles.label}>¿Aceptas efectivo?</Text>
          <View style={styles.options}>
            <OptionButton
              label="Acepto efectivo"
              onPress={() => setAcceptsCash(true)}
              selected={acceptsCash}
            />
            <OptionButton
              label="No acepto efectivo"
              onPress={() => setAcceptsCash(false)}
              selected={!acceptsCash}
            />
          </View>

          <Text style={styles.label}>Cuenta bancaria para transferencias (opcional)</Text>
          <HelperText>
            Registra la cuenta donde recibirás las transferencias. No ingreses datos de tarjetas ni billeteras.
          </HelperText>
          <Field
            autoCapitalize="words"
            label="Banco"
            maxLength={100}
            onChangeText={setBank}
            placeholder="Ej. Banco de Bogotá"
            value={bank}
          />
          <Text style={styles.label}>Tipo de cuenta</Text>
          <View style={styles.options}>
            {accountTypes.map((option) => (
              <OptionButton
                key={option.value}
                label={option.label}
                onPress={() => setAccountType(option.value)}
                selected={accountType === option.value}
              />
            ))}
          </View>
          <Field
            keyboardType="number-pad"
            label="Número de cuenta"
            maxLength={20}
            onChangeText={setAccountNumber}
            placeholder="Entre 4 y 20 dígitos"
            secureTextEntry
            value={accountNumber}
          />
          <HelperText>
            La cuenta se cifra en el servidor. Solo se usa para registrar transferencias hechas directamente a ti.
          </HelperText>
          {configuration?.avisoDomicilio ? (
            <View style={styles.noticeCard}>
              <Text style={styles.noticeText}>{configuration.avisoDomicilio}</Text>
              <Text style={styles.noticeText}>
                {configuration.domicilioDisponible
                  ? 'Tu negocio puede ofrecer domicilios.'
                  : 'Registra una cuenta bancaria para habilitar domicilios.'}
              </Text>
            </View>
          ) : null}
          <Text style={styles.methods}>
            Métodos habilitados:{' '}
            {configuration?.mediosHabilitados.length
              ? configuration.mediosHabilitados.join(', ')
              : 'ninguno'}
          </Text>
          {notice ? <Text style={styles.success}>{notice}</Text> : null}
          {configuration?.cuentaBancaria ? (
            <TextButton
              disabled={saving}
              title="Quitar cuenta bancaria"
              onPress={() => {
                setBank('');
                setAccountType(null);
                setAccountNumber('');
              }}
            />
          ) : null}
          <PrimaryButton
            busy={saving}
            title="Guardar métodos de pago"
            onPress={() => void save()}
          />
        </>
      ) : null}
      <FormFooter>
        <TextButton
          title="Volver a mi cuenta"
          onPress={() => router.replace('/session')}
        />
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
  options: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginBottom: 12,
  },
  noticeCard: {
    backgroundColor: '#E8F2EB',
    borderColor: palette.border,
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: 16,
    padding: 14,
  },
  noticeText: {
    color: palette.dark,
    fontSize: 14,
    lineHeight: 21,
  },
  methods: {
    color: palette.dark,
    fontSize: 14,
    fontWeight: '700',
    marginBottom: 12,
  },
  success: {
    color: palette.green,
    fontSize: 14,
    fontWeight: '700',
    marginBottom: 8,
  },
});
