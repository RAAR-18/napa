import { configurarUbicacionAutomatica, leerUbicacionAutomatica, suscribirUbicacionAutomatica } from '../lib/ubicacion-automatica';
import { fonts } from '../components/theme';
import { DateTimeField } from '../components/DateTimeField';
import { useEffect, useRef, useState } from 'react';
import { randomUUID } from '../lib/uuid';
import * as Location from 'expo-location';
import { router } from 'expo-router';
import { ActivityIndicator, Platform, StyleSheet, Text, View } from 'react-native';

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
import { ApiError, NetworkError } from '../lib/api';
import { clearSession, getSessionGeneration, getValidSession, readSession } from '../lib/auth';
import { outbox } from '../database/outbox';
import { useAccionesPendientes } from '../hooks/useOutbox';
import { edicionConBase, edicionEmprendimientoPendiente, RUTA_EDICION_EMPRENDIMIENTO } from '../lib/emprendimiento-pendiente';
import { borrarEmprendimientoPropio, guardarEmprendimientoPropio, leerEmprendimientoPropio } from '../lib/emprendimiento-propio-cache';
import {
  categoriaNombres,
  editarMiEmprendimiento,
  eliminarMiEmprendimiento,
  obtenerMiEmprendimiento,
  type EditarEmprendimientoInput,
  type Emprendimiento,
} from '../lib/emprendimientos';

const HORA = /^(?:[01]\d|2[0-3]):[0-5]\d$/;

function hhmm(value: string) {
  return value.slice(0, 5);
}

function errorMessage(cause: unknown, fallback: string) {
  if (cause instanceof ApiError && cause.code === 'HAS_PENDING_ORDERS') {
    const count = Number(cause.details.pedidosPendientes ?? 0);
    return `${cause.message} Tienes ${count} ${count === 1 ? 'pedido sin cerrar' : 'pedidos sin cerrar'}.`;
  }
  return cause instanceof Error ? cause.message : fallback;
}

