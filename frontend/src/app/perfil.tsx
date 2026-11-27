import { fonts } from '../components/theme';
import { useEffect, useRef, useState } from 'react';
import { randomUUID } from '../lib/uuid';
import * as Location from 'expo-location';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';

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
import FotoPerfil from '../components/FotoPerfil';
import { Icon } from '../components/NapaUI';
import { ApiError, NetworkError } from '../lib/api';
import { clearSession, getSessionGeneration, readSession } from '../lib/auth';
import { replaceAuthHistory } from '../lib/auth-navigation';
import { manejarError, mensajeDeCampo } from '../lib/errores';
import { borrarPerfilGuardado, guardarPerfil, leerPerfilGuardado } from '../lib/perfil-cache';
import {
  confirmarCambioCelular,
  describirPendientes,
  editarMiPerfil,
  eliminarMiCuenta,
  obtenerMiPerfil,
  rolNombres,
  solicitarCambioCelular,
  type PerfilUsuario,
} from '../lib/usuarios';

/** RF-08: datos de la cuenta (nombre, ubicación y celular con código); RF-11: eliminar la cuenta. */
export default function PerfilScreen() {
  const [perfil, setPerfil] = useState<PerfilUsuario | null>(null);
  const [copia, setCopia] = useState<Awaited<ReturnType<typeof leerPerfilGuardado>>>(null);
  const [intento, setIntento] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);

  useEffect(() => {
    let activa = true;
    const generacion = getSessionGeneration();
    const vigente = () => activa && generacion === getSessionGeneration();

    async function cargar() {
      setLoading(true);
      setError(null);
      setAviso(null);
      setPerfil(null);
      setCopia(null);
      let cuentaId: string | undefined;

      try {
        cuentaId = (await readSession())?.usuarioId;
        if (!vigente()) return;
        const resultado = await obtenerMiPerfil();
        if (!vigente()) return;
        if (cuentaId && resultado.id === cuentaId) {
          await guardarPerfil(resultado, vigente)
            .catch((cause: unknown) => console.warn('No se pudo guardar el perfil.', cause));
        }
        if (vigente()) setPerfil(resultado);
      } catch (cause) {
        if (!vigente()) return;
        if (cuentaId && cause instanceof NetworkError) {
          try {
            const guardado = await leerPerfilGuardado(cuentaId);
            if (!vigente()) return;
            if (guardado) {
              setCopia(guardado);
            } else {
              setError('No hay un perfil guardado vigente. Conéctate para consultar tu perfil.');
            }
            return;
          } catch (cacheError) {
            console.warn('No se pudo leer el perfil guardado.', cacheError);
          }
        }
        if (cuentaId && cause instanceof ApiError && (cause.status === 401 || cause.status === 403)) {
          await borrarPerfilGuardado(cuentaId)
            .catch((error: unknown) => console.warn('No se pudo borrar el perfil guardado.', error));
        }
        if (!vigente()) return;
        const mensaje = await manejarError(cause, 'No se pudo cargar tu perfil.');
        if (activa) setError(mensaje);
      } finally {
        if (activa) setLoading(false);
      }
    }

    void cargar();
    return () => { activa = false; };
  }, [intento]);

  const datosVisibles = perfil ?? copia?.perfil;
  const esAdmin = perfil?.rol === 'ADMINISTRADOR';

  return (
    <AuthPage>
      <PageHeading eyebrow="Mi cuenta" title={datosVisibles?.nombre ?? 'Perfil'} description={datosVisibles ? (rolNombres[datosVisibles.rol] ?? datosVisibles.rol) : ''} />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando perfil" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {copia ? (
        <View style={styles.card}>
          <HelperText>
            Datos guardados. Última descarga: {new Date(copia.fecha).toLocaleString('es-CO')}.
          </HelperText>
          <Text style={styles.detail}>Celular: {copia.perfil.celular ?? 'Sin celular registrado'}</Text>
          <Text style={styles.detail}>Correo: {copia.perfil.correo ?? 'Sin correo registrado'}</Text>
          <Text style={styles.detail}>Dirección: {copia.perfil.direccion ?? 'Sin dirección registrada'}</Text>
        </View>
      ) : null}
      {aviso ? (
        <View accessibilityRole="alert" style={styles.ok}>
          <Text style={styles.okText}>{aviso}</Text>
        </View>
      ) : null}
      {perfil ? (
        <>
          <FotoPerfil
            nombre={perfil.nombre}
            fotoUrl={perfil.fotoUrl}
            onCambiada={(fotoUrl) => {
              setPerfil({ ...perfil, fotoUrl });
              setAviso('Tu foto se actualizó.');
            }}
          />
          <DatosBasicos perfil={perfil} onGuardado={(p) => { setPerfil(p); setAviso('Tus datos se actualizaron.'); }} />
          {!esAdmin ? (
            <CambioCelular perfil={perfil} onCambiado={(p) => { setPerfil(p); setAviso('Tu celular se actualizó.'); }} />
          ) : null}
          {!esAdmin ? <EliminarCuenta cuentaId={perfil.id} /> : null}
        </>
      ) : null}
      {!loading && (copia || error) ? (
        <TextButton icon="refresh-cw" title="Actualizar perfil" onPress={() => setIntento((actual) => actual + 1)} />
      ) : null}
    </AuthPage>
  );
}

