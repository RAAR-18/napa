import { useEffect, useRef, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import * as Crypto from 'expo-crypto';
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
import { ApiError } from '../lib/api';
import { clearSession, getValidSession } from '../lib/auth';
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
} from '../lib/domicilios';
import { borrarRuta } from '../lib/ruta-cache';
import PagoTarifaDomicilio from '../components/PagoTarifaDomicilio';

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
          {punto.barrio ? `Barrio ${punto.barrio}. ` : ''}Zona aproximada ({punto.latitud.toFixed(2)},{' '}
          {punto.longitud.toFixed(2)}). Verás la dirección exacta al aceptar.
        </Text>
      ) : (
        <>
          <Text style={styles.detailText}>{punto.direccion ?? 'Sin dirección escrita'}</Text>
          {punto.barrio ? <Text style={styles.detailText}>Barrio: {punto.barrio}</Text> : null}
          {punto.referencia ? <Text style={styles.detailText}>Referencia: {punto.referencia}</Text> : null}
          <Text style={styles.detailText}>{punto.latitud}, {punto.longitud}</Text>
        </>
      )}
    </View>
  );
}

export default function DomicilioDomiciliarioScreen() {
  const { domicilioId: rawId } = useLocalSearchParams<{ domicilioId?: string | string[] }>();
  const domicilioId = Array.isArray(rawId) ? rawId[0] : rawId;
  const [detail, setDetail] = useState<DomicilioDomiciliarioDetalle | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [pendingAction, setPendingAction] = useState<Action | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [unavailable, setUnavailable] = useState(false);
  const actionKey = useRef<{ action: Action; key: string } | null>(null);
  const pickupKey = useRef<string | null>(null);
  const [codigo, setCodigo] = useState('');

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setError(null);
      if (!domicilioId) {
        setError('No se indicó el domicilio que quieres consultar.');
        setLoading(false);
        return;
      }
      try {
        const session = await getValidSession();
        if (!active) return;
        if (session.rol !== 'DOMICILIARIO') {
          setError('Solo las cuentas de domiciliario pueden consultar domicilios.');
          return;
        }
        const response = await obtenerDomicilioDomiciliario(domicilioId);
        if (active) setDetail(response);
      } catch (cause) {
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
      actionKey.current = { action, key: Crypto.randomUUID() };
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
    if (!domicilioId || busy) return;
    pickupKey.current ??= Crypto.randomUUID();
    setBusy(true);
    setError(null);
    try {
      await confirmarRecogida(domicilioId, pickupKey.current);
      pickupKey.current = null;
      setNotice('Recogida confirmada. Cuando el vendedor confirme tu salida podrás entregar.');
      setLoadAttempt((attempt) => attempt + 1);
    } catch (cause) {
      if (cause instanceof ApiError) pickupKey.current = null;
      setError(actionError(cause));
    } finally {
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
      await confirmarEntrega(domicilioId, codigo, Crypto.randomUUID());
      void borrarRuta(domicilioId).catch(() => undefined);
      setCodigo('');
      setNotice('¡Entrega confirmada! El vendedor debe pagarte la tarifa.');
      setLoadAttempt((attempt) => attempt + 1);
    } catch (cause) {
      setError(deliveryError(cause));
    } finally {
      setBusy(false);
    }
  }

  const canDecide = detail?.estado === 'DISPONIBLE' && !detail.asignadoAMi && !unavailable;
  const mine = Boolean(detail?.asignadoAMi) && !unavailable;

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Domiciliario"
        title={detail?.asignadoAMi ? 'Tu domicilio' : 'Información del domicilio'}
        description="Origen, destino, quién recibe y cuánto ganarías."
      />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando domicilio" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {notice ? <Text accessibilityRole="status" style={styles.notice}>{notice}</Text> : null}
      {detail && !unavailable ? (
        <>
          <View style={styles.card}>
            <Text style={styles.gain}>Ganancia: {formatPrice(detail.ganancia)}</Text>
            <Text style={styles.detailText}>Estado: {estadoDomicilioNombres[detail.estado]}</Text>
            <Text style={styles.detailText}>Distancia de A a B: {formatearDistancia(detail.distanciaMetros)}</Text>
            <Text style={styles.detailText}>
              Pedido de {detail.resumenItems.cantidadItems}{' '}
              {detail.resumenItems.cantidadItems === 1 ? 'producto' : 'productos'}
            </Text>
          </View>
          <Punto title="Origen (A)" punto={detail.origen} />
          <Punto title="Destino (B)" punto={detail.destino} />
          <View style={styles.card}>
            <Text style={styles.sectionTitle}>Recibe</Text>
            <Text style={styles.detailText}>{detail.receptor.nombre ?? 'Cliente'}</Text>
            {detail.receptor.celular ? (
              <Text style={styles.detailText}>Celular: {detail.receptor.celular}</Text>
            ) : null}
          </View>
        </>
      ) : null}
      {canDecide && !pendingAction && !loading ? (
        <>
          <HelperText>Aceptar o rechazar requiere conexión a internet.</HelperText>
          <PrimaryButton title="Aceptar domicilio" onPress={() => setPendingAction('accept')} />
          <TextButton title="Rechazar domicilio" onPress={() => setPendingAction('reject')} />
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
          <TextButton
            title="Cancelar"
            disabled={busy}
            onPress={() => {
              actionKey.current = null;
              setPendingAction(null);
            }}
          />
        </View>
      ) : null}
      {mine && detail?.rutaDisponible && !loading ? (
        <PrimaryButton
          title="Ver ruta en el mapa"
          onPress={() => router.push({ pathname: '/ruta-domicilio', params: { domicilioId: detail.id } })}
        />
      ) : null}
      {mine && detail?.estado === 'ASIGNADO' && !loading ? (
        <>
          <HelperText>Cuando tengas el pedido en tus manos, confirma la recogida.</HelperText>
          <PrimaryButton title="Confirmar recogida" busy={busy} onPress={() => void pickUp()} />
        </>
      ) : null}
      {mine && detail?.estado === 'RECOGIDO' && !loading ? (
        <HelperText>Espera a que el vendedor confirme tu salida para iniciar el trayecto.</HelperText>
      ) : null}
      {mine && detail?.estado === 'EN_CAMINO' && !loading ? (
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
      {mine && detail?.estado === 'ENTREGADO' && !loading ? (
        <>
          <HelperText>Este domicilio ya fue entregado.</HelperText>
          <PagoTarifaDomicilio domicilioId={detail.id} rol="DOMICILIARIO" />
        </>
      ) : null}
      {!loading && error && !detail && !unavailable ? (
        <PrimaryButton title="Reintentar carga" onPress={() => setLoadAttempt((attempt) => attempt + 1)} />
      ) : null}
      {mine ? (
        <TextButton title="Mis domicilios en curso" onPress={() => router.replace('/domicilios-en-curso')} />
      ) : null}
      <TextButton title="Volver a disponibles" onPress={() => router.replace('/domicilios-disponibles')} />
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
  gain: { color: palette.green, fontSize: 18, fontWeight: '800' },
  notice: { color: palette.green, fontSize: 14, fontWeight: '700', marginVertical: 10 },
  detailText: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 6 },
  sectionTitle: { color: palette.dark, fontSize: 16, fontWeight: '800' },
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
