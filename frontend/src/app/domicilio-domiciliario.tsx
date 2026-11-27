import { fonts } from '../components/theme';
import { GoogleMapsButton } from '../components/GoogleMapsButton';
import { useEffect, useRef, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import { randomUUID } from '../lib/uuid';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  Field,
  HelperText,
  PageHeading,
  PrimaryButton,
  TextButton,
  palette,
} from '../components/AuthUI';
import { EstadoConfirmacionDomicilio } from '../components/EstadoConfirmacionDomicilio';
import { outbox } from '../database/outbox';
import { useConfirmacionDomicilio } from '../hooks/useConfirmacionDomicilio';
import { confirmacionReintentable } from '../lib/confirmacion-domicilio';
import { guardarConsultaPedidos, leerConsultaPedidos, quitarDetalleGuardado } from '../lib/pedidos-cliente-cache';
import { ApiError, NetworkError } from '../lib/api';
import { clearSession, getValidSession, readSession } from '../lib/auth';
import {
  INTENTOS_CODIGO_ENTREGA,
  aceptarDomicilio,
  confirmarEntrega,
  confirmarRecogida,
  estadoDomicilioNombres,
  formatearDistancia,
  obtenerDomicilioDomiciliario,
  rechazarDomicilio,
  type DomicilioDomiciliarioDetalle,
  type PuntoDomiciliario,
  type DomicilioGuardado,
  type TransicionDomicilio,
} from '../lib/domicilios';
import { borrarRuta } from '../lib/ruta-cache';
import PagoTarifaDomicilio from '../components/PagoTarifaDomicilio';
import { useCompartirUbicacion } from '../hooks/useCompartirUbicacion';

type Action = 'accept' | 'reject';

function formatPrice(value: number) {
  return `$${value.toLocaleString('es-CO')} COP`;
}

function deliveryError(cause: unknown) {
  if (cause instanceof ApiError && cause.code === 'CODE_INVALID') {
    const remaining = cause.details.intentosRestantes;
    return typeof remaining === 'number'
      ? `El código no es válido. Te ${remaining === 1 ? 'queda 1 intento' : `quedan ${remaining} intentos`}.`
      : 'El código no es válido.';
  }
  if (cause instanceof ApiError && cause.code === 'CODE_ATTEMPTS_LOCKED') {
    return `Superaste los ${INTENTOS_CODIGO_ENTREGA} intentos. Espera 15 minutos o crea un reporte.`;
  }
  return actionError(cause);
}

function actionError(cause: unknown) {
  if (cause instanceof ApiError && cause.code === 'NOT_AVAILABLE') {
    return 'Este domicilio ya no está disponible.';
  }
  if (cause instanceof ApiError && cause.code === 'MAX_ACTIVE_DELIVERIES') {
    return 'Ya tienes 3 domicilios en curso. Entrega uno antes de aceptar otro.';
  }
  return cause instanceof Error ? cause.message : 'No se pudo completar la acción.';
}

function Punto({ title, punto }: { title: string; punto: PuntoDomiciliario }) {
  return (
    <View style={styles.card}>
      <Text style={styles.sectionTitle}>{title}</Text>
      {punto.aproximado ? (
        <Text style={styles.detailText}>
          {punto.barrio ? `Barrio ${punto.barrio}. ` : ''}Zona aproximada. Verás la dirección exacta al aceptar.
        </Text>
      ) : (
        <>
          <Text style={styles.detailText}>{punto.direccion ?? 'Sin dirección escrita'}</Text>
          {punto.barrio ? <Text style={styles.detailText}>Barrio: {punto.barrio}</Text> : null}
          {punto.referencia ? <Text style={styles.detailText}>Referencia: {punto.referencia}</Text> : null}
        </>
      )}
    </View>
  );
}

