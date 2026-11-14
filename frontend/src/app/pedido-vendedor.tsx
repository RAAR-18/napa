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
import DomiciliarioAsignado from '../components/DomiciliarioAsignado';
import PagoTarifaDomicilio from '../components/PagoTarifaDomicilio';
import { ApiError } from '../lib/api';
import { clearSession, getValidSession } from '../lib/auth';
import {
  confirmarSalida,
  estadoDomicilioNombres,
  motivoCancelacionNombres,
  publicarDomicilio,
} from '../lib/domicilios';
import {
  MARGEN_RESERVA_MS,
  aceptarPedido,
  confirmarEntregaDirecta,
  confirmarPagoPedido,
  confirmarRetiro,
  iniciarEntregaDirecta,
  marcarListoParaRecoger,
  obtenerPedidoVendedor,
  rechazarPedido,
  type PedidoVendedorDetalle,
} from '../lib/pedidos';

const sellerRoles = ['VENDEDOR_AMBULANTE', 'VENDEDOR_PUNTO_FIJO'];
type ResponseAction =
  | 'accept'
  | 'reject'
  | 'confirmPayment'
  | 'publishDelivery'
  | 'confirmDeparture'
  | 'startDelivery'
  | 'confirmDelivery'
  | 'markReady'
  | 'confirmPickup';

/** Lo que el vendedor puede hacer con la reserva desde 15 minutos antes (RESERVATION_NOT_DUE). */
const reservationActions: Partial<Record<ResponseAction, string>> = {
  publishDelivery: 'publicar el domicilio',
  startDelivery: 'iniciar la entrega',
  markReady: 'marcarlo como listo',
};

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

function paymentText(detail: PedidoVendedorDetalle) {
  return detail.medioPago === 'EFECTIVO'
    ? `${formatPrice(detail.total)} en efectivo`
    : `la transferencia por ${formatPrice(detail.total)} en tu cuenta (verifícala en tu banco)`;
}

