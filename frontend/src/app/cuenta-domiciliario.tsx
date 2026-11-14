import { useEffect, useState } from 'react';
import { router } from 'expo-router';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  Field,
  HelperText,
  OptionButton,
  PageHeading,
  PrimaryButton,
  TextButton,
  palette,
} from '../components/AuthUI';
import { ApiError } from '../lib/api';
import { clearSession, getValidSession } from '../lib/auth';
import {
  guardarCuentaDomiciliario,
  obtenerCuentaDomiciliario,
  type CuentaDomiciliario,
  type TipoCuentaBancaria,
} from '../lib/pagos';

const accountTypes: { value: TipoCuentaBancaria; label: string }[] = [
  { value: 'AHORROS', label: 'Ahorros' },
  { value: 'CORRIENTE', label: 'Corriente' },
];

/** RF-43: cuenta a la que los vendedores transfieren la tarifa del domicilio. */
export default function CuentaDomiciliarioScreen() {
  const [current, setCurrent] = useState<CuentaDomiciliario | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [bank, setBank] = useState('');
  const [accountType, setAccountType] = useState<TipoCuentaBancaria | null>(null);
  const [accountNumber, setAccountNumber] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const session = await getValidSession();
        if (!active) return;
        if (session.rol !== 'DOMICILIARIO') {
          setError('Solo los domiciliarios registran aquí su cuenta para cobrar la tarifa.');
          return;
        }
        const response = await obtenerCuentaDomiciliario();
        if (!active) return;
        setCurrent(response);
        setBank(response.banco ?? '');
        setAccountType(response.tipoCuenta);
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 401) {
          await clearSession();
          router.replace('/login');
          return;
        }
        if (active) setError(cause instanceof Error ? cause.message : 'No se pudo cargar tu cuenta.');
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, []);

  async function save() {
    setError(null);
    setNotice(null);
    const cleanBank = bank.trim();
    const cleanNumber = accountNumber.trim();
    if (!cleanBank || cleanBank.length > 100 || !accountType || !/^\d{4,20}$/.test(cleanNumber)) {
      setError('Completa el banco, el tipo de cuenta y un número de 4 a 20 dígitos.');
      return;
    }
    setSaving(true);
    try {
      const saved = await guardarCuentaDomiciliario({
        banco: cleanBank,
        tipoCuenta: accountType,
        numeroCuenta: cleanNumber,
      });
      setCurrent(saved);
      setAccountNumber('');
      setNotice('Cuenta guardada. Los vendedores la verán cuando entregues un domicilio.');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'No se pudo guardar tu cuenta.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Domiciliario"
        title="Mi cuenta para cobrar"
        description="Aquí te transfieren la tarifa de cada domicilio. Ñapa no procesa ni retiene dinero."
      />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando tu cuenta" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {current ? (
        <>
          {current.numeroCuenta ? (
            <View style={styles.card}>
              <Text style={styles.sectionTitle}>Cuenta registrada</Text>
              <Text style={styles.detailText}>
                {current.banco} · {current.tipoCuenta === 'AHORROS' ? 'Ahorros' : 'Corriente'} · {current.numeroCuenta}
              </Text>
            </View>
          ) : (
            <HelperText>Aún no registras una cuenta. Sin ella, el vendedor tendrá que preguntarte dónde pagarte.</HelperText>
          )}
          <Field autoCapitalize="words" label="Banco" maxLength={100} onChangeText={setBank} value={bank} />
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
            label={current.numeroCuenta ? 'Número de cuenta (escríbelo de nuevo para cambiarla)' : 'Número de cuenta'}
            maxLength={20}
            onChangeText={setAccountNumber}
            placeholder="Entre 4 y 20 dígitos"
            secureTextEntry
            value={accountNumber}
          />
          <HelperText>
            La cuenta se guarda cifrada. Solo la ve el vendedor del domicilio que entregaste, para pagarte.
          </HelperText>
          {notice ? <Text style={styles.success}>{notice}</Text> : null}
          <PrimaryButton busy={saving} title="Guardar cuenta" onPress={() => void save()} />
        </>
      ) : null}
      <TextButton title="Volver" onPress={() => router.replace('/session')} />
    </AuthPage>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: palette.white,
    borderColor: palette.border,
    borderRadius: 16,
    borderWidth: 1,
    marginVertical: 7,
    padding: 16,
  },
  sectionTitle: { color: palette.dark, fontSize: 16, fontWeight: '800' },
  detailText: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 6 },
  label: { color: palette.dark, fontSize: 14, fontWeight: '700', marginTop: 12 },
  options: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginVertical: 8 },
  success: { color: palette.green, fontSize: 14, fontWeight: '700', marginVertical: 10 },
});
