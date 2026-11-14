import { useEffect, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  HelperText,
  PageHeading,
  PrimaryButton,
  TextButton,
  palette,
} from '../components/AuthUI';
import DomiciliarioAsignado from '../components/DomiciliarioAsignado';
import { ApiError } from '../lib/api';
import { clearSession, getValidSession } from '../lib/auth';
import {
  estadoDomicilioNombres,
  motivoCancelacionNombres,
  obtenerCodigoConfirmacion,
  type CodigoConfirmacion,
  type EstadoDomicilio,
} from '../lib/domicilios';
import {
  estadoPedidoNombres,
  modalidadPedidoNombres,
  obtenerPedidoCliente,
  type EstadoPedido,
  type ModalidadPedido,
  type PedidoClienteDetalle,
} from '../lib/pedidos';

/** RF-48: mientras la pantalla está abierta el estado se refresca solo. */
const REFRESCO_MS = 30_000;
const CODIGO_VISIBLE: EstadoDomicilio[] = ['ASIGNADO', 'RECOGIDO', 'EN_CAMINO'];

/** Pasos normales de cada modalidad (la API devuelve estados; los textos viven en la app). */
const pasosPorModalidad: Record<ModalidadPedido, EstadoPedido[]> = {
  ENTREGA_DIRECTA: ['PENDIENTE', 'ACEPTADO', 'EN_CAMINO', 'ENTREGADO'],
  RECOGIDA: ['PENDIENTE', 'ACEPTADO', 'LISTO_PARA_RECOGER', 'ENTREGADO'],
  DOMICILIO: ['PENDIENTE', 'ACEPTADO', 'EN_CAMINO', 'ENTREGADO'],
};

const mensajesEspera: Partial<Record<EstadoPedido, string>> = {
  PENDIENTE: 'Esperando a que el vendedor acepte tu pedido.',
  RECHAZADO: 'El vendedor no pudo atender tu pedido.',
  CANCELADO: 'El pedido fue cancelado.',
  ENTREGADO: 'Tu pedido fue entregado. ¡Gracias por comprar en Ñapa!',
};

const pagoNombres: Record<string, string> = {
  PENDIENTE_CONFIRMACION: 'Pendiente de confirmación',
  CONFIRMADO: 'Confirmado',
};

function formatDate(value: string) {
  return new Date(value).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
}

function formatPrice(value: number) {
  return `$${value.toLocaleString('es-CO')} COP`;
}

function LineaEstado({ modalidad, estado }: { modalidad: ModalidadPedido; estado: EstadoPedido }) {
  const pasos = pasosPorModalidad[modalidad];
  const actual = pasos.indexOf(estado);
  if (actual < 0) return null;
  return (
    <View accessibilityLabel={`Paso ${actual + 1} de ${pasos.length}`} style={styles.line}>
      {pasos.map((paso, index) => (
        <View key={paso} style={styles.lineStep}>
          <View style={[styles.dot, index <= actual && styles.dotDone]} />
          <Text style={[styles.lineText, index === actual && styles.lineTextCurrent]}>
            {estadoPedidoNombres[paso]}
          </Text>
        </View>
      ))}
    </View>
  );
}

