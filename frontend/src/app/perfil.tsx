import { useEffect, useRef, useState } from 'react';
import * as Crypto from 'expo-crypto';
import * as Location from 'expo-location';
import { router } from 'expo-router';
import { ActivityIndicator, Image, StyleSheet, Text, View } from 'react-native';

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
import { clearSession } from '../lib/auth';
import { manejarError, mensajeDeCampo } from '../lib/errores';
import {
  confirmarCambioCelular,
  describirPendientes,
  editarMiPerfil,
  eliminarMiCuenta,
  obtenerMiPerfil,
  rolNombres,
  solicitarCambioCelular,
  urlFoto,
  type PerfilUsuario,
} from '../lib/usuarios';

/** RF-08: datos de la cuenta (nombre, ubicación y celular con código); RF-11: eliminar la cuenta. */
export default function PerfilScreen() {
  const [perfil, setPerfil] = useState<PerfilUsuario | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);

  useEffect(() => {
    async function cargar() {
      setLoading(true);
      setError(null);
      try {
        setPerfil(await obtenerMiPerfil());
      } catch (cause) {
        setError(await manejarError(cause, 'No se pudo cargar tu perfil.'));
      } finally {
        setLoading(false);
      }
    }
    void cargar();
  }, []);

  const foto = urlFoto(perfil?.fotoUrl ?? null);
  const esAdmin = perfil?.rol === 'ADMINISTRADOR';

  return (
    <AuthPage>
      <PageHeading eyebrow="Mi cuenta" title={perfil?.nombre ?? 'Perfil'} description={perfil ? (rolNombres[perfil.rol] ?? perfil.rol) : ''} />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando perfil" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {aviso ? (
        <View accessibilityRole="alert" style={styles.ok}>
          <Text style={styles.okText}>{aviso}</Text>
        </View>
      ) : null}
      {perfil ? (
        <>
          {foto ? <Image accessibilityLabel="Foto de perfil" source={{ uri: foto }} style={styles.photo} /> : null}
          <DatosBasicos perfil={perfil} onGuardado={(p) => { setPerfil(p); setAviso('Tus datos se actualizaron.'); }} />
          {!esAdmin ? (
            <CambioCelular perfil={perfil} onCambiado={(p) => { setPerfil(p); setAviso('Tu celular se actualizó.'); }} />
          ) : null}
          {!esAdmin ? <EliminarCuenta /> : null}
        </>
      ) : null}
      <TextButton title="Volver" onPress={() => router.back()} />
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
    requestKey.current ??= Crypto.randomUUID();
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
    confirmKey.current ??= Crypto.randomUUID();
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

function EliminarCuenta() {
  const [confirmando, setConfirmando] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pendientes, setPendientes] = useState<string[]>([]);
  const key = useRef<string | null>(null);

  async function eliminar() {
    setBusy(true);
    setError(null);
    setPendientes([]);
    key.current ??= Crypto.randomUUID();
    try {
      await eliminarMiCuenta(key.current);
      await clearSession();
      router.replace('/');
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
        <TextButton title="Quiero eliminar mi cuenta" onPress={() => setConfirmando(true)} />
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
          <TextButton title="Cancelar" disabled={busy} onPress={() => setConfirmando(false)} />
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
  danger: { borderColor: palette.error },
  section: { color: palette.dark, fontSize: 16, fontWeight: '800', marginBottom: 6 },
  spaced: { marginTop: 12 },
  detail: { color: palette.muted, fontSize: 14, lineHeight: 21, marginTop: 4 },
  photo: { alignSelf: 'center', borderRadius: 48, height: 96, marginBottom: 8, width: 96 },
  ok: { backgroundColor: '#EAF4EE', borderRadius: 12, marginBottom: 8, padding: 12 },
  okText: { color: palette.green, fontWeight: '700' },
});