function actionError(cause: unknown, reserva: string | null | undefined, action: ResponseAction) {
  if (cause instanceof ApiError && cause.code === 'OUT_OF_STOCK') {
    return 'No hay inventario suficiente. Actualiza las cantidades o rechaza el pedido.';
  }
  if (cause instanceof ApiError && cause.code === 'BANK_ACCOUNT_REQUIRED') {
    return 'Registra una cuenta bancaria antes de aceptar un pedido a domicilio.';
  }
  if (cause instanceof ApiError && cause.code === 'PAYMENT_NOT_CONFIRMED') {
    return 'Primero debes confirmar el pago del pedido.';
  }
  if (cause instanceof ApiError && cause.code === 'ALREADY_PUBLISHED') {
    return 'Este pedido ya tiene un domicilio activo.';
  }
  if (cause instanceof ApiError && cause.code === 'RESERVATION_NOT_DUE') {
    const what = reservationActions[action] ?? 'atender esta reserva';
    const from =
      typeof cause.details.disponibleDesde === 'string'
        ? cause.details.disponibleDesde
        : reserva
          ? new Date(new Date(reserva).getTime() - MARGEN_RESERVA_MS).toISOString()
          : null;
    return from ? `Podrás ${what} desde ${formatDate(from)}.` : `Aún no puedes ${what} de esta reserva.`;
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
  const [userRole, setUserRole] = useState<string | null>(null);
  const actionKey = useRef<{ action: ResponseAction; key: string } | null>(null);
  const activeDelivery =
    detail?.domicilio && detail.domicilio.estado !== 'CANCELADO' ? detail.domicilio : null;
  const canPublishDelivery =
    userRole === 'VENDEDOR_PUNTO_FIJO' &&
    detail?.estado === 'ACEPTADO' &&
    detail.modalidad === 'DOMICILIO' &&
    detail.pago?.estado === 'CONFIRMADO' &&
    !activeDelivery;
  const canConfirmDeparture =
    userRole === 'VENDEDOR_PUNTO_FIJO' && detail?.domicilio?.estado === 'RECOGIDO';
  const isStreetSeller = userRole === 'VENDEDOR_AMBULANTE';
  const isFixedSeller = userRole === 'VENDEDOR_PUNTO_FIJO';
  const canStartDelivery =
    isStreetSeller && detail?.modalidad === 'ENTREGA_DIRECTA' && detail.estado === 'ACEPTADO';
  const canConfirmDelivery =
    isStreetSeller && detail?.modalidad === 'ENTREGA_DIRECTA' && detail.estado === 'EN_CAMINO';
  const canMarkReady = isFixedSeller && detail?.modalidad === 'RECOGIDA' && detail.estado === 'ACEPTADO';
  const canConfirmPickup =
    isFixedSeller && detail?.modalidad === 'RECOGIDA' && detail.estado === 'LISTO_PARA_RECOGER';
  const canSeeCustomerOnMap =
    isStreetSeller &&
    detail?.modalidad === 'ENTREGA_DIRECTA' &&
    (detail.estado === 'ACEPTADO' || detail.estado === 'EN_CAMINO') &&
    Boolean(detail.ubicacionEntrega);
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
        setUserRole(session.rol);
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
      } else if (action === 'reject') {
        await rechazarPedido(pedidoId, idempotencyKey);
        setNotice('Pedido rechazado.');
      } else if (action === 'confirmPayment') {
        await confirmarPagoPedido(pedidoId, idempotencyKey);
        setNotice('Pago confirmado. Ya puedes publicar el domicilio.');
      } else if (action === 'startDelivery') {
        await iniciarEntregaDirecta(pedidoId, idempotencyKey);
        setNotice('Entrega iniciada. Avisamos al cliente que vas en camino.');
      } else if (action === 'confirmDelivery') {
        await confirmarEntregaDirecta(pedidoId, idempotencyKey);
        setNotice('Entrega confirmada y pago registrado.');
      } else if (action === 'markReady') {
        await marcarListoParaRecoger(pedidoId, idempotencyKey);
        setNotice('Pedido listo. Avisamos al cliente que ya puede recogerlo.');
      } else if (action === 'confirmPickup') {
        await confirmarRetiro(pedidoId, idempotencyKey);
        setNotice('Retiro confirmado y pago registrado.');
      } else if (action === 'confirmDeparture') {
        if (!detail?.domicilio) return;
        await confirmarSalida(detail.domicilio.id, idempotencyKey);
        setNotice('Salida confirmada. El pedido va en camino al cliente.');
      } else {
        await publicarDomicilio(pedidoId, idempotencyKey);
        setNotice('Domicilio publicado. Los domiciliarios ya pueden verlo y aceptarlo.');
      }
      actionKey.current = null;
      setPendingAction(null);
      setDetail(null);
      setLoadAttempt((attempt) => attempt + 1);
    } catch (cause) {
      if (cause instanceof ApiError) actionKey.current = null;
      setError(actionError(cause, detail?.reserva, action));
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
                {detail.ubicacionEntrega.barrio ? (
                  <Text style={styles.detailText}>Barrio: {detail.ubicacionEntrega.barrio}</Text>
                ) : null}
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
            {canSeeCustomerOnMap ? (
              <TextButton
                title="Ver ubicación en el mapa"
                onPress={() => router.push({ pathname: '/ubicacion-entrega', params: { pedidoId: detail.id } })}
              />
            ) : null}
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
          {detail.domicilio ? (
            <View style={styles.card}>
              <Text style={styles.sectionTitle}>Domicilio</Text>
              <Text style={styles.status}>{estadoDomicilioNombres[detail.domicilio.estado]}</Text>
              {detail.domicilio.estado !== 'CANCELADO' ? (
                <DomiciliarioAsignado pedidoId={detail.id} estadoDomicilio={detail.domicilio.estado} />
              ) : null}
              {detail.domicilio.motivoCancelacion ? (
                <Text style={styles.detailText}>
                  {motivoCancelacionNombres[detail.domicilio.motivoCancelacion]}
                </Text>
              ) : null}
              {detail.domicilio.estado === 'DISPONIBLE' ? (
                <Text style={styles.detailText}>
                  Esperando a que un domiciliario lo acepte. Si nadie lo toma en 2 horas, el pedido se cancela.
                </Text>
              ) : null}
              {detail.domicilio.estado === 'ASIGNADO' ? (
                <Text style={styles.detailText}>
                  Un domiciliario aceptó el domicilio y viene a recoger el pedido.
                </Text>
              ) : null}
              {detail.domicilio.estado === 'RECOGIDO' ? (
                <Text style={styles.detailText}>
                  El domiciliario confirmó la recogida. Confirma su salida cuando se vaya con el pedido.
                </Text>
              ) : null}
              <TextButton
                title="Ver trazabilidad"
                onPress={() =>
                  router.push({
                    pathname: '/trazabilidad-domicilio',
                    params: { domicilioId: detail.domicilio?.id ?? '' },
                  })
                }
              />
            </View>
          ) : null}
          {detail.domicilio?.estado === 'ENTREGADO' && userRole === 'VENDEDOR_PUNTO_FIJO' ? (
            <PagoTarifaDomicilio domicilioId={detail.domicilio.id} rol="VENDEDOR" />
          ) : null}
          {notice ? <Text accessibilityRole="status" style={styles.notice}>{notice}</Text> : null}
          {detail.estado === 'PENDIENTE' && !pendingAction && !loading ? (
            <>
              <HelperText>Responder requiere conexión a internet.</HelperText>
              <PrimaryButton title="Aceptar pedido" onPress={() => setPendingAction('accept')} />
              <TextButton title="Rechazar pedido" onPress={() => setPendingAction('reject')} />
            </>
          ) : null}
          {userRole === 'VENDEDOR_PUNTO_FIJO' &&
          detail.estado === 'ACEPTADO' &&
          detail.modalidad === 'DOMICILIO' &&
          detail.pago?.estado === 'PENDIENTE_CONFIRMACION' &&
          !pendingAction &&
          !loading ? (
            <>
              <HelperText>
                Confirma el pago solo después de verificar en tu banco que recibiste la transferencia por el total del pedido.
              </HelperText>
              <PrimaryButton
                title="Confirmar pago recibido"
                onPress={() => setPendingAction('confirmPayment')}
              />
            </>
          ) : null}
          {canPublishDelivery && !pendingAction && !loading ? (
            <>
              <HelperText>
                Publica el domicilio cuando el pedido esté listo o casi listo. Una vez publicado no se puede editar.
              </HelperText>
              <PrimaryButton
                title="Publicar domicilio"
                onPress={() => setPendingAction('publishDelivery')}
              />
            </>
          ) : null}
          {canConfirmDeparture && !pendingAction && !loading ? (
            <PrimaryButton title="Confirmar salida" onPress={() => setPendingAction('confirmDeparture')} />
          ) : null}
          {canStartDelivery && !pendingAction && !loading ? (
            <>
              {detail.reserva ? (
                <HelperText>Podrás iniciar la entrega desde 15 minutos antes de la hora acordada.</HelperText>
              ) : null}
              <PrimaryButton title="Iniciar entrega" onPress={() => setPendingAction('startDelivery')} />
            </>
          ) : null}
          {canConfirmDelivery && !pendingAction && !loading ? (
            <>
              <HelperText>
                Confirma solo cuando el cliente tenga todo el pedido y hayas recibido el pago. Si no lo encuentras o no
                paga, no confirmes la entrega.
              </HelperText>
              <PrimaryButton title="Confirmar entrega y cobro" onPress={() => setPendingAction('confirmDelivery')} />
            </>
          ) : null}
          {canMarkReady && !pendingAction && !loading ? (
            <>
              {detail.reserva ? (
                <HelperText>Podrás marcarlo como listo desde 15 minutos antes de la hora acordada.</HelperText>
              ) : null}
              <PrimaryButton title="Marcar listo para recoger" onPress={() => setPendingAction('markReady')} />
            </>
          ) : null}
          {canConfirmPickup && !pendingAction && !loading ? (
            <>
              <HelperText>
                Confirma cuando el cliente retire todo el pedido y hayas recibido el pago.
              </HelperText>
              <PrimaryButton title="Confirmar retiro y cobro" onPress={() => setPendingAction('confirmPickup')} />
            </>
          ) : null}
          {pendingAction ? (
            <View style={styles.confirmation}>
              <Text style={styles.confirmationText}>
                {pendingAction === 'accept'
                  ? acceptanceConfirmation
                  : pendingAction === 'reject'
                    ? '¿Confirmas que no atenderás este pedido?'
                    : pendingAction === 'confirmPayment'
                      ? `¿Confirmas que verificaste la transferencia por ${formatPrice(detail.total)}?`
                      : pendingAction === 'confirmDeparture'
                        ? '¿Confirmas que el domiciliario salió con el pedido? El cliente verá que va en camino.'
                        : pendingAction === 'startDelivery'
                          ? '¿Sales a entregar el pedido? El cliente verá que vas en camino.'
                          : pendingAction === 'confirmDelivery'
                            ? `¿Entregaste el pedido completo y recibiste ${paymentText(detail)}?`
                            : pendingAction === 'markReady'
                              ? '¿El pedido ya está listo? Avisaremos al cliente que puede pasar a recogerlo.'
                              : pendingAction === 'confirmPickup'
                                ? `¿El cliente retiró el pedido completo y recibiste ${paymentText(detail)}?`
                                : `¿Publicar el domicilio? Los domiciliarios verán el pedido con una tarifa de ${formatPrice(detail.tarifaDomicilio ?? 0)} y no podrás editarlo.`}
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
                title={
                  pendingAction === 'accept'
                    ? 'Confirmar aceptación'
                    : pendingAction === 'reject'
                      ? 'Confirmar rechazo'
                      : pendingAction === 'confirmPayment'
                        ? 'Confirmar pago recibido'
                        : pendingAction === 'confirmDeparture'
                          ? 'Sí, ya salió'
                          : pendingAction === 'startDelivery'
                            ? 'Sí, voy en camino'
                            : pendingAction === 'confirmDelivery'
                              ? 'Sí, entregado y cobrado'
                              : pendingAction === 'markReady'
                                ? 'Sí, está listo'
                                : pendingAction === 'confirmPickup'
                                  ? 'Sí, retirado y cobrado'
                                  : 'Confirmar publicación'
                }
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
