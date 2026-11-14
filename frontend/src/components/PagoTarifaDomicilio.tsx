import { useEffect, useRef, useState } from 'react';
import * as Crypto from 'expo-crypto';
import { StyleSheet, Text, View } from 'react-native';

import { ApiError } from '../lib/api';
import {
  confirmarPagoTarifa,
  consultarPagoTarifa,
  registrarPagoTarifa,
  type EstadoPagoTarifa,
} from '../lib/pagos';
import { ErrorBanner, PrimaryButton, TextButton, palette } from './AuthUI';

type Props = {
  domicilioId: string;
  rol: 'VENDEDOR' | 'DOMICILIARIO';
};

const precio = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });

function formatDate(value: string) {
  return new Date(value).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
}

function mensajeError(cause: unknown) {
  if (cause instanceof ApiError) {
    switch (cause.code) {
      case 'CONFLICT':
        return 'Ya registraste el pago de la tarifa.';
      case 'FEE_NOT_REGISTERED':
        return 'El vendedor aún no registró el pago.';
      case 'ALREADY_CONFIRMED':
        return 'Ya confirmaste este pago.';
      case 'INVALID_STATE':
        return 'Primero debe confirmarse la entrega.';
      default:
        return cause.message;
    }
  }
  return cause instanceof Error ? cause.message : 'No se pudo completar la acción.';
}

/**
 * RF-43, con el domicilio entregado. El vendedor ve la cuenta del domiciliario y registra que le transfirió; el
 * domiciliario confirma que le llegó. La app no mueve dinero: solo deja constancia.
 */
export default function PagoTarifaDomicilio({ domicilioId, rol }: Props) {
  const [data, setData] = useState<EstadoPagoTarifa | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const actionKey = useRef<string | null>(null);

  useEffect(() => {
    let active = true;
    consultarPagoTarifa(domicilioId)
      .then((response) => {
        if (active) setData(response);
      })
      .catch((cause: unknown) => {
        if (active) setError(mensajeError(cause));
      });
    return () => {
      active = false;
    };
  }, [domicilioId, attempt]);

  async function act() {
    if (busy) return;
    setBusy(true);
    setError(null);
    // La misma clave en los reintentos por red: el servidor no registra dos veces.
    actionKey.current ??= Crypto.randomUUID();
    try {
      const response =
        rol === 'VENDEDOR'
          ? await registrarPagoTarifa(domicilioId, actionKey.current)
          : await confirmarPagoTarifa(domicilioId, actionKey.current);
      actionKey.current = null;
      setConfirming(false);
      setData((previous) => ({ ...response, cuentaDomiciliario: previous?.cuentaDomiciliario ?? null }));
    } catch (cause) {
      if (cause instanceof ApiError) {
        actionKey.current = null;
        setConfirming(false);
        setAttempt((value) => value + 1);
      }
      setError(mensajeError(cause));
    } finally {
      setBusy(false);
    }
  }

  if (!data) {
    return <ErrorBanner message={error} />;
  }
  const cuenta = data.cuentaDomiciliario;
  const actionTitle = rol === 'VENDEDOR' ? 'Registrar pago de la tarifa' : 'Confirmé que recibí mi tarifa';
  const canAct = rol === 'VENDEDOR' ? data.estado === 'SIN_REGISTRAR' : data.estado === 'REGISTRADO';

  return (
    <View style={styles.card}>
      <Text style={styles.sectionTitle}>Tarifa del domicilio</Text>
      <Text style={styles.amount}>{precio.format(data.monto)}</Text>
      {data.estado === 'SIN_REGISTRAR' ? (
        <Text style={styles.detailText}>
          {rol === 'VENDEDOR'
            ? 'Transfiere la tarifa al domiciliario y luego registra el pago aquí.'
            : 'El vendedor aún no registra el pago de tu tarifa.'}
        </Text>
      ) : null}
      {data.estado === 'REGISTRADO' && data.registradoAt ? (
        <Text style={styles.detailText}>
          {rol === 'VENDEDOR'
            ? `Registraste el pago el ${formatDate(data.registradoAt)}. Falta que el domiciliario lo confirme.`
            : `El vendedor registró el pago el ${formatDate(data.registradoAt)}. Revisa tu cuenta y confírmalo.`}
        </Text>
      ) : null}
      {data.estado === 'CONFIRMADO' && data.confirmadoAt ? (
        <Text style={styles.detailText}>Pago confirmado el {formatDate(data.confirmadoAt)}.</Text>
      ) : null}
      {rol === 'VENDEDOR' && data.estado === 'SIN_REGISTRAR' ? (
        cuenta ? (
          <View style={styles.account}>
            <Text style={styles.detailText}>Banco: {cuenta.banco}</Text>
            <Text style={styles.detailText}>Tipo: {cuenta.tipoCuenta === 'AHORROS' ? 'Ahorros' : 'Corriente'}</Text>
            <Text selectable style={styles.accountNumber}>
              Cuenta: {cuenta.numeroCuenta}
            </Text>
          </View>
        ) : (
          <Text style={styles.detailText}>
            El domiciliario aún no registró su cuenta bancaria. Coordina con él por dónde transferirle.
          </Text>
        )
      ) : null}
      <ErrorBanner message={error} />
      {canAct && !confirming ? <PrimaryButton title={actionTitle} onPress={() => setConfirming(true)} /> : null}
      {canAct && confirming ? (
        <View style={styles.confirmation}>
          <Text style={styles.detailText}>
            {rol === 'VENDEDOR'
              ? `¿Ya le transferiste ${precio.format(data.monto)} al domiciliario?`
              : `¿Ya te llegaron ${precio.format(data.monto)} a tu cuenta?`}
          </Text>
          <PrimaryButton title="Sí, confirmar" busy={busy} onPress={() => void act()} />
          <TextButton title="Cancelar" disabled={busy} onPress={() => setConfirming(false)} />
        </View>
      ) : null}
    </View>
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
  amount: { color: palette.green, fontSize: 20, fontWeight: '800', marginTop: 6 },
  detailText: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 6 },
  account: { marginTop: 4 },
  accountNumber: { color: palette.dark, fontSize: 16, fontWeight: '700', marginTop: 6 },
  confirmation: {
    backgroundColor: '#FFF8E8',
    borderColor: '#E8D8AE',
    borderRadius: 14,
    borderWidth: 1,
    marginTop: 10,
    padding: 12,
  },
});