/** RF-29: el vendedor edita descripción, horario y ubicación; RF-30: elimina su emprendimiento. */
export default function MiEmprendimientoScreen() {
  const [role, setRole] = useState<string | null>(null);
  const [actual, setActual] = useState<Emprendimiento | null>(null);
  const [sinEmprendimiento, setSinEmprendimiento] = useState(false);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  const [descripcion, setDescripcion] = useState('');
  const [apertura, setApertura] = useState('');
  const [cierre, setCierre] = useState('');
  const [latitud, setLatitud] = useState('');
  const [longitud, setLongitud] = useState('');
  const [direccion, setDireccion] = useState('');
  const [referencia, setReferencia] = useState('');
  const [busy, setBusy] = useState(false);
  const [locating, setLocating] = useState(false);
  const [ubicacionAutomatica, setUbicacionAutomatica] = useState(false);
  const [ubicacionSegundoPlano, setUbicacionSegundoPlano] = useState(false);
  const [configurandoUbicacion, setConfigurandoUbicacion] = useState(false);
  const configuracionEnCurso = useRef(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [fechaCopia, setFechaCopia] = useState<number | null>(null);
  const [revisionId, setRevisionId] = useState<string | null>(null);
  const deleteKey = useRef<string | null>(null);
  const guardando = useRef(false);
  const borradorTocado = useRef(false);
  const sesionForm = useRef<{ cuenta?: string; generacion: number } | null>(null);
  const pendientes = useAccionesPendientes().flatMap((accion) => {
    const cambios = edicionEmprendimientoPendiente(accion);
    return cambios ? [{ accion, cambios }] : [];
  });
  const pendienteActual = pendientes.find(({ cambios }) => cambios.base.emprendimientoId === actual?.id);
  const bloqueado = Boolean(pendienteActual && pendienteActual.accion.id !== revisionId);
  const anteriores = useRef(pendientes.length);

  useEffect(() => {
    const cantidadAnterior = anteriores.current;
    anteriores.current = pendientes.length;
    if (cantidadAnterior > pendientes.length) {
      borradorTocado.current = false;
      const actualizar = () => {
        setNotice(null);
        setAttempt((valor) => valor + 1);
      };
      actualizar();
    }
  }, [pendientes.length]);
  const ambulante = role === 'VENDEDOR_AMBULANTE';

  useEffect(() => {
    let vigente = true;
    if (!ambulante || !actual) return;
    const refrescar = () => { void leerUbicacionAutomatica().then((preferencia) => {
      if (vigente) {
        const coincide = preferencia?.emprendimientoId === actual.id && preferencia.usuarioId === sesionForm.current?.cuenta;
        setUbicacionAutomatica(coincide);
        setUbicacionSegundoPlano(Boolean(coincide && preferencia?.segundoPlano));
      }
    }).catch(() => undefined); };
    refrescar();
    const dejar = suscribirUbicacionAutomatica(refrescar);
    return () => { vigente = false; dejar(); };
  }, [ambulante, actual]);

  async function cambiarUbicacionAutomatica() {
    if (!actual || configuracionEnCurso.current) return;
    configuracionEnCurso.current = true;
    setConfigurandoUbicacion(true);
    setError(null);
    try {
      const session = await getValidSession();
      if (!session.usuarioId) throw new Error('Inicia sesión nuevamente para continuar.');
      await configurarUbicacionAutomatica(session.usuarioId, actual.id, !ubicacionAutomatica);
      setUbicacionAutomatica(!ubicacionAutomatica);
      setUbicacionSegundoPlano(false);
      setNotice(!ubicacionAutomatica
        ? 'Tu zona se actualizará mientras uses Ñapa y tengas conexión.'
        : 'La ubicación automática quedó desactivada.');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'No se pudo cambiar la ubicación automática.');
    } finally { configuracionEnCurso.current = false; setConfigurandoUbicacion(false); }
  }

  async function cambiarUbicacionSegundoPlano() {
    if (!actual || configuracionEnCurso.current) return;
    configuracionEnCurso.current = true;
    setConfigurandoUbicacion(true);
    setError(null);
    try {
      const session = await getValidSession();
      if (!session.usuarioId) throw new Error('Inicia sesión nuevamente para continuar.');
      const activar = !ubicacionSegundoPlano;
      await configurarUbicacionAutomatica(session.usuarioId, actual.id, true, activar);
      setUbicacionAutomatica(true);
      setUbicacionSegundoPlano(activar);
      setNotice(activar ? 'Compartes ubicación también cuando no usas Ñapa. Puedes detenerla aquí cuando quieras.'
        : 'Segundo plano desactivado. Solo se comparte mientras usas Ñapa.');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'No se pudo cambiar el seguimiento.');
    } finally { configuracionEnCurso.current = false; setConfigurandoUbicacion(false); }
  }

  function fill(value: Emprendimiento) {
    setActual(value);
    setDescripcion(value.descripcion);
    setApertura(hhmm(value.horaApertura));
    setCierre(hhmm(value.horaCierre));
    setLatitud(String(value.ubicacion.latitud));
    setLongitud(String(value.ubicacion.longitud));
    setDireccion(value.ubicacion.direccion ?? '');
    setReferencia(value.ubicacion.referencia ?? '');
  }

  useEffect(() => {
    let active = true;
    const generacion = getSessionGeneration();
    const vigente = () => active && generacion === getSessionGeneration();

    async function load() {
      setLoading(true);
      setError(null);
      let cuenta: string | undefined;
      let hayCopia = false;
      try {
        const local = await readSession();
        const vendedorLocal = local?.rol === 'VENDEDOR_AMBULANTE' || local?.rol === 'VENDEDOR_PUNTO_FIJO';
        cuenta = vendedorLocal ? local?.usuarioId : undefined;
        if (cuenta && local) {
          try {
            const copia = await leerEmprendimientoPropio(cuenta);
            if (!vigente()) return;
            if (copia) {
              hayCopia = true;
              sesionForm.current = { cuenta, generacion };
              setRole(local.rol);
              setSinEmprendimiento(false);
              if (!borradorTocado.current) fill(copia.valor);
              setFechaCopia(copia.fecha);
              setLoading(false);
            }
          } catch (cause) {
            console.warn('No se pudo leer el emprendimiento guardado.', cause);
          }
        }
        const session = await getValidSession();
        if (!vigente()) return;
        setRole(session.rol);
        if (session.rol !== 'VENDEDOR_AMBULANTE' && session.rol !== 'VENDEDOR_PUNTO_FIJO') {
          setActual(null);
          setFechaCopia(null);
          setError('Solo las cuentas de vendedor tienen un emprendimiento.');
          return;
        }
        const response = await obtenerMiEmprendimiento();
        if (!vigente()) return;
        if (session.usuarioId) {
          await guardarEmprendimientoPropio(session.usuarioId, response, vigente)
            .catch((cause: unknown) => console.warn('No se pudo guardar el emprendimiento.', cause));
        }
        if (!vigente()) return;
        if (!borradorTocado.current) {
          sesionForm.current = { cuenta: session.usuarioId, generacion };
          setSinEmprendimiento(false);
          fill(response);
          setFechaCopia(null);
        }
      } catch (cause) {
        if (!vigente()) return;
        if (cause instanceof NetworkError && hayCopia) return;
        setActual(null);
        setFechaCopia(null);
        if (cuenta && cause instanceof ApiError && [401, 403, 404].includes(cause.status)) {
          await borrarEmprendimientoPropio(cuenta)
            .catch((error: unknown) => console.warn('No se pudo limpiar el emprendimiento guardado.', error));
        }
        if (!vigente()) return;
        if (cause instanceof ApiError && cause.status === 401) {
          await clearSession();
          router.replace('/login');
          return;
        }
        if (!active) return;
        if (cause instanceof ApiError && cause.status === 404) {
          setSinEmprendimiento(true);
          return;
        }
        setError(cause instanceof Error ? cause.message : 'No se pudo cargar tu emprendimiento.');
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [attempt]);

  /** Solo se envían los campos que cambiaron (RF-29: al menos uno). */
  function changes(): EditarEmprendimientoInput | string {
    if (!actual) return 'No hay emprendimiento para editar.';
    const input: EditarEmprendimientoInput = {};
    if (descripcion.trim() !== actual.descripcion) {
      if (!descripcion.trim() || descripcion.trim().length > 500) {
        return 'La descripción es obligatoria y no debe exceder 500 caracteres.';
      }
      input.descripcion = descripcion.trim();
    }
    if (!HORA.test(apertura.trim()) || !HORA.test(cierre.trim())) return 'Escribe el horario en formato HH:mm.';
    if (apertura.trim() === cierre.trim()) return 'La hora de apertura y la de cierre deben ser diferentes.';
    if (apertura.trim() !== hhmm(actual.horaApertura)) input.horaApertura = apertura.trim();
    if (cierre.trim() !== hhmm(actual.horaCierre)) input.horaCierre = cierre.trim();
    const lat = Number(latitud.trim().replace(',', '.'));
    const lng = Number(longitud.trim().replace(',', '.'));
    const ubicacionCambio =
      lat !== actual.ubicacion.latitud ||
      lng !== actual.ubicacion.longitud ||
      direccion.trim() !== (actual.ubicacion.direccion ?? '') ||
      referencia.trim() !== (actual.ubicacion.referencia ?? '');
    if (ubicacionCambio && !ambulante) {
      if (!Number.isFinite(lat) || lat < -90 || lat > 90 || !Number.isFinite(lng) || lng < -180 || lng > 180) {
        return 'Revisa las coordenadas de la ubicación.';
      }
      if (!ambulante && !direccion.trim()) return 'La dirección es obligatoria para un punto fijo.';
      input.ubicacion = {
        latitud: lat,
        longitud: lng,
        direccion: direccion.trim() || null,
        referencia: referencia.trim() || null,
      };
    }
    return Object.keys(input).length === 0 ? 'No has cambiado ningún dato.' : input;
  }

  async function save() {
    if (!actual || bloqueado || guardando.current || busy) return;
    setError(null);
    setNotice(null);
    const input = changes();
    if (typeof input === 'string') {
      setError(input);
      return;
    }
    guardando.current = true;
    setBusy(true);
    const generacion = getSessionGeneration();
    const vigente = () => generacion === getSessionGeneration();
    try {
      const cuenta = (await readSession())?.usuarioId;
      if (!vigente() || sesionForm.current?.generacion !== generacion || sesionForm.current.cuenta !== cuenta) {
        throw new Error('La cuenta activa cambió. Abre Mi emprendimiento de nuevo.');
      }
      if (input.ubicacion && fechaCopia !== null) {
        throw new Error('Conéctate para cambiar la ubicación.');
      }
      if (cuenta && !input.ubicacion) {
        const envio = {
          body: edicionConBase(input, actual), idempotencyKey: randomUUID(),
          descripcion: `Editar emprendimiento: ${actual.nombre}`,
        };
        if (revisionId && pendienteActual?.accion.id === revisionId) {
          await outbox.corregirFallida(pendienteActual.accion, envio);
        } else {
          await outbox.encolar({ ...envio, usuarioId: cuenta, method: 'PATCH', path: RUTA_EDICION_EMPRENDIMIENTO });
        }
        if (!vigente()) return;
        borradorTocado.current = false;
        setRevisionId(null);
        fill(actual);
        setNotice('Edición pendiente de sincronización.');
        void outbox.procesar().then(({ enviadas }) => {
          if (enviadas > 0 && vigente()) {
            setNotice(null);
            setAttempt((valor) => valor + 1);
          }
        }).catch(() => undefined);
      } else {
        if (fechaCopia !== null) throw new Error('Conéctate para confirmar la cuenta antes de guardar.');
        const guardado = await editarMiEmprendimiento(input);
        if (!vigente()) return;
        if (cuenta) await guardarEmprendimientoPropio(cuenta, guardado, vigente).catch(() => undefined);
        if (!vigente()) return;
        borradorTocado.current = false;
        fill(guardado);
        setNotice('Cambios guardados. Los clientes ya los ven.');
      }
    } catch (cause) {
      if (vigente()) setError(errorMessage(cause, 'No se pudieron guardar los cambios.'));
    } finally {
      guardando.current = false;
      setBusy(false);
    }
  }

  async function revisar(pendiente: (typeof pendientes)[number]) {
    if (busy || guardando.current) return;
    guardando.current = true;
    setBusy(true);
    setError(null);
    const generacion = getSessionGeneration();
    try {
      const cuenta = (await readSession())?.usuarioId;
      if (cuenta !== pendiente.accion.usuarioId || generacion !== getSessionGeneration()) return;
      const vigente = await obtenerMiEmprendimiento();
      if (generacion !== getSessionGeneration()) return;
      sesionForm.current = { cuenta, generacion };
      if (vigente.id !== pendiente.cambios.base.emprendimientoId) {
        fill(vigente);
        setFechaCopia(null);
        throw new Error('El emprendimiento original ya no está activo. Puedes descartar la propuesta.');
      }
      fill(vigente);
      setFechaCopia(null);
      setRevisionId(pendiente.accion.id);
      borradorTocado.current = true;
      setDescripcion(pendiente.cambios.descripcion ?? vigente.descripcion);
      setApertura(hhmm(pendiente.cambios.horaApertura ?? vigente.horaApertura));
      setCierre(hhmm(pendiente.cambios.horaCierre ?? vigente.horaCierre));
    } catch (cause) {
      setError(errorMessage(cause, 'Conéctate para revisar los datos vigentes.'));
    } finally {
      guardando.current = false;
      setBusy(false);
    }
  }

  async function tomarUbicacionActual() {
    setError(null);
    setLocating(true);
    try {
      const permission = await Location.requestForegroundPermissionsAsync();
      if (permission.status !== 'granted') {
        setError('Sin permiso de ubicación. Escribe las coordenadas a mano.');
        return;
      }
      const position = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      setLatitud(position.coords.latitude.toFixed(6));
      setLongitud(position.coords.longitude.toFixed(6));
      setNotice('Tomamos tu ubicación actual. Guarda los cambios para publicarla.');
    } catch {
      setError('No se pudo obtener tu ubicación.');
    } finally {
      setLocating(false);
    }
  }

  async function remove() {
    setError(null);
    deleteKey.current ??= randomUUID();
    setBusy(true);
    try {
      await eliminarMiEmprendimiento(deleteKey.current);
      const cuenta = sesionForm.current?.cuenta;
      if (cuenta) await borrarEmprendimientoPropio(cuenta).catch(() => undefined);
      deleteKey.current = null;
      setConfirmDelete(false);
      setActual(null);
      setSinEmprendimiento(true);
      setNotice('Eliminaste tu emprendimiento. Ya no aparece para los clientes.');
    } catch (cause) {
      if (cause instanceof ApiError) deleteKey.current = null;
      setError(errorMessage(cause, 'No se pudo eliminar el emprendimiento.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Mi emprendimiento"
        title={actual?.nombre ?? 'Mi emprendimiento'}
        description={
          actual
            ? `${actual.categoriaPersonalizada ?? categoriaNombres[actual.categoria]}. El nombre y la categoría no se pueden cambiar.`
            : 'Administra el negocio que ven los clientes.'
        }
      />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando emprendimiento" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {notice ? <Text aria-live="polite" style={styles.notice}>{notice}</Text> : null}
      {!loading && error && !actual && !sinEmprendimiento && role ? (
        <PrimaryButton title="Reintentar" onPress={() => setAttempt((value) => value + 1)} />
      ) : null}
      {sinEmprendimiento ? (
        <>
          <HelperText>Aún no tienes un emprendimiento activo.</HelperText>
          <PrimaryButton title="Crear mi emprendimiento" onPress={() => router.push('/emprendimiento')} />
        </>
      ) : null}
      {fechaCopia !== null ? (
        <HelperText>Datos guardados. Última actualización: {new Date(fechaCopia).toLocaleString('es-CO')}. La ubicación requiere conexión.</HelperText>
      ) : null}
      {pendientes.map((pendiente) => {
        const { accion, cambios } = pendiente;
        const revisarDisponible = accion.status === 'FALLIDA'
          && ![403, 404, 410].includes(accion.errorStatus ?? 0)
          && (!actual || fechaCopia !== null || actual.id === cambios.base.emprendimientoId);
        return (
          <View key={accion.id} style={styles.pending}>
            <Text style={styles.section}>{accion.status === 'FALLIDA' ? 'Requiere revisión' : 'Edición pendiente de sincronización'}</Text>
            <HelperText>{accion.descripcion}</HelperText>
            {cambios.descripcion !== undefined ? <Text>Descripción propuesta: {cambios.descripcion}</Text> : null}
            {cambios.horaApertura !== undefined || cambios.horaCierre !== undefined ? (
              <Text>Horario propuesto: {hhmm(cambios.horaApertura ?? cambios.base.horaApertura)}–{hhmm(cambios.horaCierre ?? cambios.base.horaCierre)}</Text>
            ) : null}
            {accion.status === 'FALLIDA' ? (
              <>
                <HelperText>{accion.lastError ?? 'El servidor rechazó la edición.'}</HelperText>
                {revisarDisponible ? <TextButton title="Revisar propuesta" disabled={busy} onPress={() => void revisar(pendiente)} /> : null}
                <TextButton variant="danger" icon="x-circle" title="Descartar" disabled={busy} onPress={() => {
                  void outbox.descartar(accion.id).then(() => setRevisionId(null)).catch((cause: unknown) => {
                    setError(errorMessage(cause, 'No se pudo descartar la propuesta.'));
                  });
                }} />
              </>
            ) : null}
          </View>
        );
      })}
      {actual ? (
        <>
          {revisionId ? (
            <HelperText>Datos vigentes: {actual.descripcion}. Horario: {hhmm(actual.horaApertura)}–{hhmm(actual.horaCierre)}.</HelperText>
          ) : null}
          <Field label="Descripción" value={descripcion} onChangeText={(valor) => {
            borradorTocado.current = true;
            setDescripcion(valor);
          }} editable={!busy && !bloqueado} multiline maxLength={500} />
          <View style={styles.row}>
            <View style={styles.half}>
              <DateTimeField mode="time" label="Hora de apertura" value={apertura} onChangeText={(valor) => { borradorTocado.current = true; setApertura(valor); }} editable={!busy && !bloqueado} />
            </View>
            <View style={styles.half}>
              <DateTimeField mode="time" label="Hora de cierre" value={cierre} onChangeText={(valor) => { borradorTocado.current = true; setCierre(valor); }} editable={!busy && !bloqueado} />
            </View>
          </View>
          {fechaCopia === null && !revisionId && !bloqueado ? (
          <>
          <Text style={styles.section}>{ambulante ? 'Zona de venta' : 'Ubicación del punto'}</Text>
          {ambulante ? (
            <HelperText>Tu negocio usa tu posición GPS mientras usas Ñapa. No tiene dirección fija; activa la ubicación en vivo para que se mueva contigo.</HelperText>
          ) : (
            <HelperText>No podrás moverla mientras tengas pedidos sin cerrar.</HelperText>
          )}
          {ambulante ? (
            <>
              <TextButton
                title={ubicacionAutomatica ? 'Desactivar ubicación automática' : 'Activar ubicación automática'}
                icon="map-pin"
                disabled={configurandoUbicacion || busy}
                onPress={() => void cambiarUbicacionAutomatica()}
              />
              <HelperText>{ubicacionSegundoPlano
                ? 'Compartes tu ubicación aunque uses otra app o bloquees la pantalla. Desactivar ubicación automática detiene ambos modos.'
                : 'Compartes ubicación solo mientras usas Ñapa. Puedes desactivarla cuando quieras.'}</HelperText>
              {Platform.OS !== 'web' ? (
                <>
                  <HelperText>Para compartir sin tener Ñapa abierta debes permitir ubicación «Siempre». Android mostrará una notificación permanente. El sistema controla la frecuencia y puede detenerla si fuerzas el cierre.</HelperText>
                  <TextButton title={ubicacionSegundoPlano ? 'Desactivar ubicación en segundo plano' : 'Activar ubicación en segundo plano'}
                    icon="map-pin"
                    disabled={configurandoUbicacion || busy} onPress={() => void cambiarUbicacionSegundoPlano()} />
                </>
              ) : <HelperText>En el navegador la ubicación solo funciona con Ñapa abierta.</HelperText>}
            </>
          ) : null}
          {!ambulante ? (
            <>
          <TextButton icon="map-pin"
            title={ambulante ? 'Estoy aquí' : 'Usar mi ubicación actual'}
            disabled={locating || busy}
            onPress={() => void tomarUbicacionActual()}
          />
          <View style={styles.row}>
            <View style={styles.half}>
              <Field label="Latitud" value={latitud} onChangeText={setLatitud} keyboardType="numbers-and-punctuation" />
            </View>
            <View style={styles.half}>
              <Field label="Longitud" value={longitud} onChangeText={setLongitud} keyboardType="numbers-and-punctuation" />
            </View>
          </View>
          <Field
            label={ambulante ? 'Dirección (opcional)' : 'Dirección'}
            value={direccion}
            onChangeText={setDireccion}
            maxLength={300}
          />
          <Field label="Referencia (opcional)" value={referencia} onChangeText={setReferencia} maxLength={300} />
            </>
          ) : null}
          </>
          ) : null}
          <PrimaryButton title={revisionId ? 'Confirmar y reenviar' : 'Guardar cambios'} busy={busy && !confirmDelete}
            disabled={bloqueado} onPress={() => void save()} />
          <TextButton
            title="Ver la reputación de mi emprendimiento"
            variant="gold"
            icon="star"
            onPress={() =>
              router.push({
                pathname: '/calificaciones',
                params: { tipoEntidad: 'EMPRENDIMIENTO', entidadId: actual.id, titulo: actual.nombre },
              })
            }
          />
          {fechaCopia === null && !revisionId && !bloqueado ? (
          <View style={styles.danger}>
            <Text style={styles.section}>Eliminar emprendimiento</Text>
            <HelperText>
              Dejará de aparecer para los clientes junto con sus productos. Solo puedes eliminarlo si no tienes
              pedidos ni reservas sin cerrar.
            </HelperText>
            {confirmDelete ? (
              <>
                <Text style={styles.confirm}>¿Seguro que quieres eliminar {actual.nombre}? No se puede deshacer.</Text>
                <PrimaryButton title="Sí, eliminar" busy={busy} onPress={() => void remove()} />
                <TextButton variant="danger" icon="x" title="Cancelar" disabled={busy} onPress={() => setConfirmDelete(false)} />
              </>
            ) : (
              <TextButton variant="danger" icon="trash-2" title="Eliminar emprendimiento" disabled={busy} onPress={() => setConfirmDelete(true)} />
            )}
          </View>
          ) : null}
        </>
      ) : null}
      {actual ? (
        <TextButton icon="refresh-cw" title="Actualizar datos" disabled={busy || loading || Boolean(revisionId)} onPress={() => {
          borradorTocado.current = false;
          setAttempt((valor) => valor + 1);
        }} />
      ) : null}
    </AuthPage>
  );
}

const styles = StyleSheet.create({
  pending: { marginVertical: 8, padding: 12, borderRadius: 12, borderColor: palette.border, borderWidth: 1 },
  row: { flexDirection: 'row', gap: 10 },
  half: { flex: 1 },
  section: { color: palette.dark, fontFamily: fonts.sans, fontSize: 17, fontWeight: '800', marginTop: 14 },
  notice: { color: palette.green, fontFamily: fonts.sans, fontSize: 14, fontWeight: '700', marginVertical: 8 },
  danger: {
    backgroundColor: '#FFF6F4',
    borderColor: '#F0D2CB',
    borderRadius: 14,
    borderWidth: 1,
    marginTop: 18,
    padding: 14,
  },
  confirm: { color: palette.dark, fontFamily: fonts.sans, fontSize: 15, lineHeight: 22, marginVertical: 8 },
});