export default function PedidoClienteScreen() {
  const { pedidoId: rawId } = useLocalSearchParams<{ pedidoId?: string | string[] }>();
  const pedidoId = Array.isArray(rawId) ? rawId[0] : rawId;
  const [detail, setDetail] = useState<PedidoClienteDetalle | null>(null);
  const [codigo, setCodigo] = useState<CodigoConfirmacion | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    async function load(silent: boolean) {
      if (!silent) {
        setLoading(true);
        setError(null);
      }
      if (!pedidoId) {
        setError('No se indicó el pedido que quieres consultar.');
        setLoading(false);
        return;
      }
      try {
        const session = await getValidSession();
        if (!active) return;
        if (session.rol !== 'CLIENTE') {
          setError('Solo el cliente que hizo el pedido puede consultarlo aquí.');
          return;
        }
        const response = await obtenerPedidoCliente(pedidoId);
        if (!active) return;
        setDetail(response);
        const delivery = response.domicilio;
        if (delivery && CODIGO_VISIBLE.includes(delivery.estado)) {
          try {
            const code = await obtenerCodigoConfirmacion(delivery.id);
            if (active) setCodigo(code);
          } catch {
            // El domicilio pudo cambiar de estado entre ambas consultas; el siguiente refresco lo corrige.
            if (active) setCodigo(null);
          }
        } else if (active) {
          setCodigo(null);
        }
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 401) {
          await clearSession();
          router.replace('/login');
          return;
        }
        if (active && !silent) {
          setError(
            cause instanceof ApiError && cause.status === 404
              ? 'El pedido no está disponible.'
              : cause instanceof Error
                ? cause.message
                : 'No se pudo cargar el pedido.',
          );
        }
      } finally {
        if (active && !silent) setLoading(false);
      }
    }
    void load(false);
    const timer = setInterval(() => void load(true), REFRESCO_MS);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [pedidoId, loadAttempt]);

  const delivery = detail?.domicilio ?? null;

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Mi pedido"
        title={detail ? `Pedido a ${detail.emprendimiento.nombre}` : 'Estado del pedido'}
        description="El estado se actualiza solo mientras tienes esta pantalla abierta."
      />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando pedido" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {detail ? (
        <>
          <View style={styles.card}>
            <Text style={styles.status}>{estadoPedidoNombres[detail.estado]}</Text>
            <Text style={styles.detailText}>Modalidad: {modalidadPedidoNombres[detail.modalidad]}</Text>
            <Text style={styles.detailText}>Última actualización: {formatDate(detail.updatedAt)}</Text>
            {detail.reserva ? (
              <Text style={styles.detailText}>Reserva: {formatDate(detail.reserva)}</Text>
            ) : null}
            {mensajesEspera[detail.estado] ? (
              <Text style={styles.detailText}>{mensajesEspera[detail.estado]}</Text>
            ) : null}
            <LineaEstado modalidad={detail.modalidad} estado={detail.estado} />
          </View>
          {codigo ? (
            <View style={styles.codeCard}>
              <Text style={styles.sectionTitle}>Código de entrega</Text>
              <Text accessibilityLabel={`Código ${codigo.codigo.split('').join(' ')}`} style={styles.code}>
                {codigo.codigo}
              </Text>
              <Text style={styles.detailText}>
                Dáselo al domiciliario solo cuando tengas el pedido en tus manos.
              </Text>
            </View>
          ) : null}
          {delivery ? (
            <View style={styles.card}>
              <Text style={styles.sectionTitle}>Domicilio</Text>
              <Text style={styles.detailText}>Estado: {estadoDomicilioNombres[delivery.estado]}</Text>
              {delivery.estado !== 'CANCELADO' ? (
                <DomiciliarioAsignado pedidoId={detail.id} estadoDomicilio={delivery.estado} />
              ) : null}
              {delivery.motivoCancelacion ? (
                <Text style={styles.detailText}>{motivoCancelacionNombres[delivery.motivoCancelacion]}</Text>
              ) : null}
              {delivery.estado === 'DISPONIBLE' ? (
                <Text style={styles.detailText}>
                  Buscando domiciliario. Tu código aparecerá cuando uno acepte el domicilio.
                </Text>
              ) : null}
              <TextButton
                title="Ver trazabilidad"
                onPress={() =>
                  router.push({ pathname: '/trazabilidad-domicilio', params: { domicilioId: delivery.id } })
                }
              />
            </View>
          ) : null}
          <View style={styles.card}>
            <Text style={styles.sectionTitle}>Productos</Text>
            {detail.items.map((item) => (
              <Text key={item.productoId} style={styles.detailText}>
                {item.cantidad.toLocaleString('es-CO')} {item.unidad.toLowerCase()} de {item.nombre}:{' '}
                {formatPrice(item.subtotal)}
              </Text>
            ))}
            {detail.tarifaDomicilio !== null ? (
              <Text style={styles.detailText}>Domicilio: {formatPrice(detail.tarifaDomicilio)}</Text>
            ) : null}
            <Text style={styles.total}>Total: {formatPrice(detail.total)}</Text>
            {detail.pago ? (
              <Text style={styles.detailText}>
                Pago: {pagoNombres[detail.pago.estado] ?? detail.pago.estado}
              </Text>
            ) : null}
            {detail.modalidad === 'DOMICILIO' && detail.pago?.estado === 'PENDIENTE_CONFIRMACION' ? (
              <HelperText>
                Transfiere {formatPrice(detail.total)} al vendedor. Cuando lo confirme, publicará tu domicilio.
              </HelperText>
            ) : null}
          </View>
        </>
      ) : null}
      <PrimaryButton title="Actualizar" disabled={loading} onPress={() => setLoadAttempt((a) => a + 1)} />
      <TextButton title="Mis pedidos" onPress={() => router.replace('/mis-pedidos')} />
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
  codeCard: {
    alignItems: 'center',
    backgroundColor: '#EEF7F1',
    borderColor: palette.green,
    borderRadius: 16,
    borderWidth: 1,
    marginVertical: 7,
    padding: 16,
  },
  code: { color: palette.dark, fontSize: 34, fontWeight: '800', letterSpacing: 8, marginVertical: 8 },
  status: { color: palette.dark, fontSize: 18, fontWeight: '800' },
  sectionTitle: { color: palette.dark, fontSize: 16, fontWeight: '800' },
  detailText: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 5 },
  total: { color: palette.dark, fontWeight: '800', marginTop: 8 },
  line: { marginTop: 12 },
  lineStep: { alignItems: 'center', flexDirection: 'row', marginTop: 6 },
  dot: { backgroundColor: palette.border, borderRadius: 5, height: 10, marginRight: 10, width: 10 },
  dotDone: { backgroundColor: palette.green },
  lineText: { color: palette.muted, fontSize: 14 },
  lineTextCurrent: { color: palette.dark, fontWeight: '800' },
});