export default function DomicilioDomiciliarioScreen() {
  const { domicilioId: rawId } = useLocalSearchParams<{ domicilioId?: string | string[] }>();
  const domicilioId = Array.isArray(rawId) ? rawId[0] : rawId;
  const [detail, setDetail] = useState<DomicilioDomiciliarioDetalle | DomicilioGuardado | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [pendingAction, setPendingAction] = useState<Action | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [unavailable, setUnavailable] = useState(false);
  const actionKey = useRef<{ action: Action; key: string } | null>(null);
  const pickupKey = useRef<string | null>(null);
  const guardandoRecogida = useRef(false);
  const [cuenta, setCuenta] = useState<string | null>(null);
  const [fechaGuardada, setFechaGuardada] = useState<number | null>(null);
  const [enLinea, setEnLinea] = useState(false);
  const [respuestaDirecta, setRespuestaDirecta] = useState<TransicionDomicilio | null>(null);
  const recogida = useConfirmacionDomicilio(cuenta, domicilioId, 'confirmar-recogida',
    () => setLoadAttempt((intento) => intento + 1));
  const confirmada = recogida.confirmada ?? (respuestaDirecta?.id === domicilioId ? respuestaDirecta : null);
  const estado = fechaGuardada !== null && detail?.estado === 'ASIGNADO' && confirmada
    ? confirmada.estado : detail?.estado;
  const [codigo, setCodigo] = useState('');

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setError(null);
      setDetail(null);
      setEnLinea(false);
      setFechaGuardada(null);
      setUnavailable(false);
      if (!domicilioId) {
        setError('No se indicó el domicilio que quieres consultar.');
        setLoading(false);
        return;
      }
      let local: Awaited<ReturnType<typeof readSession>> = null;
      try {
        local = typeof readSession === 'function' ? await readSession() : null;
        const session = await getValidSession();
        if (!active) return;
        if (session.rol !== 'DOMICILIARIO') {
          setError('Solo las cuentas de domiciliario pueden consultar domicilios.');
          return;
        }
        setCuenta(session.usuarioId ?? null);
        const response = await obtenerDomicilioDomiciliario(domicilioId);
        if (!active) return;
        setDetail(response);
        setEnLinea(true);
        if (session.usuarioId) {
          if (response.asignadoAMi && response.id === domicilioId) {
            const { id, estado, ganancia, asignadoAMi, distanciaMetros, resumenItems, asignadoAt } = response;
            void guardarConsultaPedidos(session.usuarioId, 'detalle-domiciliario', domicilioId,
              { id, estado, ganancia, asignadoAMi, distanciaMetros, resumenItems, asignadoAt },
              () => active).catch(() => undefined);
          } else {
            void quitarDetalleGuardado(session.usuarioId, 'detalle-domiciliario', domicilioId).catch(() => undefined);
          }
        }
      } catch (cause) {
        if (cause instanceof NetworkError && local?.rol === 'DOMICILIARIO' && local.usuarioId) {
          const copia = await leerConsultaPedidos<DomicilioGuardado>(
            local.usuarioId, 'detalle-domiciliario', domicilioId,
          ).catch(() => null);
          const vigente = await readSession();
          if (!active || vigente?.usuarioId !== local.usuarioId) return;
          setCuenta(local.usuarioId);
          if (copia?.data.asignadoAMi && copia.data.id === domicilioId) {
            setDetail(copia.data);
            setFechaGuardada(copia.fecha);
          } else {
            setError('Conéctate para descargar el detalle de este domicilio.');
          }
          return;
        }
        if (cause instanceof ApiError && [401, 403, 404].includes(cause.status) && local?.usuarioId) {
          await quitarDetalleGuardado(local.usuarioId, 'detalle-domiciliario', domicilioId).catch(() => undefined);
        }
        if (cause instanceof ApiError && cause.status === 401) {
          await clearSession();
          router.replace('/login');
          return;
        }
        if (!active) return;
        if (cause instanceof ApiError && (cause.code === 'NOT_AVAILABLE' || cause.status === 404)) {
          setUnavailable(true);
          setDetail(null);
        }
        setError(actionError(cause));
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [domicilioId, loadAttempt]);

  async function confirm() {
    if (!domicilioId || !pendingAction || busy) return;
    const action = pendingAction;
    if (actionKey.current?.action !== action) {
      actionKey.current = { action, key: randomUUID() };
    }
    setBusy(true);
    setError(null);
    try {
      if (action === 'accept') {
        await aceptarDomicilio(domicilioId, actionKey.current.key);
        setNotice('¡Domicilio aceptado! Ve al punto de origen a recoger el pedido.');
        actionKey.current = null;
        setPendingAction(null);
        setLoadAttempt((attempt) => attempt + 1);
      } else {
        await rechazarDomicilio(domicilioId, actionKey.current.key);
        actionKey.current = null;
        router.replace('/domicilios-disponibles');
      }
    } catch (cause) {
      if (cause instanceof ApiError) actionKey.current = null;
      if (cause instanceof ApiError && cause.code === 'NOT_AVAILABLE') {
        setUnavailable(true);
        setPendingAction(null);
      }
      setError(actionError(cause));
    } finally {
      setBusy(false);
    }
  }

  async function pickUp() {
    if (!domicilioId || busy || guardandoRecogida.current || !detail?.asignadoAMi ||
        detail.id !== domicilioId || estado !== 'ASIGNADO' || recogida.pendiente || confirmada) return;
    guardandoRecogida.current = true;
    pickupKey.current ??= randomUUID();
    setBusy(true);
    setError(null);
    try {
      if (await recogida.yaEncolada()) return;
      if (fechaGuardada !== null) {
        await recogida.encolar(pickupKey.current, false);
        pickupKey.current = null;
        setNotice(null);
        return;
      }
      const resultado = await confirmarRecogida(domicilioId, pickupKey.current);
      setRespuestaDirecta(resultado);
      await recogida.registrar(resultado).catch(() => undefined);
      pickupKey.current = null;
      setNotice('Recogida confirmada. Cuando el vendedor confirme tu salida podrás entregar.');
      setLoadAttempt((attempt) => attempt + 1);
    } catch (cause) {
      if (cuenta && fechaGuardada === null && pickupKey.current && confirmacionReintentable(cause)) {
        try {
          await recogida.encolar(pickupKey.current, true);
          pickupKey.current = null;
          setNotice(null);
          return;
        } catch (error) {
          setError(actionError(error));
          return;
        }
      }
      if (cause instanceof ApiError) pickupKey.current = null;
      setError(actionError(cause));
    } finally {
      guardandoRecogida.current = false;
      setBusy(false);
    }
  }

  async function deliver() {
    if (!domicilioId || busy) return;
    if (!/^\d{6}$/.test(codigo)) {
      setError('El código tiene 6 dígitos. Pídeselo al cliente.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      // Una clave por intento: con la misma clave el servidor repetiría la respuesta anterior.
      await confirmarEntrega(domicilioId, codigo, randomUUID());
      if (cuenta) void borrarRuta(domicilioId, cuenta).catch(() => undefined);
      setCodigo('');
      setNotice('¡Entrega confirmada! El vendedor debe pagarte la tarifa.');
      setLoadAttempt((attempt) => attempt + 1);
    } catch (cause) {
      setError(deliveryError(cause));
    } finally {
      setBusy(false);
    }
  }

  const canDecide = enLinea && detail?.estado === 'DISPONIBLE' && !detail.asignadoAMi && !unavailable;
  const mine = Boolean(detail?.asignadoAMi) && !unavailable;
  // RF-26: el cliente ve dónde va el pedido mientras está en camino.
  const compartir = useCompartirUbicacion(detail?.id, enLinea && mine && detail?.estado === 'EN_CAMINO');

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Domiciliario"
        title={detail?.asignadoAMi ? 'Tu domicilio' : 'Información del domicilio'}
        description="Origen, destino, quién recibe y cuánto ganarías."
      />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando domicilio" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {fechaGuardada !== null ? (
        <HelperText>Datos guardados. Última actualización {new Date(fechaGuardada).toLocaleString('es-CO')}.</HelperText>
      ) : null}
      <EstadoConfirmacionDomicilio paso="confirmar-recogida" pendiente={recogida.pendiente} confirmada={confirmada}
        cerrarRevision={enLinea && recogida.pendiente?.status === 'FALLIDA' ? () => {
          void outbox.descartar(recogida.pendiente!.id).catch((cause) => setError(actionError(cause)));
        } : undefined} />
      {domicilioId ? <TextButton icon="refresh-cw" title="Actualizar domicilio" variant="soft" disabled={loading || busy}
        onPress={() => setLoadAttempt((intento) => intento + 1)} /> : null}
      {notice ? <Text aria-live="polite" style={styles.notice}>{notice}</Text> : null}
      {detail && !unavailable ? (
        <>
          <View style={styles.card}>
            <Text style={styles.gain}>Ganancia: {formatPrice(detail.ganancia)}</Text>
            <Text style={styles.detailText}>Estado: {estadoDomicilioNombres[estado ?? detail.estado]}</Text>
            <Text style={styles.detailText}>Distancia de A a B: {formatearDistancia(detail.distanciaMetros)}</Text>
            <Text style={styles.detailText}>
              Pedido de {detail.resumenItems.cantidadItems}{' '}
              {detail.resumenItems.cantidadItems === 1 ? 'producto' : 'productos'}
            </Text>
          </View>
          {enLinea && 'origen' in detail ? <>
          <Punto title="Origen (A)" punto={detail.origen} />
          <Punto title="Destino (B)" punto={detail.destino} />
          <View style={styles.card}>
            <Text style={styles.sectionTitle}>Recibe</Text>
            <Text style={styles.detailText}>{detail.receptor.nombre ?? 'Cliente'}</Text>
            {detail.receptor.celular ? (
              <Text style={styles.detailText}>Celular: {detail.receptor.celular}</Text>
            ) : null}
          </View>
          </> : null}
        </>
      ) : null}
      {canDecide && !pendingAction && !loading ? (
        <>
          <HelperText>Aceptar o rechazar requiere conexión a internet.</HelperText>
          <PrimaryButton title="Aceptar domicilio" onPress={() => setPendingAction('accept')} />
          <View style={{ marginTop: 12 }}>
            <TextButton icon="x-circle" title="Rechazar domicilio" variant="danger" onPress={() => setPendingAction('reject')} />
          </View>
        </>
      ) : null}
      {pendingAction && detail ? (
        <View style={styles.confirmation}>
          <Text style={styles.confirmationText}>
            {pendingAction === 'accept'
              ? `¿Aceptas este domicilio por ${formatPrice(detail.ganancia)}? La tarifa no se puede cambiar.`
              : '¿Rechazar este domicilio? Dejará de aparecer en tu lista y no podrás recuperarlo.'}
          </Text>
          <PrimaryButton
            title={pendingAction === 'accept' ? 'Confirmar aceptación' : 'Confirmar rechazo'}
            busy={busy}
            onPress={() => void confirm()}
          />
          <TextButton variant="danger" icon="x"
            title="Cancelar"
            disabled={busy}
            onPress={() => {
              actionKey.current = null;
              setPendingAction(null);
            }}
          />
        </View>
      ) : null}
      {enLinea && detail && 'origen' in detail && !loading &&
        (canDecide || (mine && ['ASIGNADO', 'RECOGIDO', 'EN_CAMINO'].includes(estado ?? ''))) ? (
        <View style={styles.mapActions}>
          <GoogleMapsButton
            title={mine ? 'Ver ruta en Google Maps' : 'Ver recogida en Google Maps'}
            destino={mine ? detail.destino : detail.origen}
            parada={mine && estado === 'ASIGNADO' ? detail.origen : undefined}
          />
          {mine && detail.rutaDisponible ? (
            <TextButton icon="map-pin" title="Ver detalle de la ruta" variant="soft"
              onPress={() => router.push({ pathname: '/ruta-domicilio', params: { domicilioId: detail.id } })} />
          ) : null}
        </View>
      ) : null}
      {mine && estado === 'ASIGNADO' && !recogida.pendiente && !confirmada && !loading ? (
        <>
          <HelperText>Cuando tengas el pedido en tus manos, confirma la recogida.</HelperText>
          <PrimaryButton title="Confirmar recogida" busy={busy} onPress={() => void pickUp()} />
        </>
      ) : null}
      {mine && estado === 'RECOGIDO' && !loading ? (
        <HelperText>Espera a que el vendedor confirme tu salida para iniciar el trayecto.</HelperText>
      ) : null}
      {enLinea && mine && detail?.estado === 'EN_CAMINO' && !loading ? (
        <View style={styles.card}>
          <Text style={styles.sectionTitle}>Confirmar entrega</Text>
          <Text style={styles.detailText}>Pídele al cliente su código de 6 dígitos.</Text>
          <Field
            label="Código del cliente"
            value={codigo}
            onChangeText={(value) => setCodigo(value.replace(/\D/g, '').slice(0, 6))}
            keyboardType="number-pad"
            maxLength={6}
            autoComplete="off"
          />
          <PrimaryButton title="Confirmar entrega" busy={busy} onPress={() => void deliver()} />
        </View>
      ) : null}
      {enLinea && mine && detail?.estado === 'EN_CAMINO' ? (
        <HelperText>
          {compartir === 'compartiendo'
            ? 'Compartiendo tu ubicación aproximada con el cliente mientras vas en camino.'
            : compartir === 'sin-permiso'
              ? 'Activa el permiso de ubicación para que el cliente vea por dónde vas.'
              : compartir === 'error'
                ? 'No pudimos enviar tu ubicación; lo seguiremos intentando.'
                : 'Preparando el envío de tu ubicación…'}
        </HelperText>
      ) : null}
      {enLinea && mine && detail?.estado === 'ENTREGADO' && !loading ? (
        <>
          <HelperText>Este domicilio ya fue entregado.</HelperText>
          <PagoTarifaDomicilio domicilioId={detail.id} rol="DOMICILIARIO" />
          <PrimaryButton
            title="Calificar al cliente y al emprendimiento"
            onPress={() => router.push({ pathname: '/calificar-pedido', params: { domicilioId: detail.id } })}
          />
        </>
      ) : null}
      {!loading && error && !detail && !unavailable ? (
        <PrimaryButton title="Reintentar carga" onPress={() => setLoadAttempt((attempt) => attempt + 1)} />
      ) : null}
      {mine ? (
        <View style={styles.secondaryActions}>
          {detail ? (
            <TextButton icon="alert-triangle"
              title="Reportar un problema con este domicilio"
              variant="danger"
              onPress={() => router.push({ pathname: '/reportar', params: { domicilioId: detail.id } })}
            />
          ) : null}
          <TextButton title="Mis domicilios en curso" variant="soft" onPress={() => router.replace('/domicilios-en-curso')} />
        </View>
      ) : null}
    </AuthPage>
  );
}

const styles = StyleSheet.create({
  mapActions: { marginVertical: 16, gap: 12 },
  secondaryActions: { alignItems: 'center', marginTop: 24, gap: 14 },
  card: {
    backgroundColor: palette.white,
    borderColor: palette.border,
    borderRadius: 16,
    borderWidth: 1,
    marginVertical: 7,
    padding: 16,
  },
  gain: { color: palette.green, fontFamily: fonts.sans, fontSize: 18, fontWeight: '800' },
  notice: { color: palette.green, fontFamily: fonts.sans, fontSize: 14, fontWeight: '700', marginVertical: 10 },
  detailText: { color: palette.muted, fontFamily: fonts.sans, fontSize: 14, lineHeight: 21, marginTop: 6 },
  sectionTitle: { color: palette.dark, fontFamily: fonts.sans, fontSize: 16, fontWeight: '800' },
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
