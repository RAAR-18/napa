import { fonts } from '../components/theme';
import { useEffect, useRef, useState } from 'react';
import { randomUUID } from '../lib/uuid';
import { router, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  Field,
  HelperText,
  OptionButton,
  PageHeading,
  PrimaryButton,
  TextButton,
  palette,
} from '../components/AuthUI';
import { ApiError } from '../lib/api';
import { manejarError, mensajeDeCampo } from '../lib/errores';
import {
  describirPendientes,
  editarUsuario,
  eliminarUsuario,
  estadoCuentaNombres,
  obtenerUsuario,
  rolNombres,
  type PerfilUsuario,
} from '../lib/usuarios';

const ROLES_ASIGNABLES = ['CLIENTE', 'VENDEDOR_AMBULANTE', 'VENDEDOR_PUNTO_FIJO', 'DOMICILIARIO'];

function param(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

/** RF-66 (detalle), RF-67 (editar) y RF-68 (eliminar con advertencia). */
export default function UsuarioAdminScreen() {
  const usuarioId = param(useLocalSearchParams<{ usuarioId?: string }>().usuarioId);
  const [usuario, setUsuario] = useState<PerfilUsuario | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);

  const [intento, setIntento] = useState(0);

  useEffect(() => {
    async function cargar() {
      setLoading(true);
      setError(null);
      if (!usuarioId) {
        setError('No se indicó el usuario.');
        setLoading(false);
        return;
      }
      try {
        setUsuario(await obtenerUsuario(usuarioId));
      } catch (cause) {
        setError(
          cause instanceof ApiError && cause.status === 404
            ? 'El usuario no existe.'
            : await manejarError(cause, 'No se pudo cargar el usuario.'),
        );
      } finally {
        setLoading(false);
      }
    }
    void cargar();
  }, [usuarioId, intento]);

  const editable = usuario && usuario.rol !== 'ADMINISTRADOR' && usuario.estado === 'ACTIVA';

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Usuario"
        title={usuario?.nombre ?? 'Usuario'}
        description={usuario ? `${rolNombres[usuario.rol] ?? usuario.rol} · ${estadoCuentaNombres[usuario.estado] ?? usuario.estado}` : ''}
      />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando usuario" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {aviso ? <HelperText>{aviso}</HelperText> : null}
      {usuario ? (
        <>
          <View style={styles.card}>
            <Text style={styles.detail}>Celular: {usuario.celular ?? '—'} {usuario.celularVerificado ? '(verificado)' : ''}</Text>
            <Text style={styles.detail}>Correo: {usuario.correo ?? '—'}</Text>
            <Text style={styles.detail}>Ubicación: {usuario.ubicacion?.direccion ?? '—'}</Text>
            <Text style={styles.detail}>Registrado: {new Date(usuario.createdAt).toLocaleDateString('es-CO', { dateStyle: 'medium' })}</Text>
          </View>
          {usuario.rol === 'CLIENTE' || usuario.rol === 'DOMICILIARIO' ? (
            <TextButton variant="gold" icon="star"
              title="Ver sus calificaciones"
              onPress={() =>
                router.push({
                  pathname: '/calificaciones',
                  params: { tipoEntidad: 'USUARIO', entidadId: usuario.id, titulo: usuario.nombre },
                })
              }
            />
          ) : null}
          {editable ? (
            <EditarUsuario
              usuario={usuario}
              onGuardado={(u, cambioRol) => {
                setUsuario(u);
                setAviso(cambioRol ? 'Datos guardados. Se cerraron sus sesiones para aplicar el rol nuevo.' : 'Datos guardados.');
              }}
            />
          ) : null}
          {usuario.rol !== 'ADMINISTRADOR' && usuario.estado !== 'ELIMINADA' ? (
            <EliminarUsuario usuario={usuario} onEliminado={() => setIntento((i) => i + 1)} />
          ) : null}
        </>
      ) : null}
    </AuthPage>
  );
}

