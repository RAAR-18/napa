import { fonts } from './theme';
import { useState } from 'react';
import { randomUUID } from '../lib/uuid';
import { Linking, StyleSheet, Text, View } from 'react-native';

import { ErrorBanner, TextButton, palette } from './AuthUI';
import { ApiError } from '../lib/api';
import { manejarError } from '../lib/errores';
import { contactarDesdePedido, type DestinatarioContacto, type TipoContacto } from '../lib/pedidos';

const nombres: Record<DestinatarioContacto, string> = { VENDEDOR: 'al vendedor', DOMICILIARIO: 'al domiciliario' };

/** RF-49: abre la llamada o el chat de WhatsApp; la conversación ocurre fuera de Ñapa. */
export default function ContactarPedido({
  pedidoId,
  destinatario,
}: {
  pedidoId: string;
  destinatario: DestinatarioContacto;
}) {
  const [busy, setBusy] = useState<TipoContacto | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function contactar(tipo: TipoContacto) {
    setBusy(tipo);
    setError(null);
    try {
      const canal = await contactarDesdePedido(pedidoId, destinatario, tipo, randomUUID());
      await Linking.openURL(canal.enlace);
    } catch (cause) {
      setError(
        cause instanceof ApiError && cause.code === 'INVALID_STATE'
          ? `Ya no es posible contactar ${nombres[destinatario]} desde este pedido.`
          : await manejarError(cause, 'No se pudo abrir el contacto.'),
      );
    } finally {
      setBusy(null);
    }
  }

  return (
    <View style={styles.box}>
      <Text style={styles.label}>Contactar {nombres[destinatario]}</Text>
      <View style={styles.row}>
        <TextButton icon="phone" title={busy === 'LLAMADA' ? 'Abriendo…' : 'Llamar'} variant="soft" disabled={busy !== null} onPress={() => void contactar('LLAMADA')} />
        <TextButton icon="message-circle" title={busy === 'MENSAJE' ? 'Abriendo…' : 'WhatsApp'} variant="whatsapp" disabled={busy !== null} onPress={() => void contactar('MENSAJE')} />
      </View>
      <ErrorBanner message={error} />
    </View>
  );
}

const styles = StyleSheet.create({
  box: { marginTop: 8 },
  label: { color: palette.muted, fontFamily: fonts.sans, fontSize: 13, fontWeight: '700' },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 16, marginTop: 10 },
});
