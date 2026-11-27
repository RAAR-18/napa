import { fonts } from './theme';
import { useEffect, useState } from 'react';
import { Linking, StyleSheet, Text, View } from 'react-native';

import { TextButton, palette } from './AuthUI';
import { obtenerClienteDePedido, type ClienteDePedido } from '../lib/pedidos';

/** RF-52: nombre del cliente y, mientras el pedido está en curso, su celular para coordinar. */
export default function ClienteDelPedido({ pedidoId, estado }: { pedidoId: string; estado: string }) {
  const [cliente, setCliente] = useState<ClienteDePedido | null>(null);

  useEffect(() => {
    let active = true;
    async function cargar() {
      try {
        const data = await obtenerClienteDePedido(pedidoId);
        if (active) setCliente(data);
      } catch {
        // Sin los datos de contacto el resto del detalle del pedido sigue sirviendo.
      }
    }
    void cargar();
    return () => {
      active = false;
    };
  }, [pedidoId, estado]);

  if (!cliente) return null;

  return (
    <View style={styles.card}>
      <Text style={styles.label}>Cliente</Text>
      <Text style={styles.name}>{cliente.nombre}</Text>
      {cliente.celular ? (
        <>
          <Text style={styles.detail}>{cliente.celular}</Text>
          <View style={styles.row}>
            <TextButton icon="phone" title="Llamar" variant="soft" onPress={() => void Linking.openURL(`tel:${cliente.celular}`)} />
            <TextButton icon="message-circle"
              title="WhatsApp"
              variant="whatsapp"
              onPress={() => void Linking.openURL(`https://wa.me/${cliente.celular?.replace('+', '')}`)}
            />
          </View>
        </>
      ) : (
        <Text style={styles.detail}>El contacto se oculta cuando el pedido ya no está en curso.</Text>
      )}
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
  label: { color: palette.muted, fontFamily: fonts.sans, fontSize: 13, fontWeight: '700' },
  name: { color: palette.dark, fontFamily: fonts.sans, fontSize: 17, fontWeight: '800', marginTop: 2 },
  detail: { color: palette.muted, fontFamily: fonts.sans, fontSize: 14, lineHeight: 21, marginTop: 4 },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 16, marginTop: 10 },
});
