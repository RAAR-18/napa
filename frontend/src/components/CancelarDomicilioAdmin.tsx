import { useRef, useState } from 'react';
import * as Crypto from 'expo-crypto';
import { StyleSheet, Text, View } from 'react-native';

import { ApiError } from '../lib/api';
import {
  MOTIVO_CANCELACION_MAXIMO,
  MOTIVO_CANCELACION_MINIMO,
  cancelarDomicilioAdmin,
  type EstadoDomicilio,
} from '../lib/domicilios';
import { ErrorBanner, Field, HelperText, PrimaryButton, TextButton, palette } from './AuthUI';

type Props = {
  domicilioId: string;
  estado: EstadoDomicilio;
  /** Hay un pago de la tarifa registrado o confirmado. */
  conPagoTarifa: boolean;
  onCancelado: () => void;
  /** El servidor dijo que el domicilio ya cambió: la pantalla muestra el aviso y recarga el detalle. */
  onCambio: (mensaje: string) => void;
};

function mensajeError(cause: unknown) {
  if (cause instanceof ApiError) {
    if (cause.code === 'INVALID_STATE') {
      const actual = (cause.details as { estadoActual?: string } | undefined)?.estadoActual;
      return actual === 'ENTREGADO'
        ? 'El domicilio ya fue entregado y no se puede cancelar.'
        : 'El domicilio ya está cancelado.';
    }
    if (cause.code === 'NOT_FOUND') return 'El domicilio no existe.';
    return cause.message;
  }
  return cause instanceof Error ? cause.message : 'No se pudo cancelar el domicilio.';
}

/**
 * RF-13. El motivo es obligatorio (no se habilita confirmar sin él) y la confirmación es un segundo paso. Exige
 * conexión: no se encola.
 */
export default function CancelarDomicilioAdmin({ domicilioId, estado, conPagoTarifa, onCancelado, onCambio }: Props) {
  const [open, setOpen] = useState(false);
  const [motivo, setMotivo] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const actionKey = useRef<string | null>(null);
  const limpio = motivo.trim();
  const valido = limpio.length >= MOTIVO_CANCELACION_MINIMO && limpio.length <= MOTIVO_CANCELACION_MAXIMO;
  const enCurso = estado === 'ASIGNADO' || estado === 'RECOGIDO' || estado === 'EN_CAMINO';

  async function confirmar() {
    if (!valido || busy) return;
    setBusy(true);
    setError(null);
    // La misma clave en los reintentos por red: el servidor no cancela dos veces.
    actionKey.current ??= Crypto.randomUUID();
    try {
      await cancelarDomicilioAdmin(domicilioId, limpio, actionKey.current);
      actionKey.current = null;
      setOpen(false);
      onCancelado();
    } catch (cause) {
      if (cause instanceof ApiError) {
        actionKey.current = null;
        if (cause.code === 'INVALID_STATE' || cause.code === 'NOT_FOUND') {
          onCambio(mensajeError(cause));
          return;
        }
      }
      setError(mensajeError(cause));
    } finally {
      setBusy(false);
    }
  }

  if (!open) {
    return (
      <View style={styles.box}>
        <ErrorBanner message={error} />
        <PrimaryButton title="Cancelar domicilio" onPress={() => setOpen(true)} />
      </View>
    );
  }

  return (
    <View style={[styles.box, styles.confirmation]}>
      <Text style={styles.title}>Cancelar este domicilio</Text>
      <Text style={styles.text}>
        El domicilio y su pedido quedarán cancelados y se avisará al cliente, al vendedor
        {enCurso ? ' y al domiciliario, que quedará libre para tomar otro' : ''}. No se puede deshacer.
      </Text>
      {conPagoTarifa ? (
        <Text style={styles.warning}>
          Ya hay un pago de la tarifa registrado. Ñapa no devuelve dinero: la devolución se acuerda fuera de la app.
        </Text>
      ) : (
        <Text style={styles.text}>Si el cliente ya pagó el pedido, la devolución se acuerda fuera de la app.</Text>
      )}
      <Field
        label="Motivo de la cancelación"
        value={motivo}
        onChangeText={setMotivo}
        maxLength={MOTIVO_CANCELACION_MAXIMO}
        multiline
        placeholder="Por ejemplo: el cliente reportó que el domiciliario no llegó"
      />
      <HelperText>
        {limpio.length < MOTIVO_CANCELACION_MINIMO
          ? `Escribe al menos ${MOTIVO_CANCELACION_MINIMO} caracteres (${limpio.length}/${MOTIVO_CANCELACION_MAXIMO}).`
          : `${limpio.length}/${MOTIVO_CANCELACION_MAXIMO} caracteres.`}
      </HelperText>
      <ErrorBanner message={error} />
      <PrimaryButton title="Confirmar cancelación" busy={busy} disabled={!valido} onPress={() => void confirmar()} />
      <TextButton
        title="No cancelar"
        disabled={busy}
        onPress={() => {
          setOpen(false);
          setError(null);
        }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  box: { marginTop: 10 },
  confirmation: {
    backgroundColor: '#FFF8E8',
    borderColor: '#E8D8AE',
    borderRadius: 14,
    borderWidth: 1,
    padding: 12,
  },
  title: { color: palette.dark, fontSize: 16, fontWeight: '800' },
  text: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 6 },
  warning: { color: '#86510F', fontSize: 14, fontWeight: '700', lineHeight: 21, marginTop: 6 },
});
