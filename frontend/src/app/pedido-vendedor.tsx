import { useEffect, useRef, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import * as Crypto from 'expo-crypto';
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
import { ApiError } from '../lib/api';
import { clearSession, getValidSession } from '../lib/auth';
import {
  aceptarPedido,
  obtenerPedidoVendedor,
  rechazarPedido,
  type PedidoVendedorDetalle,
} from '../lib/pedidos';

const sellerRoles = ['VENDEDOR_AMBULANTE', 'VENDEDOR_PUNTO_FIJO'];
type ResponseAction = 'accept' | 'reject';

const statusNames: Record<string, string> = {
  PENDIENTE: 'Pendiente',
  ACEPTADO: 'Aceptado',
  RECHAZADO: 'Rechazado',
  LISTO_PARA_RECOGER: 'Listo para recoger',
  EN_CAMINO: 'En camino',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
};
const modalityNames: Record<string, string> = {
  ENTREGA_DIRECTA: 'Entrega directa',
  RECOGIDA: 'Recogida',
  DOMICILIO: 'Domicilio',
};

function formatDate(value: string) {
  return new Date(value).toLocaleString('es-CO', {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
}

function formatPrice(value: number) {
  return `$${value.toLocaleString('es-CO')} COP`;
}

function actionError(cause: unknown) {
  if (cause instanceof ApiError && cause.code === 'OUT_OF_STOCK') {
    return 'No hay inventario suficiente. Actualiza las cantidades o rechaza el pedido.';
  }
  if (cause instanceof ApiError && cause.code === 'BANK_ACCOUNT_REQUIRED') {
    return 'Registra una cuenta bancaria antes de aceptar un pedido a domicilio.';
  }
  return cause instanceof Error ? cause.message : 'No se pudo responder el pedido.';
}

export default function PedidoVendedorScreen() {
  const { pedidoId: rawPedidoId } = useLocalSearchParams<{ pedidoId?: string | string[] }>();
  const pedidoId = Array.isArray(rawPedidoId) ? rawPedidoId[0] : rawPedidoId;
  const [detail, setDetail] = useState<PedidoVendedorDetalle | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [pendingAction, setPendingAction] = useState<ResponseAction | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const actionKey = useRef<{ action: ResponseAction; key: string } | null>(null);
  const acceptanceConfirmation = detail?.reserva
    ? '¿Confirmas la aceptación? Las reservas no validan ni descuentan inventario.'
    : '¿Confirmas la aceptación? El inventario se descontará al aceptar.';

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setError(null);
      if (!pedidoId) {
        setError('No se indicó el pedido que quieres consultar.');
        setLoading(false);
        return;
      }
      try {
        const session = await getValidSession();
        if (!active) return;
        if (!sellerRoles.includes(session.rol)) {
          setError('Solo las cuentas de vendedor pueden consultar el detalle del pedido.');
          return;
        }
        const response = await obtenerPedidoVendedor(pedidoId);
        if (active) setDetail(response);
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 401) {
          await clearSession();
          router.replace('/login');
          return;
        }
        if (active) {
          setError(cause instanceof Error ? cause.message : 'No se pudo cargar el pedido.');
        }
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [pedidoId, loadAttempt]);

  function cancelResponse() {
    actionKey.current = null;
    setPendingAction(null);
    setError(null);
  }

  async function respond() {
    if (!pedidoId || !pendingAction || busy) return;
    setError(null);
    setNotice(null);
    const action = pendingAction;
    if (actionKey.current?.action !== action) {
      actionKey.current = { action, key: Crypto.randomUUID() };
    }
    const idempotencyKey = actionKey.current.key;
    setBusy(true);
    try {
      if (action === 'accept') {
        await aceptarPedido(pedidoId, idempotencyKey);
        setNotice('Pedido aceptado. El pago quedó pendiente de confirmación.');
      } else {
        await rechazarPedido(pedidoId, idempotencyKey);
        setNotice('Pedido rechazado.');
      }
      actionKey.current = null;
      setPendingAction(null);
      setDetail(null);
      setLoadAttempt((attempt) => attempt + 1);
    } catch (cause) {
      if (cause instanceof ApiError) actionKey.current = null;
      setError(actionError(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Pedido recibido"
        title={detail ? `Pedido de ${detail.cliente.nombre}` : 'Detalle del pedido'}
        description="Revisa los productos, la entrega y el pago antes de responder."
      />
      {loading ? (
        <ActivityIndicator accessibilityLabel="Cargando pedido" color={palette.green} />
      ) : null}
      <ErrorBanner message={error} />
      {detail ? (
        <>
          <View style={styles.card}>
            <Text style={styles.status}>{statusNames[detail.estado] ?? detail.estado}</Text>
            <Text style={styles.detailText}>Modalidad: {modalityNames[detail.modalidad]}</Text>
            <Text style={styles.detailText}>Recibido: {formatDate(detail.createdAt)}</Text>
            {detail.reserva ? (
              <Text style={styles.reservation}>Reserva: {formatDate(detail.reserva)}</Text>
            ) : null}
          </View>
          <Text accessibilityRole="header" style={styles.sectionTitle}>Productos</Text>
          {detail.items.map((item) => (
            <View key={item.productoId} style={styles.card}>
              <Text style={styles.itemName}>{item.nombre}</Text>
              <Text style={styles.detailText}>
                {item.cantidad.toLocaleString('es-CO')} {item.unidad.toLowerCase()} × {formatPrice(item.precioUnitario)}
              </Text>
              <Text style={styles.itemSubtotal}>Subtotal: {formatPrice(item.subtotal)}</Text>
              {item.productoEliminado ? (
                <Text style={styles.reservation}>Este producto ya no está en tu catálogo.</Text>
              ) : null}
            </View>
          ))}
          <View style={styles.card}>
            <Text style={styles.sectionTitle}>Entrega y pago</Text>
            {detail.ubicacionEntrega ? (
              <>
                <Text style={styles.detailText}>{detail.ubicacionEntrega.direccion}</Text>
                {detail.ubicacionEntrega.referencia ? (
                  <Text style={styles.detailText}>Referencia: {detail.ubicacionEntrega.referencia}</Text>
                ) : null}
                <Text style={styles.detailText}>
                  {detail.ubicacionEntrega.latitud}, {detail.ubicacionEntrega.longitud}
                </Text>
              </>
            ) : (
              <Text style={styles.detailText}>No requiere ubicación de entrega.</Text>
            )}
            <Text style={styles.detailText}>Medio: {detail.medioPago}</Text>
            <Text style={styles.detailText}>Subtotal: {formatPrice(detail.subtotal)}</Text>
            {detail.tarifaDomicilio !== null ? (
              <Text style={styles.detailText}>Domicilio: {formatPrice(detail.tarifaDomicilio)}</Text>
            ) : null}
            <Text style={styles.total}>Total: {formatPrice(detail.total)}</Text>
            <Text style={styles.detailText}>
              Estado del pago: {detail.pago?.estado ?? 'Aún no creado'}
            </Text>
          </View>
          {notice ? <Text accessibilityRole="status" style={styles.notice}>{notice}</Text> : null}
          {detail.estado === 'PENDIENTE' && !pendingAction && !loading ? (
            <>
              <HelperText>Responder requiere conexión a internet.</HelperText>
              <PrimaryButton title="Aceptar pedido" onPress={() => setPendingAction('accept')} />
              <TextButton title="Rechazar pedido" onPress={() => setPendingAction('reject')} />
            </>
          ) : null}
          {pendingAction ? (
            <View style={styles.confirmation}>
              <Text style={styles.confirmationText}>
                {pendingAction === 'accept'
                  ? acceptanceConfirmation
                  : '¿Confirmas que no atenderás este pedido?'}
              </Text>
              {error === 'Registra una cuenta bancaria antes de aceptar un pedido a domicilio.' ? (
                <TextButton
                  title="Configurar métodos de pago"
                  onPress={() => router.push('/configuracion-pagos')}
                />
              ) : null}
              {error?.startsWith('No hay inventario suficiente') ? (
                <TextButton title="Revisar mis productos" onPress={() => router.push('/mis-productos')} />
              ) : null}
              <PrimaryButton
                title={pendingAction === 'accept' ? 'Confirmar aceptación' : 'Confirmar rechazo'}
                busy={busy}
                onPress={() => void respond()}
              />
              <TextButton title="Cancelar" disabled={busy} onPress={cancelResponse} />
            </View>
          ) : null}
        </>
      ) : null}
      {!loading && error && !detail ? (
        <PrimaryButton title="Reintentar carga" onPress={() => setLoadAttempt((attempt) => attempt + 1)} />
      ) : null}
      <TextButton title="Volver a pedidos" onPress={() => router.replace('/pedidos-vendedor')} />
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
  status: { color: palette.green, fontSize: 17, fontWeight: '800' },
  notice: { color: palette.green, fontSize: 14, fontWeight: '700', marginVertical: 10 },
  detailText: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 6 },
  reservation: { color: '#86510F', fontSize: 14, fontWeight: '700', marginTop: 6 },
  sectionTitle: { color: palette.dark, fontSize: 18, fontWeight: '800', marginTop: 12 },
  itemName: { color: palette.dark, fontSize: 16, fontWeight: '800' },
  itemSubtotal: { color: palette.dark, fontSize: 14, fontWeight: '700', marginTop: 6 },
  total: { color: palette.green, fontSize: 17, fontWeight: '800', marginTop: 10 },
  confirmation: {
    backgroundColor: '#FFF8E8',
    borderColor: '#E8D8AE',
    borderRadius: 14,
    borderWidth: 1,
    marginTop: 10,
    padding: 14,
  },
  confirmationText: { color: palette.dark, fontSize: 15, lineHeight: 22 },
});
