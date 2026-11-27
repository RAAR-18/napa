import { fonts } from '../components/theme';
import { useEffect, useRef, useState } from 'react';
import { randomUUID } from '../lib/uuid';
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
import Estrellas from '../components/Estrellas';
import {
  COMENTARIO_MAXIMO,
  eliminarPorModeracion,
  estadoCalificacionNombres,
  listarCalificaciones,
  moderarTexto,
  type CalificacionSupervisada,
  type EstadoCalificacion,
  type PaginaCalificaciones,
} from '../lib/calificaciones';
import { manejarError, mensajeDeCampo } from '../lib/errores';
import { rolNombres } from '../lib/usuarios';

const ESTADOS: EstadoCalificacion[] = ['ACTIVA', 'ELIMINADA_MODERACION', 'ELIMINADA_AUTOR'];
const entidadNombres = { EMPRENDIMIENTO: 'Emprendimiento', PRODUCTO: 'Producto', USUARIO: 'Usuario' };

function formatDate(value: string) {
  return new Date(value).toLocaleDateString('es-CO', { dateStyle: 'medium' });
}

/** RF-03 (supervisión) y RF-06 (editar el texto o eliminar con un motivo que recibe el autor). */
export default function ModeracionAdminScreen() {
  const [estado, setEstado] = useState<EstadoCalificacion>('ACTIVA');
  const [page, setPage] = useState(0);
  const [pagina, setPagina] = useState<PaginaCalificaciones<CalificacionSupervisada> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [intento, setIntento] = useState(0);

  useEffect(() => {
    async function cargar() {
      setLoading(true);
      setError(null);
      try {
        setPagina(await listarCalificaciones<CalificacionSupervisada>({ estado, page, size: 20 }));
      } catch (cause) {
        setError(await manejarError(cause, 'No se pudieron cargar las calificaciones.'));
      } finally {
        setLoading(false);
      }
    }
    void cargar();
  }, [estado, page, intento]);

  const totalPaginas = pagina ? Math.max(1, Math.ceil(pagina.totalElements / pagina.size)) : 1;

  return (
    <AuthPage>
      <PageHeading eyebrow="Administración" title="Calificaciones y comentarios" description="Supervisa lo que publican los usuarios y modera lo que incumpla las reglas." />
      <View style={styles.filters}>
        {ESTADOS.map((e) => (
          <OptionButton
            key={e}
            label={estadoCalificacionNombres[e]}
            selected={estado === e}
            onPress={() => {
              setEstado(e);
              setPage(0);
            }}
          />
        ))}
      </View>
      {loading ? <ActivityIndicator accessibilityLabel="Cargando calificaciones" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {pagina?.content.map((item) => <TarjetaModeracion key={item.id} item={item} onCambio={() => setIntento((i) => i + 1)} />)}
      {!loading && pagina && pagina.content.length === 0 ? <HelperText>No hay calificaciones con este filtro.</HelperText> : null}
      {pagina && pagina.totalElements > pagina.size ? (
        <View style={styles.pager}>
          <TextButton icon="chevron-left" title="Anterior" disabled={page === 0} onPress={() => setPage((p) => p - 1)} />
          <Text style={styles.detail}>{`${page + 1} de ${totalPaginas}`}</Text>
          <TextButton icon="chevron-right" title="Siguiente" disabled={page + 1 >= totalPaginas} onPress={() => setPage((p) => p + 1)} />
        </View>
      ) : null}
    </AuthPage>
  );
}

function TarjetaModeracion({ item, onCambio }: { item: CalificacionSupervisada; onCambio: () => void }) {
  const [accion, setAccion] = useState<'EDITAR' | 'ELIMINAR' | null>(null);
  const [texto, setTexto] = useState(item.comentario ?? '');
  const [motivo, setMotivo] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const key = useRef<string | null>(null);

  async function aplicar() {
    setBusy(true);
    setError(null);
    try {
      if (accion === 'EDITAR') {
        await moderarTexto(item.id, texto, motivo);
      } else {
        key.current ??= randomUUID();
        await eliminarPorModeracion(item.id, motivo, key.current);
      }
      setAccion(null);
      onCambio();
    } catch (cause) {
      key.current = null;
      setError(mensajeDeCampo(cause) ?? (await manejarError(cause, 'No se pudo moderar el comentario.')));
    } finally {
      setBusy(false);
    }
  }

  const moderable = item.estado === 'ACTIVA' && item.comentario !== null;

  return (
    <View style={styles.card}>
      <Text style={styles.caption}>
        {entidadNombres[item.entidad.tipo]}: {item.entidad.nombre ?? 'Sin nombre'}
      </Text>
      <Estrellas valor={item.puntaje} />
      {item.comentario ? <Text style={styles.body}>{item.comentario}</Text> : <Text style={styles.detail}>Sin comentario.</Text>}
      <Text style={styles.detail}>
        {item.autor.nombre ?? 'Usuario'} ({rolNombres[item.autor.rol ?? ''] ?? item.autor.rol}) · {formatDate(item.createdAt)}
        {item.moderada ? ' · Texto moderado' : ''}
      </Text>
      {moderable && !accion ? (
        <View style={styles.actions}>
          <TextButton icon="edit-2" title="Editar texto" onPress={() => setAccion('EDITAR')} />
          <TextButton variant="danger" icon="trash-2" title="Eliminar" onPress={() => setAccion('ELIMINAR')} />
        </View>
      ) : null}
      {accion ? (
        <>
          {accion === 'EDITAR' ? (
            <Field label="Texto corregido" value={texto} onChangeText={setTexto} multiline maxLength={COMENTARIO_MAXIMO} editable={!busy} />
          ) : (
            <HelperText>La calificación dejará de mostrarse y saldrá del promedio.</HelperText>
          )}
          <Field label="Motivo (se envía al autor)" value={motivo} onChangeText={setMotivo} maxLength={200} editable={!busy} />
          <ErrorBanner message={error} />
          <PrimaryButton
            title={accion === 'EDITAR' ? 'Guardar texto' : 'Eliminar comentario'}
            busy={busy}
            disabled={motivo.trim().length < 5 || (accion === 'EDITAR' && !texto.trim())}
            onPress={() => void aplicar()}
          />
          <TextButton variant="danger" icon="x" title="Cancelar" disabled={busy} onPress={() => setAccion(null)} />
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  filters: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 10 },
  card: {
    backgroundColor: palette.white,
    borderColor: palette.border,
    borderRadius: 16,
    borderWidth: 1,
    marginVertical: 6,
    padding: 14,
  },
  caption: { color: palette.muted, fontFamily: fonts.sans, fontSize: 13, fontWeight: '700' },
  body: { color: palette.dark, fontFamily: fonts.sans, fontSize: 14, lineHeight: 21, marginTop: 4 },
  detail: { color: palette.muted, fontFamily: fonts.sans, fontSize: 13, marginTop: 4 },
  actions: { flexDirection: 'row', gap: 12 },
  pager: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
});
