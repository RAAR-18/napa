import { fonts } from '../components/theme';
import { useRef, useState } from 'react';
import { randomUUID } from '../lib/uuid';
import { router, useLocalSearchParams } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

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
import { outbox } from '../database/outbox';
import { ApiError, NetworkError } from '../lib/api';
import { manejarError, mensajeDeCampo } from '../lib/errores';
import {
  DESCRIPCION_MAXIMA,
  DESCRIPCION_MINIMA,
  MOTIVOS_CON_ENTIDAD,
  crearReporte,
  cuerpoReporte,
  motivoReporteNombres,
  type EntidadReporte,
  type MotivoReporte,
  type ReporteCreado,
} from '../lib/reportes';

function param(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

/** Motivos que tienen sentido según desde dónde se abre el formulario. */
function motivosPara(entidad: EntidadReporte): MotivoReporte[] {
  if (entidad.domicilioId) return ['DOMICILIO', 'PAGO', 'COMPORTAMIENTO_USUARIO', 'OTRO'];
  if (entidad.pedidoId) return ['PEDIDO', 'PAGO', 'COMPORTAMIENTO_USUARIO', 'OTRO'];
  if (entidad.usuarioId) return ['COMPORTAMIENTO_USUARIO', 'OTRO'];
  return ['PLATAFORMA', 'OTRO'];
}

/** RF-60: el reporte queda pendiente y el administrador recibe un aviso. */
export default function ReportarScreen() {
  const params = useLocalSearchParams<{ pedidoId?: string; domicilioId?: string; usuarioId?: string; contexto?: string }>();
  const entidad: EntidadReporte = {
    pedidoId: param(params.pedidoId),
    domicilioId: param(params.domicilioId),
    usuarioId: param(params.usuarioId),
  };
  const contexto = param(params.contexto);
  const motivos = motivosPara(entidad);
  const [motivo, setMotivo] = useState<MotivoReporte | null>(null);
  const [descripcion, setDescripcion] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [creado, setCreado] = useState<ReporteCreado | null>(null);
  const [guardadoSinConexion, setGuardadoSinConexion] = useState(false);
  const key = useRef<string | null>(null);

  const sinEntidad = !entidad.pedidoId && !entidad.domicilioId && !entidad.usuarioId;
  const descripcionValida = descripcion.trim().length >= DESCRIPCION_MINIMA;

  async function enviar() {
    if (!motivo) {
      setError('Elige el motivo del reporte.');
      return;
    }
    if (!descripcionValida) {
      setError(`Describe el problema con al menos ${DESCRIPCION_MINIMA} caracteres.`);
      return;
    }
    if (sinEntidad && MOTIVOS_CON_ENTIDAD.includes(motivo)) {
      setError('Para este motivo, abre el reporte desde el pedido o domicilio relacionado.');
      return;
    }
    setBusy(true);
    setError(null);
    key.current ??= randomUUID();
    const datos = { motivo, descripcion, ...entidad };
    let usuarioId: string | null = null;
    try {
      usuarioId = await outbox.propietario();
      setCreado(await crearReporte(datos, key.current));
    } catch (cause) {
      if (cause instanceof NetworkError && usuarioId) {
        // F041: se guarda con la misma clave y se envía sola cuando vuelva la conexión.
        try {
          await outbox.encolar({
            usuarioId,
            method: 'POST',
            path: '/api/v1/reportes',
            body: cuerpoReporte(datos),
            idempotencyKey: key.current,
            descripcion: `Reporte: ${motivoReporteNombres[motivo]}`,
          });
          setGuardadoSinConexion(true);
        } catch {
          setError('No se pudo guardar la acción en el teléfono. Conserva el formulario e intenta de nuevo.');
        }
        return;
      }
      key.current = null;
      if (cause instanceof ApiError && cause.code === 'CONFLICT') {
        setError('Ya tienes un reporte abierto sobre esto. Te avisaremos cuando se resuelva.');
      } else if (cause instanceof ApiError && cause.status === 404) {
        setError('No encontramos lo que quieres reportar.');
      } else {
        setError(mensajeDeCampo(cause) ?? (await manejarError(cause, 'No se pudo enviar el reporte.')));
      }
    } finally {
      setBusy(false);
    }
  }

  if (guardadoSinConexion) {
    return (
      <AuthPage>
        <PageHeading
          eyebrow="Sin conexión"
          title="Guardamos tu reporte"
          description="Se enviará solo cuando vuelva la conexión. Puedes verlo en Pendientes de envío."
        />
        <PrimaryButton title="Ver pendientes de envío" onPress={() => router.replace('/pendientes-envio')} />
      </AuthPage>
    );
  }

  if (creado) {
    return (
      <AuthPage>
        <PageHeading
          eyebrow="Reporte enviado"
          title="Recibimos tu reporte"
          description="Quedó pendiente de revisión. Te avisaremos cuando el administrador lo resuelva."
        />
        <View style={styles.card}>
          <Text style={styles.title}>{motivoReporteNombres[creado.motivo]}</Text>
          <Text style={styles.detail}>{creado.descripcion}</Text>
        </View>
        <PrimaryButton title="Ver mis reportes" onPress={() => router.replace('/mis-reportes')} />
      </AuthPage>
    );
  }

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Reportar un problema"
        title="¿Qué pasó?"
        description={contexto ?? 'Cuéntanos el problema para que el administrador pueda ayudarte.'}
      />
      <Text style={styles.label}>Motivo</Text>
      <View accessibilityRole="radiogroup" style={styles.options}>
        {motivos.map((m) => (
          <OptionButton key={m} label={motivoReporteNombres[m]} selected={motivo === m} onPress={() => setMotivo(m)} />
        ))}
      </View>
      <Field
        label="Descripción"
        value={descripcion}
        onChangeText={setDescripcion}
        multiline
        maxLength={DESCRIPCION_MAXIMA}
        editable={!busy}
        placeholder="Qué ocurrió, cuándo y con quién."
      />
      <HelperText>{`${descripcion.trim().length}/${DESCRIPCION_MAXIMA} (mínimo ${DESCRIPCION_MINIMA})`}</HelperText>
      <ErrorBanner message={error} />
      <PrimaryButton
        title="Enviar reporte"
        busy={busy}
        disabled={!motivo || !descripcionValida}
        onPress={() => void enviar()}
      />
      <View style={{ marginTop: 18 }}>
        <TextButton icon="x" title="Cancelar" variant="danger" disabled={busy} onPress={() => router.back()} />
      </View>
    </AuthPage>
  );
}

const styles = StyleSheet.create({
  label: { color: palette.dark, fontFamily: fonts.sans, fontSize: 14, fontWeight: '700', marginBottom: 6 },
  options: { gap: 8, marginBottom: 12 },
  card: {
    backgroundColor: palette.white,
    borderColor: palette.border,
    borderRadius: 16,
    borderWidth: 1,
    marginVertical: 7,
    padding: 16,
  },
  title: { color: palette.dark, fontFamily: fonts.sans, fontSize: 16, fontWeight: '800' },
  detail: { color: palette.muted, fontFamily: fonts.sans, fontSize: 14, lineHeight: 21, marginTop: 4 },
});
