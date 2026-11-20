import { fonts } from '../components/theme';
import { useState } from 'react';
import { router } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  HelperText,
  PageHeading,
  PrimaryButton,
  TextButton,
  palette,
} from '../components/AuthUI';
import { outbox, type AccionPendiente } from '../database/outbox';
import { useAccionesPendientes } from '../hooks/useOutbox';
import { borrarCarritoEncolado, paramsRevisionPedido, pedidoPendiente } from '../lib/pedido-offline';
import { edicionEmprendimientoPendiente } from '../lib/emprendimiento-pendiente';
import { edicionDescartableSinRevision, cambioPendienteProducto, productoPendiente } from '../lib/productos-pendientes';

function formatDate(ms: number) {
  return new Date(ms).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
}

/** F041 (RNF28): lo que se guardó sin conexión y aún no llega al servidor. */
export default function PendientesEnvioScreen() {
  const acciones = useAccionesPendientes();
  const [busy, setBusy] = useState(false);
  const [mensaje, setMensaje] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function enviarAhora() {
    setBusy(true);
    setError(null);
    setMensaje(null);
    try {
      const resultado = await outbox.procesar({ forzar: true });
      setMensaje(
        resultado.pendientes === 0 && resultado.fallidas === 0
          ? 'Todo se envió.'
          : resultado.fallidas > 0
            ? `Hay ${resultado.fallidas} acciones que requieren revisión. Consulta el mensaje y decide si reintentar o descartar.`
          : resultado.enviadas > 0
            ? `Se enviaron ${resultado.enviadas}. Las demás se intentarán de nuevo.`
            : 'Aún no hay conexión con el servidor. Lo intentaremos de nuevo en un momento.',
      );
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'No se pudo enviar.');
    } finally {
      setBusy(false);
    }
  }

  const pendientes = acciones.filter((a) => a.status === 'PENDIENTE');
  const fallidas = acciones.filter((a) => a.status === 'FALLIDA');

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Sin conexión"
        title="Pendientes de envío"
        description="Lo que hiciste sin señal se envía solo cuando vuelve la conexión, sin duplicarse."
      />
      <ErrorBanner message={error} />
      {mensaje ? <HelperText>{mensaje}</HelperText> : null}
      {pendientes.map((accion) => (
        <Tarjeta key={accion.id} accion={accion} />
      ))}
      {fallidas.length > 0 ? <Text style={styles.section}>No se pudieron enviar</Text> : null}
      {fallidas.map((accion) => (
        <Tarjeta key={accion.id} accion={accion} />
      ))}
      {acciones.length === 0 ? <HelperText>No tienes acciones pendientes.</HelperText> : null}
      {pendientes.length > 0 ? (
        <PrimaryButton title="Enviar ahora" busy={busy} onPress={() => void enviarAhora()} />
      ) : null}
      <TextButton title="Volver" onPress={() => router.back()} />
    </AuthPage>
  );
}

function Tarjeta({ accion }: { accion: AccionPendiente }) {
  const fallida = accion.status === 'FALLIDA';
  const edicion = cambioPendienteProducto(accion);
  const pedido = pedidoPendiente(accion);
  return (
    <View style={[styles.card, fallida && styles.failed]}>
      <Text style={styles.title}>{accion.descripcion}</Text>
      <Text style={styles.detail}>Guardada: {formatDate(accion.createdAt)}</Text>
      {fallida ? (
        <>
          <Text style={styles.error}>{accion.lastError ?? 'El servidor la rechazó.'}</Text>
          <View style={styles.actions}>
            {pedido ? (
              <TextButton title="Revisar pedido" onPress={() => router.push({
                pathname: '/catalogo', params: paramsRevisionPedido(accion, pedido),
              })} />
            ) : productoPendiente(accion) ? (
              <TextButton title="Corregir y reenviar" onPress={() => router.push({
                pathname: '/nuevo-producto', params: { pendienteId: accion.id },
              })} />
            ) : edicionEmprendimientoPendiente(accion) ? (
              ![403, 404, 410].includes(accion.errorStatus ?? 0) ? (
                <TextButton title="Ver propuesta" onPress={() => router.push('/mi-emprendimiento')} />
              ) : null
            ) : edicion ? (
              !edicionDescartableSinRevision(accion) ? (
                <TextButton title={edicion.inventario ? 'Revisar ajuste' : 'Revisar edición'} onPress={() => router.push({
                  pathname: '/editar-producto', params: { productoId: edicion.productoId, pendienteId: accion.id },
                })} />
              ) : null
            ) : (
              <TextButton title="Reintentar" onPress={() => void outbox.reintentar(accion.id)} />
            )}
            <TextButton title="Descartar" onPress={() => void outbox.descartar(accion.id).then(() => {
              if (pedido) return borrarCarritoEncolado(accion.usuarioId, accion.idempotencyKey);
            }).catch(() => undefined)} />
          </View>
        </>
      ) : (
        <Text style={styles.detail}>
          {accion.attempts === 0 ? 'Pendiente de envío' : `Pendiente de envío · ${accion.attempts} intentos`}
        </Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  section: { color: palette.dark, fontFamily: fonts.sans, fontSize: 16, fontWeight: '800', marginTop: 12 },
  card: {
    backgroundColor: palette.white,
    borderColor: palette.border,
    borderRadius: 16,
    borderWidth: 1,
    marginVertical: 6,
    padding: 14,
  },
  failed: { borderColor: palette.error },
  title: { color: palette.dark, fontFamily: fonts.sans, fontSize: 15, fontWeight: '800' },
  detail: { color: palette.muted, fontFamily: fonts.sans, fontSize: 13, marginTop: 4 },
  error: { color: palette.error, fontFamily: fonts.sans, fontSize: 13, marginTop: 4 },
  actions: { flexDirection: 'row', gap: 12 },
});
