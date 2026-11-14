import { useEffect, useRef, useState } from 'react';
import * as Crypto from 'expo-crypto';
import * as Location from 'expo-location';
import { router } from 'expo-router';
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
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const deleteKey = useRef<string | null>(null);
  const ambulante = role === 'VENDEDOR_AMBULANTE';

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
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const session = await getValidSession();
        if (!active) return;
        setRole(session.rol);
        if (session.rol !== 'VENDEDOR_AMBULANTE' && session.rol !== 'VENDEDOR_PUNTO_FIJO') {
          setError('Solo las cuentas de vendedor tienen un emprendimiento.');
          return;
        }
        const response = await obtenerMiEmprendimiento();
        if (active) fill(response);
      } catch (cause) {
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
    if (ubicacionCambio) {
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
    setError(null);
    setNotice(null);
    const input = changes();
    if (typeof input === 'string') {
      setError(input);
      return;
    }
    setBusy(true);
    try {
      fill(await editarMiEmprendimiento(input));
      setNotice('Cambios guardados. Los clientes ya los ven.');
    } catch (cause) {
      setError(errorMessage(cause, 'No se pudieron guardar los cambios.'));
    } finally {
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
    deleteKey.current ??= Crypto.randomUUID();
    setBusy(true);
    try {
      await eliminarMiEmprendimiento(deleteKey.current);
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
            ? `${categoriaNombres[actual.categoria]}. El nombre y la categoría no se pueden cambiar.`
            : 'Administra el negocio que ven los clientes.'
        }
      />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando emprendimiento" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {notice ? <Text accessibilityRole="status" style={styles.notice}>{notice}</Text> : null}
      {!loading && error && !actual && !sinEmprendimiento && role ? (
        <PrimaryButton title="Reintentar" onPress={() => setAttempt((value) => value + 1)} />
      ) : null}
      {sinEmprendimiento ? (
        <>
          <HelperText>Aún no tienes un emprendimiento activo.</HelperText>
          <PrimaryButton title="Crear mi emprendimiento" onPress={() => router.push('/emprendimiento')} />
        </>
      ) : null}
      {actual ? (
        <>
          <Field label="Descripción" value={descripcion} onChangeText={setDescripcion} multiline maxLength={500} />
          <View style={styles.row}>
            <View style={styles.half}>
              <Field label="Abre (HH:mm)" value={apertura} onChangeText={setApertura} maxLength={5} />
            </View>
            <View style={styles.half}>
              <Field label="Cierra (HH:mm)" value={cierre} onChangeText={setCierre} maxLength={5} />
            </View>
          </View>
          <Text style={styles.section}>{ambulante ? 'Zona de venta' : 'Ubicación del punto'}</Text>
          {ambulante ? (
            <HelperText>Cuando cambies de zona durante el día, toca «Estoy aquí» y guarda.</HelperText>
          ) : (
            <HelperText>No podrás moverla mientras tengas pedidos sin cerrar.</HelperText>
          )}
          <TextButton
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
          <PrimaryButton title="Guardar cambios" busy={busy && !confirmDelete} onPress={() => void save()} />
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
                <TextButton title="Cancelar" disabled={busy} onPress={() => setConfirmDelete(false)} />
              </>
            ) : (
              <TextButton title="Eliminar emprendimiento" disabled={busy} onPress={() => setConfirmDelete(true)} />
            )}
          </View>
        </>
      ) : null}
      <TextButton title="Volver a mi cuenta" onPress={() => router.replace('/session')} />
    </AuthPage>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', gap: 10 },
  half: { flex: 1 },
  section: { color: palette.dark, fontSize: 17, fontWeight: '800', marginTop: 14 },
  notice: { color: palette.green, fontSize: 14, fontWeight: '700', marginVertical: 8 },
  danger: {
    backgroundColor: '#FFF6F4',
    borderColor: '#F0D2CB',
    borderRadius: 14,
    borderWidth: 1,
    marginTop: 18,
    padding: 14,
  },
  confirm: { color: palette.dark, fontSize: 15, lineHeight: 22, marginVertical: 8 },
});
