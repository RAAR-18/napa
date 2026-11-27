import { useCalificacionPendiente } from '../hooks/useCalificacionPendiente';
import { fonts } from '../components/theme';
import { useEffect, useRef, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import { randomUUID } from '../lib/uuid';
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
import ClienteDelPedido from '../components/ClienteDelPedido';
import { GoogleMapsButton } from '../components/GoogleMapsButton';
import DomiciliarioAsignado from '../components/DomiciliarioAsignado';
import PagoTarifaDomicilio from '../components/PagoTarifaDomicilio';
import { EstadoConfirmacionDomicilio } from '../components/EstadoConfirmacionDomicilio';
import { rutaConfirmarPagoPedido } from '../lib/pago-pedido-pendiente';
import { cuerpoConfirmacionEntrega, rutaEntregaDirecta } from '../lib/entrega-directa-pendiente';
import { useConfirmacionDomicilio, useConfirmacionPendiente } from '../hooks/useConfirmacionDomicilio';
import { confirmacionReintentable } from '../lib/confirmacion-domicilio';
import { useAccionesPendientes } from '../hooks/useOutbox';
import { outbox } from '../database/outbox';
import { guardarConsultaPedidos, leerConsultaPedidos, quitarDetalleVendedorGuardado } from '../lib/pedidos-cliente-cache';
import { respuestaPedidoPendiente, rutaRespuestaPedido } from '../lib/respuesta-pedido-pendiente';
import { ApiError, NetworkError } from '../lib/api';
import { estadoPagoNombres } from '../lib/pagos';
import { clearSession, getValidSession, readSession } from '../lib/auth';
import {
  confirmarSalida,
  type TransicionDomicilio,
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
  type PagoPedidoConfirmado,
  type PedidoEntregado,
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
  const acciones = useAccionesPendientes();
  const [cuenta, setCuenta] = useState<string | null>(null);
  const [fechaGuardada, setFechaGuardada] = useState<number | null>(null);
  const [consultaServidor, setConsultaServidor] = useState(false);
  const decision = acciones.find((a) =>
    cuenta && a.usuarioId === cuenta && respuestaPedidoPendiente(a)?.pedidoId === pedidoId,
  );
  const estadoDecision = decision ? `${decision.id}:${decision.status}` : null;
  const decisionAnterior = useRef<string | null>(null);
  const enviando = useRef(false);
  const [detail, setDetail] = useState<PedidoVendedorDetalle | null>(null);
  const puedeCalificar = useCalificacionPendiente(detail?.id, detail?.estado === 'ENTREGADO');
  const [loading, setLoading] = useState(true);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [respuestaPago, setRespuestaPago] = useState<PagoPedidoConfirmado | null>(null);
  const pago = useConfirmacionPendiente<PagoPedidoConfirmado>({
    cuenta, ruta: pedidoId ? rutaConfirmarPagoPedido(pedidoId) : null,
    tipo: 'confirmacion-pago-pedido', descripcion: 'Confirmación de pago del pedido',
    actualizar: () => setLoadAttempt((intento) => intento + 1),
  });
  const confirmacionPago = pago.confirmada?.pedidoId === pedidoId ? pago.confirmada : respuestaPago;
  const pagoConfirmado = confirmacionPago?.pedidoId === pedidoId &&
    confirmacionPago?.pago?.estado === 'CONFIRMADO' ? confirmacionPago : null;

  const [respuestaEntrega, setRespuestaEntrega] = useState<PedidoEntregado | null>(null);
  const entrega = useConfirmacionPendiente<PedidoEntregado>({
    cuenta, ruta: pedidoId ? rutaEntregaDirecta(pedidoId) : null,
    tipo: 'confirmacion-entrega-directa', descripcion: 'Entrega directa y cobro',
    body: cuerpoConfirmacionEntrega, actualizar: () => setLoadAttempt((intento) => intento + 1),
  });
  const entregaConfirmada = entrega.confirmada ?? (respuestaEntrega?.id === pedidoId ? respuestaEntrega : null);
  const entregaConfirmadaOffline = fechaGuardada !== null && detail?.estado === 'EN_CAMINO' && entregaConfirmada;
  const estadoPedido = entregaConfirmadaOffline ? entregaConfirmadaOffline.estado : detail?.estado;
  const estadoPago = entregaConfirmadaOffline
  ? entregaConfirmadaOffline.pago.estado
  : fechaGuardada !== null && detail?.modalidad === 'DOMICILIO' && pagoConfirmado
    ? pagoConfirmado.pago.estado
    : detail?.pago?.estado;
  const [respuestaSalida, setRespuestaSalida] = useState<TransicionDomicilio | null>(null);
  const domicilioId = detail?.domicilio?.id;
  const salida = useConfirmacionDomicilio(cuenta, domicilioId, 'confirmar-salida',
    () => setLoadAttempt((intento) => intento + 1));
  const salidaConfirmada = salida.confirmada ?? (respuestaSalida?.id === domicilioId ? respuestaSalida : null);
  const estadoDomicilio = fechaGuardada !== null && detail?.domicilio?.estado === 'RECOGIDO' && salidaConfirmada
    ? salidaConfirmada.estado : detail?.domicilio?.estado;
  const [busy, setBusy] = useState(false);
  const [pendingAction, setPendingAction] = useState<ResponseAction | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [userRole, setUserRole] = useState<string | null>(null);
  const actionKey = useRef<{ action: ResponseAction; key: string } | null>(null);
  const activeDelivery =
    detail?.domicilio && detail.domicilio.estado !== 'CANCELADO' ? detail.domicilio : null;
  const pagoDomicilioPendiente =
    sellerRoles.includes(userRole ?? '') && detail?.estado === 'ACEPTADO' &&
    (detail.modalidad === 'DOMICILIO' || (Boolean(detail.reserva) && detail.medioPago === 'CUENTA_BANCARIA')) && estadoPago === 'PENDIENTE_CONFIRMACION';
  const datosTransferencia = !!detail && Number.isFinite(detail.total) && detail.total >= 0 &&
    detail.medioPago === 'CUENTA_BANCARIA';
  const canConfirmPayment = pagoDomicilioPendiente && datosTransferencia && !pago.pendiente && !pagoConfirmado;
  const canPublishDelivery =
    userRole === 'VENDEDOR_PUNTO_FIJO' &&
    detail?.estado === 'ACEPTADO' &&
    detail.modalidad === 'DOMICILIO' &&
    detail.pago?.estado === 'CONFIRMADO' &&
    !activeDelivery && !pago.pendiente;
  const canConfirmDeparture =
    userRole === 'VENDEDOR_PUNTO_FIJO' && estadoDomicilio === 'RECOGIDO' && !salida.pendiente && !salidaConfirmada;
  const isStreetSeller = userRole === 'VENDEDOR_AMBULANTE';
  const isFixedSeller = userRole === 'VENDEDOR_PUNTO_FIJO';
  const canStartDelivery =
    isStreetSeller && detail?.modalidad === 'ENTREGA_DIRECTA' && detail.estado === 'ACEPTADO';
  const entregaDirectaEnCamino =
    isStreetSeller && detail?.modalidad === 'ENTREGA_DIRECTA' && estadoPedido === 'EN_CAMINO';
  const datosCobro = !!detail && Number.isFinite(detail.total) && detail.total >= 0 &&
    (detail.medioPago === 'EFECTIVO' || detail.medioPago === 'CUENTA_BANCARIA');
  const canConfirmDelivery = entregaDirectaEnCamino && datosCobro && !entrega.pendiente && !entregaConfirmada;
  const canMarkReady = isFixedSeller && detail?.modalidad === 'RECOGIDA' && detail.estado === 'ACEPTADO';
  const canConfirmPickup =
    isFixedSeller && detail?.modalidad === 'RECOGIDA' && detail.estado === 'LISTO_PARA_RECOGER';
  const canSeeCustomerOnMap =
    isStreetSeller &&
    detail?.modalidad === 'ENTREGA_DIRECTA' &&
    (detail.estado === 'ACEPTADO' || detail.estado === 'EN_CAMINO') &&
    Boolean(detail.ubicacionEntrega);
  useEffect(() => {
    if (decisionAnterior.current && decisionAnterior.current !== estadoDecision) {
      setLoadAttempt((intento) => intento + 1);
    }
    decisionAnterior.current = estadoDecision;
  }, [estadoDecision]);

  const acceptanceConfirmation = detail?.reserva
    ? '¿Confirmas la aceptación? Las reservas no validan ni descuentan inventario.'
    : '¿Confirmas la aceptación? El inventario se descontará al aceptar.';

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setError(null);
      setConsultaServidor(false);
      setFechaGuardada(null);
      setDetail(null);
      if (!pedidoId) {
        setError('No se indicó el pedido que quieres consultar.');
        setLoading(false);
        return;
      }
      let local: Awaited<ReturnType<typeof readSession>> = null;
      try {
        local = typeof readSession === 'function' ? await readSession() : null;
        const session = await getValidSession();
        if (!active) return;
        setUserRole(session.rol);
        setCuenta(session.usuarioId ?? null);
        if (!sellerRoles.includes(session.rol)) {
          setError('Solo las cuentas de vendedor pueden consultar el detalle del pedido.');
          return;
        }
        const response = await obtenerPedidoVendedor(pedidoId);
        if (!active) return;
        setDetail(response);
        setConsultaServidor(true);
        if (session.usuarioId && response.id === pedidoId) {
          void guardarConsultaPedidos(session.usuarioId, 'detalle-vendedor', pedidoId, {
            ...response, ubicacionEntrega: null,
            domicilio: response.domicilio ? {
              id: response.domicilio.id, estado: response.domicilio.estado,
              motivoCancelacion: response.domicilio.motivoCancelacion,
            } : null,
          }, () => active).catch(() => undefined);
        }
      } catch (cause) {
        if (cause instanceof NetworkError && local?.usuarioId && sellerRoles.includes(local.rol)) {
          const copia = await leerConsultaPedidos<PedidoVendedorDetalle>(
            local.usuarioId, 'detalle-vendedor', pedidoId,
          ).catch(() => null);
          const vigente = await readSession();
          if (!active || vigente?.usuarioId !== local.usuarioId) return;
          setCuenta(local.usuarioId);
          if (copia?.data.id === pedidoId) {
            setUserRole(local.rol);
            setDetail(copia.data);
            setFechaGuardada(copia.fecha);
            return;
          }
          setError('No hay un detalle guardado para este pedido. Conéctate para consultarlo.');
          return;
        }
        if (cause instanceof ApiError && [401, 403, 404].includes(cause.status) && local?.usuarioId) {
          await quitarDetalleVendedorGuardado(local.usuarioId, pedidoId).catch(() => undefined);
        }
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
    if (!pedidoId || !pendingAction || busy || enviando.current) return;
    setError(null);
    setNotice(null);
    const action = pendingAction;
    if (actionKey.current?.action !== action) {
      actionKey.current = { action, key: randomUUID() };
    }
    const idempotencyKey = actionKey.current.key;
    const respuesta = action === 'accept' || action === 'reject' ? action : null;
    enviando.current = true;
    setBusy(true);
    try {
      if (respuesta && (detail?.id !== pedidoId || detail.estado !== 'PENDIENTE' || decision)) return;
      if (respuesta && cuenta) {
        if (cuenta !== await outbox.propietario()) throw new Error('La sesión cambió. Abre nuevamente el pedido.');
        const pendientes = await outbox.listar();
        if (pendientes.some((a) => respuestaPedidoPendiente(a)?.pedidoId === pedidoId)) {
          setPendingAction(null);
          return;
        }
      }
      if (respuesta && fechaGuardada !== null) {
        await guardarDecision(pedidoId, respuesta, idempotencyKey, false);
        return;
      }
      if (action === 'accept') {
        await aceptarPedido(pedidoId, idempotencyKey);
        setNotice('Pedido aceptado. El pago quedó pendiente de confirmación.');
      } else if (action === 'reject') {
        await rechazarPedido(pedidoId, idempotencyKey);
        setNotice('Pedido rechazado.');
      } else if (action === 'confirmPayment') {
        if (detail?.id !== pedidoId || !canConfirmPayment) return;
        if (await pago.yaEncolada()) {
          limpiarConfirmacionGuardada();
          return;
        }
        if (fechaGuardada !== null) {
          await pago.encolar(idempotencyKey, false);
          limpiarConfirmacionGuardada();
          return;
        }
        const resultado = await confirmarPagoPedido(pedidoId, idempotencyKey);
        setRespuestaPago(resultado);
        await pago.registrar(resultado).catch(() => undefined);
        setNotice(detail?.modalidad === 'DOMICILIO' ? 'Pago confirmado. Ya puedes publicar el domicilio.' : 'Anticipo de la reserva confirmado. No vuelvas a cobrar al entregar.');
      } else if (action === 'startDelivery') {
        await iniciarEntregaDirecta(pedidoId, idempotencyKey);
        setNotice('Entrega iniciada. Avisamos al cliente que vas en camino.');
      } else if (action === 'confirmDelivery') {
        if (detail?.id !== pedidoId || !canConfirmDelivery) return;
        if (await entrega.yaEncolada()) {
          limpiarConfirmacionGuardada();
          return;
        }
        if (fechaGuardada !== null) {
          await entrega.encolar(idempotencyKey, false);
          limpiarConfirmacionGuardada();
          return;
        }
        const resultado = await confirmarEntregaDirecta(pedidoId, idempotencyKey);
        setRespuestaEntrega(resultado);
        await entrega.registrar(resultado).catch(() => undefined);
        setNotice('Entrega confirmada y pago registrado.');
      } else if (action === 'markReady') {
        await marcarListoParaRecoger(pedidoId, idempotencyKey);
        setNotice('Pedido listo. Avisamos al cliente que ya puede recogerlo.');
      } else if (action === 'confirmPickup') {
        await confirmarRetiro(pedidoId, idempotencyKey);
        setNotice('Retiro confirmado y pago registrado.');
      } else if (action === 'confirmDeparture') {
        if (!domicilioId || !canConfirmDeparture) return;
        if (await salida.yaEncolada()) {
          limpiarConfirmacionGuardada();
          return;
        }
        if (fechaGuardada !== null) {
          await salida.encolar(idempotencyKey, false);
          limpiarConfirmacionGuardada();
          return;
        }
        const resultado = await confirmarSalida(domicilioId, idempotencyKey);
        setRespuestaSalida(resultado);
        await salida.registrar(resultado).catch(() => undefined);
        setNotice('Salida confirmada. El pedido va en camino al cliente.');
      } else {
        await publicarDomicilio(pedidoId, idempotencyKey);
        setNotice('Domicilio publicado. Los domiciliarios ya pueden verlo y aceptarlo.');
      }
      if (respuesta && cuenta) {
        await quitarDetalleVendedorGuardado(cuenta, pedidoId).catch(() => undefined);
      }
      actionKey.current = null;
      setPendingAction(null);
      setDetail(null);
      setLoadAttempt((attempt) => attempt + 1);
    } catch (cause) {
      if (
        (action === 'confirmDeparture' || action === 'confirmPayment' || action === 'confirmDelivery') &&
        cuenta && fechaGuardada === null && confirmacionReintentable(cause)
      ) {
        try {
          const confirmacion = action === 'confirmPayment' ? pago
            : action === 'confirmDelivery' ? entrega
            : salida;
          await confirmacion.encolar(idempotencyKey, true);
          limpiarConfirmacionGuardada();
          return;
        } catch (fallo) {
          setError(actionError(fallo, detail?.reserva, action));
          return;
        }
      }
      if (respuesta && cuenta && (cause instanceof NetworkError || (cause instanceof ApiError &&
        (cause.status >= 500 || cause.status === 408 || cause.status === 429 ||
          (cause.status === 409 && cause.code === 'IDEMPOTENCY_IN_PROGRESS'))))) {
        try {
          await guardarDecision(pedidoId, respuesta, idempotencyKey, true);
          return;
        } catch (fallo) {
          setError(actionError(fallo, detail?.reserva, action));
          return;
        }
      }
      if (cause instanceof ApiError) actionKey.current = null;
      setError(actionError(cause, detail?.reserva, action));
    } finally {
      enviando.current = false;
      setBusy(false);
    }
  }

  function limpiarConfirmacionGuardada() {
    actionKey.current = null;
    setPendingAction(null);
    setNotice(null);
  }

  async function guardarDecision(id: string, respuesta: 'accept' | 'reject', clave: string, intentoPrevio: boolean) {
    if (!cuenta || cuenta !== await outbox.propietario()) {
      throw new Error('La sesión cambió. Abre nuevamente el pedido.');
    }
    await outbox.encolar({
      usuarioId: cuenta, method: 'POST', path: rutaRespuestaPedido(id, respuesta),
      idempotencyKey: clave, intentoPrevio,
      descripcion: respuesta === 'accept' ? 'Aceptación de pedido' : 'Rechazo de pedido',
    });
    actionKey.current = null;
    setPendingAction(null);
    setNotice(null);
  }

  function descartarDecision() {
    if (!decision) return;
    void outbox.descartar(decision.id).catch((cause) => {
      setError(cause instanceof Error ? cause.message : 'No se pudo descartar la decisión.');
    });
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
      {fechaGuardada !== null ? (
        <HelperText>Datos guardados. Última actualización {formatDate(new Date(fechaGuardada).toISOString())}.</HelperText>
      ) : null}
      {decision ? (
        <>
          <HelperText>{decision.status === 'FALLIDA' ? 'Requiere revisión'
            : respuestaPedidoPendiente(decision)?.decision === 'accept'
              ? 'Aceptación pendiente de envío' : 'Rechazo pendiente de envío'}</HelperText>
          {decision.status === 'FALLIDA' && decision.lastError ? <HelperText>{decision.lastError}</HelperText> : null}
          {(decision.status === 'PENDIENTE' && decision.attempts === 0) ||
           (decision.status === 'FALLIDA' && consultaServidor && !loading) ? (
            <TextButton variant="soft" title={decision.status === 'FALLIDA' ? 'Cerrar revisión' : 'Descartar decisión'}
              onPress={descartarDecision} />
          ) : null}
        </>
      ) : null}
      {pedidoId ? (
        <TextButton icon="refresh-cw" variant="soft" title="Actualizar pedido" disabled={loading || busy} onPress={() => setLoadAttempt((a) => a + 1)} />
      ) : null}
      <EstadoConfirmacionDomicilio paso="confirmar-pago" pendiente={pago.pendiente}
        confirmada={pagoConfirmado ? { updatedAt: pagoConfirmado.pago.confirmadoAt } : null}
        cerrarRevision={consultaServidor && !loading && pago.pendiente?.status === 'FALLIDA'
          ? () => { void outbox.descartar(pago.pendiente!.id).catch((cause) =>
            setError(actionError(cause, detail?.reserva, 'confirmPayment'))); } : undefined} />
      <EstadoConfirmacionDomicilio paso="confirmar-entrega-directa" pendiente={entrega.pendiente}
        confirmada={entregaConfirmada} cerrarRevision={consultaServidor && !loading && entrega.pendiente?.status === 'FALLIDA'
          ? () => { void outbox.descartar(entrega.pendiente!.id).catch((cause) =>
            setError(actionError(cause, detail?.reserva, 'confirmDelivery'))); } : undefined} />
      {detail ? (
        <>
          <View style={styles.card}>
            <Text style={styles.status}>{statusNames[estadoPedido ?? detail.estado] ?? detail.estado}</Text>
            <Text style={styles.detailText}>Modalidad: {modalityNames[detail.modalidad]}</Text>
            <Text style={styles.detailText}>Recibido: {formatDate(detail.createdAt)}</Text>
            {detail.reserva ? (
              <Text style={styles.reservation}>Reserva: {formatDate(detail.reserva)}</Text>
            ) : null}
          </View>
          {consultaServidor ? <ClienteDelPedido pedidoId={detail.id} estado={detail.estado} /> : null}
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
              </>
            ) : (
              <Text style={styles.detailText}>No requiere ubicación de entrega.</Text>
            )}
            {canSeeCustomerOnMap && consultaServidor && detail.ubicacionEntrega ? (
              <View style={{ marginTop: 18, marginBottom: 18 }}>
                <GoogleMapsButton
                  title="Ver ubicación en Google Maps"
                  destino={detail.ubicacionEntrega}
                />
              </View>
            ) : null}
            <Text style={styles.detailText}>Medio: {detail.medioPago === 'CUENTA_BANCARIA' ? 'CUENTA BANCARIA' : 'EFECTIVO'}</Text>
            <Text style={styles.detailText}>Subtotal: {formatPrice(detail.subtotal)}</Text>
            {detail.tarifaDomicilio !== null ? (
              <Text style={styles.detailText}>Domicilio: {formatPrice(detail.tarifaDomicilio)}</Text>
            ) : null}
            <Text style={styles.total}>Total: {formatPrice(detail.total)}</Text>
            <Text style={styles.detailText}>
              Estado del pago: {estadoPago
                ? estadoPagoNombres[estadoPago as keyof typeof estadoPagoNombres] ?? 'Estado no disponible'
                : 'Aún no creado'}
            </Text>
          </View>
          {detail.domicilio ? (
            <View style={styles.card}>
              <Text style={styles.sectionTitle}>Domicilio</Text>
              <Text style={styles.status}>{estadoDomicilioNombres[estadoDomicilio ?? detail.domicilio.estado]}</Text>
              {consultaServidor && detail.domicilio.estado !== 'CANCELADO' ? (
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
              {estadoDomicilio === 'RECOGIDO' ? (
                <Text style={styles.detailText}>
                  El domiciliario confirmó la recogida. Confirma su salida cuando se vaya con el pedido.
                </Text>
              ) : null}
              <TextButton icon="map-pin" variant="soft"
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
          {consultaServidor && detail.domicilio?.estado === 'ENTREGADO' && userRole === 'VENDEDOR_PUNTO_FIJO' ? (
            <PagoTarifaDomicilio domicilioId={detail.domicilio.id} rol="VENDEDOR" />
          ) : null}
          {notice ? <Text aria-live="polite" style={styles.notice}>{notice}</Text> : null}
          {detail.estado === 'PENDIENTE' && !decision && !pendingAction && !loading ? (
            <>
              {fechaGuardada !== null ? <HelperText>La decisión se enviará al recuperar conexión.</HelperText> : null}
              <PrimaryButton title="Aceptar pedido" onPress={() => setPendingAction('accept')} />
              <TextButton icon="x-circle" variant="danger" title="Rechazar pedido" onPress={() => setPendingAction('reject')} />
            </>
          ) : null}
          {pagoDomicilioPendiente && !datosTransferencia && !loading ? (
            <HelperText>Conéctate para actualizar el total y el medio de pago antes de confirmar.</HelperText>
          ) : null}
          {detail.reserva && estadoPago === 'CONFIRMADO' ? <HelperText>Esta reserva ya está pagada. Confirma la entrega cuando corresponda, sin volver a cobrar.</HelperText> : null}
          {canConfirmPayment && !decision && !pendingAction && !loading ? (
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
          {canPublishDelivery && consultaServidor && !decision && !pendingAction && !loading ? (
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
          <EstadoConfirmacionDomicilio paso="confirmar-salida" pendiente={salida.pendiente}
            confirmada={salidaConfirmada} cerrarRevision={consultaServidor && !loading && salida.pendiente?.status === 'FALLIDA'
              ? () => { void outbox.descartar(salida.pendiente!.id).catch((cause) =>
                setError(actionError(cause, detail.reserva, 'confirmDeparture'))); } : undefined} />
          {canConfirmDeparture && !decision && !pendingAction && !loading ? (
            <PrimaryButton title="Confirmar salida" onPress={() => setPendingAction('confirmDeparture')} />
          ) : null}
          {canStartDelivery && consultaServidor && !decision && !pendingAction && !loading ? (
            <>
              {detail.reserva ? (
                <HelperText>Podrás iniciar la entrega desde 15 minutos antes de la hora acordada.</HelperText>
              ) : null}
              <PrimaryButton title="Iniciar entrega" onPress={() => setPendingAction('startDelivery')} />
            </>
          ) : null}
          {entregaDirectaEnCamino && !datosCobro && !loading ? (
            <HelperText>Conéctate para actualizar el total y el medio de pago antes de confirmar.</HelperText>
          ) : null}
          {canConfirmDelivery && !decision && !pendingAction && !loading ? (
            <>
              <HelperText>
                Confirma solo cuando el cliente tenga todo el pedido y hayas recibido el pago. Si no lo encuentras o no
                paga, no confirmes la entrega.
              </HelperText>
              <PrimaryButton title="Confirmar entrega y cobro" onPress={() => setPendingAction('confirmDelivery')} />
            </>
          ) : null}
          {canMarkReady && consultaServidor && !decision && !pendingAction && !loading ? (
            <>
              {detail.reserva ? (
                <HelperText>Podrás marcarlo como listo desde 15 minutos antes de la hora acordada.</HelperText>
              ) : null}
              <PrimaryButton title="Marcar listo para recoger" onPress={() => setPendingAction('markReady')} />
            </>
          ) : null}
          {canConfirmPickup && consultaServidor && !decision && !pendingAction && !loading ? (
            <>
              <HelperText>
                Confirma cuando el cliente retire todo el pedido y hayas recibido el pago.
              </HelperText>
              <PrimaryButton title="Confirmar retiro y cobro" onPress={() => setPendingAction('confirmPickup')} />
            </>
          ) : null}
          {pendingAction && !decision && !loading && (pendingAction !== 'confirmDeparture' || canConfirmDeparture) &&
            (pendingAction !== 'confirmPayment' || canConfirmPayment) &&
            (pendingAction !== 'confirmDelivery' || canConfirmDelivery) ? (
            <View style={styles.confirmation}>
              <Text style={styles.confirmationText}>
                {pendingAction === 'accept'
                  ? acceptanceConfirmation
                  : pendingAction === 'reject'
                    ? '¿Confirmas que no atenderás este pedido?'
                    : pendingAction === 'confirmPayment'
                      ? `¿Confirmas que verificaste la transferencia por ${formatPrice(detail.total)} en tu banco?`
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
                <TextButton variant="soft"
                  title="Configurar métodos de pago"
                  onPress={() => router.push('/configuracion-pagos')}
                />
              ) : null}
              {error?.startsWith('No hay inventario suficiente') ? (
                <TextButton variant="soft" title="Revisar mis productos" onPress={() => router.push('/mis-productos')} />
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
              <TextButton icon="x" variant="danger" title="Cancelar" disabled={busy} onPress={cancelResponse} />
            </View>
          ) : null}
        </>
      ) : null}
      {puedeCalificar && detail ? (
        <PrimaryButton
          title="Calificar al cliente"
          onPress={() => router.push({ pathname: '/calificar-pedido', params: { pedidoId: detail.id } })}
        />
      ) : null}
      {detail ? (
        <View style={{ marginTop: 24 }}>
          <TextButton icon="alert-triangle" variant="danger"
            title="Reportar un problema con este pedido"
            onPress={() =>
              router.push({
                pathname: '/reportar',
                params: detail.domicilio
                  ? { domicilioId: detail.domicilio.id, contexto: `Pedido de ${detail.cliente.nombre}` }
                  : { pedidoId: detail.id, contexto: `Pedido de ${detail.cliente.nombre}` },
              })
            }
          />
        </View>
      ) : null}
      {!loading && error && !detail ? (
        <PrimaryButton title="Reintentar carga" onPress={() => setLoadAttempt((attempt) => attempt + 1)} />
      ) : null}
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
  status: { color: palette.green, fontFamily: fonts.sans, fontSize: 17, fontWeight: '800' },
  notice: { color: palette.green, fontFamily: fonts.sans, fontSize: 14, fontWeight: '700', marginVertical: 10 },
  detailText: { color: palette.muted, fontFamily: fonts.sans, fontSize: 14, lineHeight: 21, marginTop: 6 },
  reservation: { color: '#86510F', fontFamily: fonts.sans, fontSize: 14, fontWeight: '700', marginTop: 6 },
  sectionTitle: { color: palette.dark, fontFamily: fonts.sans, fontSize: 18, fontWeight: '800', marginTop: 12 },
  itemName: { color: palette.dark, fontFamily: fonts.sans, fontSize: 16, fontWeight: '800' },
  itemSubtotal: { color: palette.dark, fontFamily: fonts.sans, fontSize: 14, fontWeight: '700', marginTop: 6 },
  total: { color: palette.green, fontFamily: fonts.sans, fontSize: 17, fontWeight: '800', marginTop: 10 },
  confirmation: {
    backgroundColor: '#FFF8E8',
    borderColor: '#E8D8AE',
    borderRadius: 14,
    borderWidth: 1,
    marginTop: 10,
    padding: 14,
  },
  confirmationText: { color: palette.dark, fontFamily: fonts.sans, fontSize: 15, lineHeight: 22 },
});
