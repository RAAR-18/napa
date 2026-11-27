import { fonts } from '../components/theme';
import { useEffect, useRef, useState } from 'react';
import { randomUUID } from '../lib/uuid';
import { useLocalSearchParams } from 'expo-router';
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
import Estrellas from '../components/Estrellas';
import { ApiError } from '../lib/api';
import {
  COMENTARIO_MAXIMO,
  editarCalificacion,
  eliminarCalificacion,
  listarCalificaciones,
  textoPromedio,
  type CalificacionPublica,
  type PaginaCalificaciones,
  type TipoEntidadCalificada,
} from '../lib/calificaciones';
import { manejarError, mensajeDeCampo } from '../lib/errores';

const TAMANO = 20;
const TIPOS: TipoEntidadCalificada[] = ['EMPRENDIMIENTO', 'PRODUCTO', 'USUARIO'];

function param(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

function formatDate(value: string) {
  return new Date(value).toLocaleDateString('es-CO', { dateStyle: 'medium' });
}

/** RF-02: promedio y comentarios de un emprendimiento, producto o usuario; RF-04 y RF-05 para los propios. */
export default function CalificacionesScreen() {
  const params = useLocalSearchParams<{ tipoEntidad?: string; entidadId?: string; titulo?: string }>();
  const tipo = param(params.tipoEntidad) as TipoEntidadCalificada | undefined;
  const entidadId = param(params.entidadId);
  const titulo = param(params.titulo);
  const [pagina, setPagina] = useState<PaginaCalificaciones<CalificacionPublica> | null>(null);
  const [items, setItems] = useState<CalificacionPublica[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [page, setPage] = useState(0);
  const [intento, setIntento] = useState(0);

  useEffect(() => {
    async function cargar() {
      setLoading(true);
      setError(null);
      if (!tipo || !TIPOS.includes(tipo) || !entidadId) {
        setError('No se indicó qué calificaciones quieres ver.');
        setLoading(false);
        return;
      }
      try {
        const respuesta = await listarCalificaciones({ tipoEntidad: tipo, entidadId, page, size: TAMANO });
        setPagina(respuesta);
        setItems((actuales) => (page === 0 ? respuesta.content : [...actuales, ...respuesta.content]));
      } catch (cause) {
        setError(
          cause instanceof ApiError && cause.status === 403
            ? 'No tienes acceso a estas calificaciones.'
            : cause instanceof ApiError && cause.status === 404
              ? 'No encontramos esa entidad.'
              : await manejarError(cause, 'No se pudieron cargar las calificaciones.'),
        );
      } finally {
        setLoading(false);
      }
    }
    void cargar();
  }, [tipo, entidadId, page, intento]);

  function recargar() {
    setPage(0);
    setIntento((i) => i + 1);
  }

  const hayMas = pagina ? (pagina.page + 1) * pagina.size < pagina.totalElements : false;

  return (
    <AuthPage>
      <PageHeading eyebrow="Reputación" title={titulo ?? 'Calificaciones'} description={textoPromedio(pagina?.resumen)} />
      {pagina?.resumen?.promedio ? (
        <View style={styles.summary}>
          <Estrellas valor={Math.round(pagina.resumen.promedio)} />
        </View>
      ) : null}
      <ErrorBanner message={error} />
      {items.map((item) => (
        <TarjetaCalificacion
          key={item.id}
          item={item}
          onCambio={recargar}
        />
      ))}
      {!loading && pagina && items.length === 0 ? <HelperText>Aún sin calificaciones.</HelperText> : null}
      {loading ? <ActivityIndicator accessibilityLabel="Cargando calificaciones" color={palette.green} /> : null}
      {hayMas && !loading ? (
        <TextButton title="Ver más" onPress={() => setPage((p) => p + 1)} />
      ) : null}
    </AuthPage>
  );
}

function TarjetaCalificacion({ item, onCambio }: { item: CalificacionPublica; onCambio: () => void }) {
  const [editando, setEditando] = useState(false);
  const [puntaje, setPuntaje] = useState(item.puntaje);
  const [comentario, setComentario] = useState(item.comentario ?? '');
  const [confirmarBorrado, setConfirmarBorrado] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const deleteKey = useRef<string | null>(null);

  async function guardar() {
    setBusy(true);
    setError(null);
    try {
      const cambios: { puntaje?: number; comentario?: string } = {};
      if (puntaje !== item.puntaje) cambios.puntaje = puntaje;
      if (comentario.trim() && comentario.trim() !== (item.comentario ?? '')) cambios.comentario = comentario.trim();
      if (Object.keys(cambios).length > 0) await editarCalificacion(item.id, cambios);
      setEditando(false);
      onCambio();
    } catch (cause) {
      setError(mensajeDeCampo(cause) ?? (await manejarError(cause, 'No se pudo guardar el cambio.')));
    } finally {
      setBusy(false);
    }
  }

  async function borrar() {
    setBusy(true);
    setError(null);
    deleteKey.current ??= randomUUID();
    try {
      await eliminarCalificacion(item.id, deleteKey.current);
      onCambio();
    } catch (cause) {
      setError(await manejarError(cause, 'No se pudo eliminar la calificación.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={[styles.card, item.esMia && styles.mine]}>
      <View style={styles.header}>
        <Text style={styles.author}>{item.esMia ? 'Tu calificación' : (item.autorNombre ?? 'Usuario')}</Text>
        <Text style={styles.date}>{formatDate(item.createdAt)}</Text>
      </View>
      {editando ? (
        <>
          <Estrellas valor={puntaje} onChange={setPuntaje} />
          <Field
            label="Comentario"
            value={comentario}
            onChangeText={setComentario}
            multiline
            maxLength={COMENTARIO_MAXIMO}
            editable={!busy}
          />
          <ErrorBanner message={error} />
          <PrimaryButton title="Guardar cambios" busy={busy} onPress={() => void guardar()} />
          <TextButton variant="danger" icon="x" title="Cancelar" disabled={busy} onPress={() => setEditando(false)} />
        </>
      ) : (
        <>
          <Estrellas valor={item.puntaje} />
          {item.comentario ? <Text style={styles.comment}>{item.comentario}</Text> : null}
          {item.esMia ? (
            <>
              <ErrorBanner message={error} />
              {confirmarBorrado ? (
                <>
                  <HelperText>¿Eliminar tu calificación? Dejará de contar en el promedio y no podrás recuperarla.</HelperText>
                  <PrimaryButton title="Sí, eliminar" busy={busy} onPress={() => void borrar()} />
                  <TextButton title="No" disabled={busy} onPress={() => setConfirmarBorrado(false)} />
                </>
              ) : (
                <View style={styles.actions}>
                  <TextButton icon="edit-2" title="Editar" onPress={() => setEditando(true)} />
                  <TextButton variant="danger" icon="trash-2" title="Eliminar" onPress={() => setConfirmarBorrado(true)} />
                </View>
              )}
            </>
          ) : null}
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  summary: { alignItems: 'flex-start', marginBottom: 8 },
  card: {
    backgroundColor: palette.white,
    borderColor: palette.border,
    borderRadius: 16,
    borderWidth: 1,
    marginVertical: 6,
    padding: 14,
  },
  mine: { borderColor: palette.green },
  header: { flexDirection: 'row', justifyContent: 'space-between' },
  author: { color: palette.dark, fontFamily: fonts.sans, fontSize: 15, fontWeight: '700' },
  date: { color: palette.muted, fontFamily: fonts.sans, fontSize: 13 },
  comment: { color: palette.dark, fontFamily: fonts.sans, fontSize: 14, lineHeight: 21, marginTop: 6 },
  actions: { flexDirection: 'row', gap: 12 },
});
