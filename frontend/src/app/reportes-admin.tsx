import { fonts } from '../components/theme';
import { useEffect, useState } from 'react';
import { router } from 'expo-router';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  HelperText,
  OptionButton,
  PageHeading,
  TextButton,
  palette,
} from '../components/AuthUI';
import { manejarError } from '../lib/errores';
import {
  estadoReporteNombres,
  listarReportes,
  motivoReporteNombres,
  type EstadoReporte,
  type ReportesPagina,
} from '../lib/reportes';
import { rolNombres } from '../lib/usuarios';

const FILTROS: (EstadoReporte | undefined)[] = ['PENDIENTE', 'RESUELTO', undefined];

function formatDate(value: string) {
  return new Date(value).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
}

/** RF-61: los pendientes primero, del más antiguo al más reciente. */
export default function ReportesAdminScreen() {
  const [estado, setEstado] = useState<EstadoReporte | undefined>('PENDIENTE');
  const [pagina, setPagina] = useState<ReportesPagina | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function cargar() {
      setLoading(true);
      setError(null);
      try {
        setPagina(await listarReportes({ estado, page, size: 20 }));
      } catch (cause) {
        setError(await manejarError(cause, 'No se pudieron cargar los reportes.'));
      } finally {
        setLoading(false);
      }
    }
    void cargar();
  }, [estado, page]);

  const totalPaginas = pagina ? Math.max(1, Math.ceil(pagina.totalElements / pagina.size)) : 1;

  return (
    <AuthPage>
      <PageHeading eyebrow="Administración" title="Reportes" description="Revisa y resuelve los problemas que reportan los usuarios." />
      <View style={styles.filters}>
        {FILTROS.map((f) => (
          <OptionButton
            key={f ?? 'TODOS'}
            label={f ? estadoReporteNombres[f] : 'Todos'}
            selected={estado === f}
            onPress={() => {
              setEstado(f);
              setPage(0);
            }}
          />
        ))}
      </View>
      {loading ? <ActivityIndicator accessibilityLabel="Cargando reportes" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {pagina?.content.map((item) => (
        <Pressable
          key={item.id}
          accessibilityRole="button"
          accessibilityLabel={`${motivoReporteNombres[item.motivo]}, ${estadoReporteNombres[item.estado]}`}
          onPress={() => router.push({ pathname: '/reporte-admin', params: { reporteId: item.id } })}
          style={styles.card}
        >
          <Text style={styles.title}>{motivoReporteNombres[item.motivo]}</Text>
          <Text style={styles.detail}>
            {item.autor.nombre ?? 'Usuario'} · {rolNombres[item.autor.rol ?? ''] ?? item.autor.rol}
          </Text>
          <Text style={styles.detail}>
            {estadoReporteNombres[item.estado]} · {formatDate(item.createdAt)}
          </Text>
        </Pressable>
      ))}
      {!loading && pagina && pagina.content.length === 0 ? <HelperText>No hay reportes con este filtro.</HelperText> : null}
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
  title: { color: palette.dark, fontFamily: fonts.sans, fontSize: 15, fontWeight: '800' },
  detail: { color: palette.muted, fontFamily: fonts.sans, fontSize: 13, marginTop: 4 },
  pager: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
});
