import { fonts } from '../components/theme';
import { useEffect, useState } from 'react';
import { router } from 'expo-router';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  HelperText,
  PageHeading,
  PrimaryButton,
  palette,
} from '../components/AuthUI';
import { manejarError } from '../lib/errores';
import {
  estadoReporteNombres,
  listarReportes,
  motivoReporteNombres,
  type ReporteItem,
} from '../lib/reportes';

function formatDate(value: string) {
  return new Date(value).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
}

/** RF-60 Edge 3: el usuario ve el estado de los reportes que envió. */
export default function MisReportesScreen() {
  const [items, setItems] = useState<ReporteItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function cargar() {
      setLoading(true);
      setError(null);
      try {
        const pagina = await listarReportes({ size: 50, sort: 'createdAt,desc' });
        setItems(pagina.content);
      } catch (cause) {
        setError(await manejarError(cause, 'No se pudieron cargar tus reportes.'));
      } finally {
        setLoading(false);
      }
    }
    void cargar();
  }, []);

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Soporte"
        title="Mis reportes"
        description="Recibirás una notificación cuando el administrador resuelva cada reporte."
      />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando reportes" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {items.map((item) => (
        <View key={item.id} style={styles.card}>
          <Text style={styles.title}>{motivoReporteNombres[item.motivo]}</Text>
          <Text style={[styles.badge, item.estado === 'RESUELTO' && styles.resolved]}>
            {estadoReporteNombres[item.estado]}
          </Text>
          <Text style={styles.detail}>Enviado: {formatDate(item.createdAt)}</Text>
        </View>
      ))}
      {!loading && !error && items.length === 0 ? <HelperText>No has enviado reportes.</HelperText> : null}
      <PrimaryButton
        title="Reportar un problema de la aplicación"
        onPress={() => router.push('/reportar')}
      />
    </AuthPage>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: palette.white,
    borderColor: palette.border,
    borderRadius: 16,
    borderWidth: 1,
    marginVertical: 6,
    padding: 14,
  },
  title: { color: palette.dark, fontFamily: fonts.sans, fontSize: 15, fontWeight: '800' },
  badge: { color: '#9A6700', fontFamily: fonts.sans, fontSize: 13, fontWeight: '700', marginTop: 4 },
  resolved: { color: palette.green },
  detail: { color: palette.muted, fontFamily: fonts.sans, fontSize: 13, marginTop: 4 },
});