function DatosBasicos({ perfil, onGuardado }: { perfil: PerfilUsuario; onGuardado: (p: PerfilUsuario) => void }) {
  const [nombre, setNombre] = useState(perfil.nombre);
  const [direccion, setDireccion] = useState(perfil.ubicacion?.direccion ?? '');
  const [referencia, setReferencia] = useState(perfil.ubicacion?.referencia ?? '');
  const [coordenadas, setCoordenadas] = useState(
    perfil.ubicacion ? { latitud: perfil.ubicacion.latitud, longitud: perfil.ubicacion.longitud } : null,
  );
  const [busy, setBusy] = useState(false);
  const [locating, setLocating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function usarUbicacionActual() {
    setLocating(true);
    setError(null);
    try {
      const permiso = await Location.requestForegroundPermissionsAsync();
      if (permiso.status !== 'granted') {
        setError('Necesitamos permiso de ubicación para tomar tu posición actual.');
        return;
      }
      const posicion = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      setCoordenadas({ latitud: posicion.coords.latitude, longitud: posicion.coords.longitude });
    } catch {
      setError('No pudimos obtener tu ubicación. Inténtalo de nuevo.');
    } finally {
      setLocating(false);
    }
  }

  async function guardar() {
    setBusy(true);
    setError(null);
    try {
      const cambios: Parameters<typeof editarMiPerfil>[0] = {};
      if (nombre.trim() !== perfil.nombre) cambios.nombre = nombre.trim();
      const ubicacionCambio =
        coordenadas &&
        direccion.trim() &&
        (coordenadas.latitud !== perfil.ubicacion?.latitud ||
          coordenadas.longitud !== perfil.ubicacion?.longitud ||
          direccion.trim() !== perfil.ubicacion?.direccion ||
          (referencia.trim() || null) !== (perfil.ubicacion?.referencia ?? null));
      if (ubicacionCambio && coordenadas) {
        cambios.ubicacion = { ...coordenadas, direccion: direccion.trim(), referencia: referencia.trim() || null };
      }
      if (Object.keys(cambios).length === 0) {
        setError('No hay cambios para guardar.');
        return;
      }
      onGuardado(await editarMiPerfil(cambios));
    } catch (cause) {
      setError(mensajeDeCampo(cause) ?? (await manejarError(cause, 'No se pudieron guardar tus datos.')));
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={styles.card}>
      <Text style={styles.section}>Datos de la cuenta</Text>
      <Field label="Nombre" value={nombre} onChangeText={setNombre} maxLength={120} editable={!busy} />
      <Text style={styles.detail}>Celular: {perfil.celular ?? 'Sin celular'}</Text>
      {perfil.correo ? <Text style={styles.detail}>Correo: {perfil.correo}</Text> : null}
      <Text style={[styles.section, styles.spaced]}>Ubicación</Text>
      <Field label="Dirección" value={direccion} onChangeText={setDireccion} maxLength={300} editable={!busy} />
      <Field label="Referencia (opcional)" value={referencia} onChangeText={setReferencia} maxLength={300} editable={!busy} />
      <Text style={styles.detail}>
        {coordenadas
          ? `Punto en el mapa: ${coordenadas.latitud.toFixed(5)}, ${coordenadas.longitud.toFixed(5)}`
          : 'Aún no hay un punto en el mapa.'}
      </Text>
      <TextButton
        title={locating ? 'Obteniendo ubicación…' : 'Usar mi ubicación actual'}
        variant="soft"
        icon="map-pin"
        disabled={locating || busy}
        onPress={() => void usarUbicacionActual()}
      />
      <ErrorBanner message={error} />
      <PrimaryButton title="Guardar datos" busy={busy} onPress={() => void guardar()} />
    </View>
  );
}

function CambioCelular({ perfil, onCambiado }: { perfil: PerfilUsuario; onCambiado: (p: PerfilUsuario) => void }) {
  const [celular, setCelular] = useState('');
  const [codigo, setCodigo] = useState('');
  const [enviado, setEnviado] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const requestKey = useRef<string | null>(null);
  const confirmKey = useRef<string | null>(null);

  async function solicitar() {
    setBusy(true);
    setError(null);
    requestKey.current ??= randomUUID();
    try {
      await solicitarCambioCelular(celular, requestKey.current);
      setEnviado(true);
    } catch (cause) {
      requestKey.current = null;
      setError(
        cause instanceof ApiError && cause.code === 'PHONE_ALREADY_REGISTERED'
          ? 'Ese celular ya está registrado en otra cuenta.'
          : (mensajeDeCampo(cause) ?? (await manejarError(cause, 'No se pudo enviar el código.'))),
      );
    } finally {
      setBusy(false);
    }
  }

  async function confirmar() {
    setBusy(true);
    setError(null);
    confirmKey.current ??= randomUUID();
    try {
      onCambiado(await confirmarCambioCelular(codigo, confirmKey.current));
      setEnviado(false);
      setCelular('');
      setCodigo('');
    } catch (cause) {
      confirmKey.current = null;
      setError(mensajeDeCampo(cause) ?? (await manejarError(cause, 'El código no es válido.')));
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={styles.card}>
      <Text style={styles.section}>Cambiar celular</Text>
      <HelperText>
        Enviaremos un código al número nuevo. Hasta que lo confirmes, seguirá vigente {perfil.celular ?? 'el actual'}.
      </HelperText>
      {!enviado ? (
        <>
          <Field label="Celular nuevo" value={celular} onChangeText={setCelular} keyboardType="phone-pad" maxLength={16} editable={!busy} />
          <ErrorBanner message={error} />
          <PrimaryButton title="Enviar código" busy={busy} disabled={celular.trim().length < 7} onPress={() => void solicitar()} />
        </>
      ) : (
        <>
          <Field label="Código de 6 dígitos" value={codigo} onChangeText={setCodigo} keyboardType="number-pad" maxLength={6} editable={!busy} />
          <ErrorBanner message={error} />
          <PrimaryButton title="Confirmar celular" busy={busy} disabled={codigo.length !== 6} onPress={() => void confirmar()} />
          <TextButton title="Usar otro número" disabled={busy} onPress={() => { setEnviado(false); setCodigo(''); }} />
        </>
      )}
    </View>
  );
}

function EliminarCuenta({ cuentaId }: { cuentaId: string }) {
  const [confirmando, setConfirmando] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pendientes, setPendientes] = useState<string[]>([]);
  const key = useRef<string | null>(null);

  async function eliminar() {
    setBusy(true);
    setError(null);
    setPendientes([]);
    key.current ??= randomUUID();
    try {
      await eliminarMiCuenta(key.current);
      await clearSession();
      await borrarPerfilGuardado(cuentaId)
        .catch((cause: unknown) => console.warn('No se pudo borrar el perfil guardado.', cause));
      replaceAuthHistory('/');
    } catch (cause) {
      key.current = null;
      if (cause instanceof ApiError && cause.code === 'HAS_PENDING_OPERATIONS') {
        setPendientes(describirPendientes(cause.details));
        setError('Resuelve tus operaciones pendientes antes de eliminar la cuenta.');
      } else {
        setError(await manejarError(cause, 'No se pudo eliminar la cuenta.'));
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={[styles.card, styles.danger]}>
      <Text style={styles.section}>Eliminar mi cuenta</Text>
      {!confirmando ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Quiero eliminar mi cuenta"
          onPress={() => setConfirmando(true)}
          style={({ pressed, hovered }: { pressed: boolean; hovered?: boolean }) => [
            styles.deleteButton,
            hovered && { backgroundColor: '#FCE1D9', borderColor: palette.error },
            pressed && { opacity: 0.8, transform: [{ scale: 0.98 }] },
          ]}
        >
          <View style={styles.deleteIcon}><Icon name="trash-2" color={palette.error} size={22} /></View>
          <Text style={styles.deleteLabel}>Quiero eliminar mi cuenta</Text>
          <Icon name="chevron-right" color={palette.error} size={18} />
        </Pressable>
      ) : (
        <>
          <HelperText>
            Se cerrarán todas tus sesiones y no podrás volver a ingresar con esta cuenta. Esta acción no se puede deshacer.
          </HelperText>
          <ErrorBanner message={error} />
          {pendientes.map((p) => (
            <Text key={p} style={styles.detail}>• Tienes {p}</Text>
          ))}
          <PrimaryButton title="Sí, eliminar mi cuenta" busy={busy} onPress={() => void eliminar()} />
          <TextButton variant="danger" icon="x" title="Cancelar" disabled={busy} onPress={() => setConfirmando(false)} />
        </>
      )}
    </View>
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
  danger: { borderColor: '#EBC1B7' },
  deleteButton: {
    flexDirection: 'row', alignItems: 'center', gap: 12,
    backgroundColor: '#FFF0EC', borderColor: '#EBC1B7', borderWidth: 1,
    borderRadius: 14, padding: 12, minHeight: 64, marginTop: 10,
  },
  deleteIcon: {
    width: 38, height: 38, borderRadius: 11, backgroundColor: '#AC352C0D',
    alignItems: 'center', justifyContent: 'center',
  },
  deleteLabel: { flex: 1, fontFamily: fonts.sans, fontSize: 14, lineHeight: 20, fontWeight: '800', color: palette.error },
  section: { color: palette.dark, fontFamily: fonts.sans, fontSize: 16, fontWeight: '800', marginBottom: 6 },
  spaced: { marginTop: 12 },
  detail: { color: palette.muted, fontFamily: fonts.sans, fontSize: 14, lineHeight: 21, marginTop: 4 },
  ok: { backgroundColor: '#EAF4EE', borderRadius: 12, marginBottom: 8, padding: 12 },
  okText: { color: palette.green, fontWeight: '700' },
});