function EditarUsuario({
  usuario,
  onGuardado,
}: {
  usuario: PerfilUsuario;
  onGuardado: (u: PerfilUsuario, cambioRol: boolean) => void;
}) {
  const [nombre, setNombre] = useState(usuario.nombre);
  const [celular, setCelular] = useState(usuario.celular ?? '');
  const [correo, setCorreo] = useState(usuario.correo ?? '');
  const [rol, setRol] = useState(usuario.rol);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function guardar() {
    const cambios: Parameters<typeof editarUsuario>[1] = {};
    if (nombre.trim() !== usuario.nombre) cambios.nombre = nombre.trim();
    if (celular.trim() && celular.trim() !== usuario.celular) cambios.celular = celular.trim();
    if (correo.trim() && correo.trim() !== (usuario.correo ?? '')) cambios.correo = correo.trim();
    if (rol !== usuario.rol) cambios.rol = rol;
    if (Object.keys(cambios).length === 0) {
      setError('No hay cambios para guardar.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      onGuardado(await editarUsuario(usuario.id, cambios), cambios.rol !== undefined);
    } catch (cause) {
      if (cause instanceof ApiError && cause.code === 'HAS_PENDING_OPERATIONS') {
        setError(`No se puede cambiar el rol: tiene ${describirPendientes(cause.details).join(', ')}.`);
      } else if (cause instanceof ApiError && cause.code === 'PHONE_ALREADY_REGISTERED') {
        setError('Ese celular ya pertenece a otra cuenta.');
      } else if (cause instanceof ApiError && cause.code === 'EMAIL_ALREADY_REGISTERED') {
        setError('Ese correo ya pertenece a otra cuenta.');
      } else {
        setError(mensajeDeCampo(cause) ?? (await manejarError(cause, 'No se pudieron guardar los cambios.')));
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={styles.card}>
      <Text style={styles.section}>Editar datos</Text>
      <Field label="Nombre" value={nombre} onChangeText={setNombre} maxLength={120} editable={!busy} />
      <Field label="Celular" value={celular} onChangeText={setCelular} keyboardType="phone-pad" maxLength={16} editable={!busy} />
      <Field label="Correo" value={correo} onChangeText={setCorreo} keyboardType="email-address" autoCapitalize="none" maxLength={254} editable={!busy} />
      <Text style={styles.label}>Rol</Text>
      <View style={styles.options}>
        {ROLES_ASIGNABLES.map((r) => (
          <OptionButton key={r} label={rolNombres[r]} selected={rol === r} onPress={() => setRol(r)} />
        ))}
      </View>
      {rol !== usuario.rol ? (
        <HelperText>Al cambiar el rol se cerrarán sus sesiones. Solo se permite si no tiene operaciones pendientes.</HelperText>
      ) : null}
      <ErrorBanner message={error} />
      <PrimaryButton title="Guardar cambios" busy={busy} onPress={() => void guardar()} />
    </View>
  );
}

function EliminarUsuario({ usuario, onEliminado }: { usuario: PerfilUsuario; onEliminado: () => void }) {
  const [confirmando, setConfirmando] = useState(false);
  const [pendientes, setPendientes] = useState<string[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const key = useRef<string | null>(null);

  async function eliminar(forzar: boolean) {
    setBusy(true);
    setError(null);
    key.current = randomUUID();
    try {
      await eliminarUsuario(usuario.id, forzar, key.current);
      onEliminado();
    } catch (cause) {
      if (cause instanceof ApiError && cause.code === 'HAS_PENDING_OPERATIONS') {
        setPendientes(describirPendientes(cause.details));
      } else {
        setError(await manejarError(cause, 'No se pudo eliminar el usuario.'));
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={[styles.card, styles.danger]}>
      <Text style={styles.section}>Eliminar cuenta</Text>
      {!confirmando ? (
        <TextButton variant="danger" icon="trash-2" title="Eliminar esta cuenta" onPress={() => setConfirmando(true)} />
      ) : pendientes ? (
        <>
          <HelperText>Este usuario tiene actividad en curso:</HelperText>
          {pendientes.map((p) => (
            <Text key={p} style={styles.detail}>• {p}</Text>
          ))}
          <HelperText>Si continúas, su emprendimiento activo también se eliminará.</HelperText>
          <ErrorBanner message={error} />
          <PrimaryButton title="Eliminar de todas formas" busy={busy} onPress={() => void eliminar(true)} />
          <TextButton variant="danger" icon="x" title="Cancelar" disabled={busy} onPress={() => { setConfirmando(false); setPendientes(null); }} />
        </>
      ) : (
        <>
          <HelperText>Se cerrarán sus sesiones y no podrá volver a ingresar. No se puede deshacer.</HelperText>
          <ErrorBanner message={error} />
          <PrimaryButton title="Sí, eliminar" busy={busy} onPress={() => void eliminar(false)} />
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
  danger: { borderColor: palette.error },
  section: { color: palette.dark, fontFamily: fonts.sans, fontSize: 16, fontWeight: '800', marginBottom: 6 },
  label: { color: palette.dark, fontFamily: fonts.sans, fontSize: 14, fontWeight: '700', marginTop: 6 },
  options: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginVertical: 8 },
  detail: { color: palette.muted, fontFamily: fonts.sans, fontSize: 14, lineHeight: 21, marginTop: 4 },
});
