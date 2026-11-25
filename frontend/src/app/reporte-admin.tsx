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
  PageHeading,
  PrimaryButton,
  TextButton,
  palette,
} from '../components/AuthUI';
import { ApiError } from '../lib/api';
import { manejarError, mensajeDeCampo } from '../lib/errores';
import {
  estadoReporteNombres,
  motivoReporteNombres,
  obtenerReporte,
  resolverReporte,
  type ReporteDetalle,
} from '../lib/reportes';
import { rolNombres } from '../lib/usuarios';

function param(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
}

const entidadNombres: Record<string, string> = { PEDIDO: 'Pedido', DOMICILIO: 'Domicilio', USUARIO: 'Usuario' };

/** Convierte el resumen de la entidad en líneas legibles sin depender de su forma exacta. */
function lineasResumen(resumen: Record<string, unknown>): string[] {
  const etiquetas: Record<string, string> = {
    estado: 'Estado',
    modalidad: 'Modalidad',
    emprendimiento: 'Emprendimiento',
    cliente: 'Cliente',
    domiciliario: 'Domiciliario',
    total: 'Total',
    nombre: 'Nombre',
    rol: 'Rol',
    pedidoId: 'Pedido',
  };
  return Object.entries(resumen)
    .filter(([, valor]) => valor !== null && valor !== undefined)
    .map(([clave, valor]) => {
      const texto =
        typeof valor === 'object' && valor !== null && 'nombre' in valor
          ? String((valor as { nombre: unknown }).nombre ?? '')
          : clave === 'total' && typeof valor === 'number'
            ? `$${valor.toLocaleString('es-CO')}`
            : clave === 'rol'
              ? (rolNombres[String(valor)] ?? String(valor))
              : String(valor);
      return `${etiquetas[clave] ?? clave}: ${texto}`;
    });
}

/** RF-61 (detalle) y RF-62 (resolver con una nota que recibe el autor). */
export default function ReporteAdminScreen() {
  const reporteId = param(useLocalSearchParams<{ reporteId?: string }>().reporteId);
  const [reporte, setReporte] = useState<ReporteDetalle | null>(null);
  const [nota, setNota] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const key = useRef<string | null>(null);

  const [intento, setIntento] = useState(0);

  useEffect(() => {
    async function cargar() {
      setLoading(true);
      setError(null);
      if (!reporteId) {
        setError('No se indicó el reporte.');
        setLoading(false);
        return;
      }
      try {
        setReporte(await obtenerReporte(reporteId));
      } catch (cause) {
        setError(
          cause instanceof ApiError && cause.status === 404
            ? 'No encontramos el reporte.'
            : await manejarError(cause, 'No se pudo cargar el reporte.'),
        );
      } finally {
        setLoading(false);
      }
    }
    void cargar();
  }, [reporteId, intento]);

  async function resolver() {
    if (!reporteId) return;
    setBusy(true);
    setError(null);
    key.current ??= randomUUID();
    try {
      await resolverReporte(reporteId, nota, key.current);
      setIntento((i) => i + 1);
    } catch (cause) {
      key.current = null;
      setError(mensajeDeCampo(cause) ?? (await manejarError(cause, 'No se pudo resolver el reporte.')));
    } finally {
      setBusy(false);
    }
  }

  const entidad = reporte?.entidad;

  return (
    <AuthPage>
      <PageHeading eyebrow="Reporte" title={reporte ? motivoReporteNombres[reporte.motivo] : 'Reporte'} description={reporte ? estadoReporteNombres[reporte.estado] : ''} />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando reporte" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {reporte ? (
        <>
          <View style={styles.card}>
            <Text style={styles.label}>Descripción</Text>
            <Text style={styles.body}>{reporte.descripcion}</Text>
            <Text style={styles.detail}>
              Autor: {reporte.autor.nombre ?? 'Usuario'} ({rolNombres[reporte.autor.rol ?? ''] ?? reporte.autor.rol})
            </Text>
            <Text style={styles.detail}>Enviado: {formatDate(reporte.createdAt)}</Text>
          </View>
          {entidad ? (
            <View style={styles.card}>
              <Text style={styles.label}>{entidadNombres[entidad.tipo] ?? entidad.tipo} relacionado</Text>
              {entidad.disponible && entidad.resumen ? (
                lineasResumen(entidad.resumen).map((linea) => (
                  <Text key={linea} style={styles.detail}>{linea}</Text>
                ))
              ) : (
                <Text style={styles.detail}>No disponible.</Text>
              )}
              {entidad.tipo === 'USUARIO' ? (
                <TextButton
                  title="Ver usuario"
                  onPress={() => router.push({ pathname: '/usuario-admin', params: { usuarioId: entidad.id } })}
                />
              ) : null}
            </View>
          ) : null}
          {reporte.estado === 'RESUELTO' ? (
            <View style={styles.card}>
              <Text style={styles.label}>Resolución</Text>
              <Text style={styles.body}>{reporte.notaResolucion}</Text>
              <Text style={styles.detail}>
                Por {reporte.resueltoPor?.nombre ?? 'Administrador'} · {formatDate(reporte.updatedAt)}
              </Text>
            </View>
          ) : (
            <>
              <Field
                label="Nota de resolución"
                value={nota}
                onChangeText={setNota}
                multiline
                maxLength={1000}
                editable={!busy}
                placeholder="Qué se hizo para resolverlo. El autor la recibirá."
              />
              <HelperText>De 5 a 1000 caracteres.</HelperText>
              <PrimaryButton
                title="Marcar como resuelto"
                busy={busy}
                disabled={nota.trim().length < 5}
                onPress={() => void resolver()}
              />
            </>
          )}
        </>
      ) : null}
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
  label: { color: palette.muted, fontFamily: fonts.sans, fontSize: 13, fontWeight: '700' },
  body: { color: palette.dark, fontFamily: fonts.sans, fontSize: 15, lineHeight: 22, marginTop: 4 },
  detail: { color: palette.muted, fontFamily: fonts.sans, fontSize: 14, lineHeight: 21, marginTop: 4 },
});
