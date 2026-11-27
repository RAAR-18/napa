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
  palette,
} from '../components/AuthUI';
import Estrellas from '../components/Estrellas';
import { outbox } from '../database/outbox';
import { ApiError, NetworkError } from '../lib/api';
import {
  COMENTARIO_MAXIMO,
  calificar,
  cuerpoCalificacion,
  obtenerOpcionesCalificacion,
  type OpcionCalificacion,
  type OpcionesCalificacion,
} from '../lib/calificaciones';
import { manejarError, mensajeDeCampo } from '../lib/errores';

function param(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

function etiqueta(opcion: OpcionCalificacion) {
  if (opcion.tipoEntidad === 'EMPRENDIMIENTO') return 'Emprendimiento';
  if (opcion.tipoEntidad === 'PRODUCTO') return 'Producto';
  return opcion.rol === 'DOMICILIARIO' ? 'Domiciliario' : 'Cliente';
}

function clave(opcion: OpcionCalificacion) {
  return `${opcion.tipoEntidad}:${opcion.entidadId}`;
}

/** RF-01: califica a las partes de un pedido entregado; cada destino se califica una sola vez. */
export default function CalificarPedidoScreen() {
  const params = useLocalSearchParams<{ pedidoId?: string; domicilioId?: string }>();
  const pedidoId = param(params.pedidoId);
  const domicilioId = param(params.domicilioId);
  const [data, setData] = useState<OpcionesCalificacion | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function cargar() {
      setLoading(true);
      setError(null);
      if (!pedidoId && !domicilioId) {
        setError('No se indicó el pedido que quieres calificar.');
        setLoading(false);
        return;
      }
      try {
        setData(await obtenerOpcionesCalificacion(pedidoId ? { pedidoId } : { domicilioId }));
      } catch (cause) {
        setError(
          cause instanceof ApiError && cause.status === 404
            ? 'No encontramos este pedido entre los tuyos.'
            : await manejarError(cause, 'No se pudieron cargar las opciones para calificar.'),
        );
      } finally {
        setLoading(false);
      }
    }
    void cargar();
  }, [pedidoId, domicilioId]);

  const PENDIENTE_ENVIO = 'pendiente-envio';

  const pendientes = data?.opciones.filter((o) => !o.calificacionId) ?? [];
  const hechas = data?.opciones.filter((o) => o.calificacionId) ?? [];

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Calificar"
        title="¿Cómo te fue?"
        description="Tu calificación ayuda a otros usuarios a decidir. Puedes calificar cada parte una sola vez."
      />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {data && data.estadoPedido !== 'ENTREGADO' ? (
        <HelperText>Podrás calificar cuando el pedido esté entregado.</HelperText>
      ) : null}
      {data
        ? pendientes.map((opcion) => (
            <FormularioCalificacion
              key={clave(opcion)}
              opcion={opcion}
              pedidoId={data.pedidoId}
              onCalificado={(id) =>
                setData((actual) =>
                  actual
                    ? {
                        ...actual,
                        opciones: actual.opciones.map((o) =>
                          clave(o) === clave(opcion) ? { ...o, calificacionId: id } : o,
                        ),
                      }
                    : actual,
                )
              }
            />
          ))
        : null}
      {hechas.length > 0 ? (
        <View style={styles.card}>
          <Text style={styles.title}>Ya calificaste</Text>
          {hechas.map((opcion) => (
            <Text key={clave(opcion)} style={styles.detail}>
              {etiqueta(opcion)}: {opcion.nombre ?? 'Sin nombre'}{' '}
              {opcion.calificacionId === PENDIENTE_ENVIO ? '(pendiente de envío)' : '✓'}
            </Text>
          ))}
          <HelperText>Puedes editar o eliminar tus calificaciones desde el perfil de cada uno.</HelperText>
        </View>
      ) : null}
      {data && data.estadoPedido === 'ENTREGADO' && data.opciones.length > 0 && pendientes.length === 0 ? (
        <HelperText>¡Gracias! Ya calificaste todo en este pedido.</HelperText>
      ) : null}
    </AuthPage>
  );
}

function FormularioCalificacion({
  opcion,
  pedidoId,
  onCalificado,
}: {
  opcion: OpcionCalificacion;
  pedidoId: string;
  onCalificado: (calificacionId: string) => void;
}) {
  const [puntaje, setPuntaje] = useState(0);
  const [comentario, setComentario] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // La misma clave en los reintentos: el servidor no duplica la calificación.
  const key = useRef<string | null>(null);

  async function enviar() {
    if (puntaje < 1) {
      setError('Elige de 1 a 5 estrellas.');
      return;
    }
    setBusy(true);
    setError(null);
    key.current ??= randomUUID();
    const datos = { pedidoId, tipoEntidad: opcion.tipoEntidad, entidadId: opcion.entidadId, puntaje, comentario };
    let usuarioId: string | null = null;
    try {
      usuarioId = await outbox.propietario();
      const creada = await calificar(datos, key.current);
      onCalificado(creada.id);
    } catch (cause) {
      if (cause instanceof NetworkError && usuarioId) {
        // F041: se guarda con la misma clave y se envía sola cuando vuelva la conexión.
        try {
          await outbox.encolar({
            usuarioId,
            method: 'POST',
            path: '/api/v1/calificaciones',
            body: cuerpoCalificacion(datos),
            idempotencyKey: key.current,
            descripcion: `Calificación de ${opcion.nombre ?? etiqueta(opcion)}`,
          });
          onCalificado('pendiente-envio');
        } catch {
          setError('No se pudo guardar la acción en el teléfono. Conserva el formulario e intenta de nuevo.');
        }
        return;
      }
      if (cause instanceof ApiError && cause.code === 'CONFLICT') {
        onCalificado('ya-calificado');
        return;
      }
      key.current = null;
      setError(mensajeDeCampo(cause) ?? (await manejarError(cause, 'No se pudo guardar tu calificación.')));
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={styles.card}>
      <Text style={styles.caption}>{etiqueta(opcion)}</Text>
      <Text style={styles.title}>{opcion.nombre ?? 'Sin nombre'}</Text>
      <Estrellas valor={puntaje} onChange={setPuntaje} etiqueta={`Puntaje para ${opcion.nombre ?? etiqueta(opcion)}`} />
      <Field
        label="Comentario (opcional)"
        value={comentario}
        onChangeText={setComentario}
        multiline
        maxLength={COMENTARIO_MAXIMO}
        editable={!busy}
      />
      <HelperText>{`${comentario.length}/${COMENTARIO_MAXIMO}`}</HelperText>
      <ErrorBanner message={error} />
      <PrimaryButton title="Enviar calificación" busy={busy} disabled={puntaje < 1} onPress={() => void enviar()} />
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
  caption: { color: palette.muted, fontFamily: fonts.sans, fontSize: 13, fontWeight: '700' },
  title: { color: palette.dark, fontFamily: fonts.sans, fontSize: 17, fontWeight: '800', marginTop: 2 },
  detail: { color: palette.muted, fontFamily: fonts.sans, fontSize: 14, lineHeight: 21, marginTop: 4 },
});
