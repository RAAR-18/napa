import { useEffect, useState } from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';

import type { EstadoDomicilio } from '../lib/domicilios';
import { obtenerDomiciliarioPedido, type DomiciliarioDePedido } from '../lib/pedidos';
import { palette } from './AuthUI';

type Props = {
  pedidoId: string;
  /** Se vuelve a consultar cuando cambia el estado del domicilio (por ejemplo, al ser aceptado). */
  estadoDomicilio: EstadoDomicilio;
};

/** RF-27: nombre, foto y calificación del domiciliario asignado, dentro del detalle del pedido. */
export default function DomiciliarioAsignado({ pedidoId, estadoDomicilio }: Props) {
  const [data, setData] = useState<DomiciliarioDePedido | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    // Mientras está disponible nadie lo ha aceptado: el render ya lo dice sin consultar.
    if (estadoDomicilio === 'DISPONIBLE') return undefined;
    obtenerDomiciliarioPedido(pedidoId)
      .then((response) => {
        if (active) {
          setData(response);
          setFailed(false);
        }
      })
      .catch(() => {
        if (active) setFailed(true);
      });
    return () => {
      active = false;
    };
  }, [pedidoId, estadoDomicilio]);

  if (estadoDomicilio === 'DISPONIBLE') {
    return <Text style={styles.detailText}>Aún no hay domiciliario asignado.</Text>;
  }
  if (failed) {
    return <Text style={styles.detailText}>No se pudo cargar la información del domiciliario.</Text>;
  }
  if (!data) return null;
  const domiciliario = data.domiciliario;
  if (!domiciliario) {
    return <Text style={styles.detailText}>Aún no hay domiciliario asignado.</Text>;
  }
  return (
    <View style={styles.row}>
      {domiciliario.fotoUrl ? (
        <Image
          accessibilityLabel={`Foto de ${domiciliario.nombre}`}
          source={{ uri: domiciliario.fotoUrl }}
          style={styles.photo}
        />
      ) : (
        <View style={[styles.photo, styles.initial]}>
          <Text style={styles.initialText}>{domiciliario.nombre.charAt(0).toUpperCase()}</Text>
        </View>
      )}
      <View style={styles.info}>
        <Text style={styles.name}>Domiciliario: {domiciliario.nombre}</Text>
        <Text style={styles.detailText}>
          {domiciliario.calificacion
            ? `Calificación: ${domiciliario.calificacion.promedio.toFixed(1)} (${domiciliario.calificacion.total})`
            : 'Aún sin calificaciones'}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { alignItems: 'center', flexDirection: 'row', marginTop: 10 },
  photo: { borderRadius: 24, height: 48, width: 48 },
  initial: { alignItems: 'center', backgroundColor: palette.border, justifyContent: 'center' },
  initialText: { color: palette.dark, fontSize: 18, fontWeight: '800' },
  info: { flex: 1, marginLeft: 12 },
  name: { color: palette.dark, fontSize: 15, fontWeight: '700' },
  detailText: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 6 },
});
