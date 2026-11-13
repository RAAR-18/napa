import { useEffect, useState } from 'react';
import { router } from 'expo-router';
import { ActivityIndicator, StyleSheet, Switch, Text, View } from 'react-native';

import { AuthPage, ErrorBanner, HelperText, PageHeading, PrimaryButton, TextButton, palette } from '../components/AuthUI';
import { ApiError } from '../lib/api';
import { clearSession, getValidSession } from '../lib/auth';
import {
  actualizarPreferencias,
  listarPreferencias,
  tiposNotificacion,
  type PreferenciaNotificacion,
  type Rol,
  type TipoNotificacion,
} from '../lib/notificaciones';

function descripcion(tipo: string) {
  return tiposNotificacion[tipo as TipoNotificacion];
}

/**
 * RF-39: cada interruptor se guarda al tocarlo y solo cambia ese tipo. Se muestran los tipos que recibe el rol
 * actual; los de otros roles se conservan como estén.
 */
export default function PreferenciasNotificacionesScreen() {
  const [role, setRole] = useState<string | null>(null);
  const [preferencias, setPreferencias] = useState<PreferenciaNotificacion[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  const [saving, setSaving] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const session = await getValidSession();
        if (!active) return;
        setRole(session.rol);
        const response = await listarPreferencias();
        if (active) setPreferencias(response);
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 401) {
          await clearSession();
          router.replace('/login');
          return;
        }
        if (active) setError(cause instanceof Error ? cause.message : 'No se pudieron cargar tus preferencias.');
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [attempt]);

  async function cambiar(tipo: string, habilitada: boolean) {
    if (saving) return;
    setSaving(tipo);
    setError(null);
    const anteriores = preferencias;
    // Se ve el cambio de inmediato; si el servidor falla, se vuelve atrás.
    setPreferencias((actual) => actual?.map((item) => (item.tipo === tipo ? { ...item, habilitada } : item)) ?? actual);
    try {
      setPreferencias(await actualizarPreferencias([{ tipo, habilitada }]));
    } catch (cause) {
      setPreferencias(anteriores);
      setError(cause instanceof Error ? cause.message : 'No se pudo guardar el cambio.');
    } finally {
      setSaving(null);
    }
  }

  const visibles = (preferencias ?? []).filter((item) => {
    const info = descripcion(item.tipo);
    return info && role !== null && info.roles.includes(role as Rol);
  });

  return (
    <AuthPage>
      <PageHeading
        eyebrow="Notificaciones"
        title="Qué notificaciones recibo"
        description="Desactiva los avisos que no quieras. Los cambios aplican a las novedades que lleguen desde ahora."
      />
      {loading ? <ActivityIndicator accessibilityLabel="Cargando preferencias" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {error && !preferencias ? (
        <PrimaryButton title="Reintentar" onPress={() => setAttempt((value) => value + 1)} />
      ) : null}
      {preferencias && visibles.length === 0 && !loading ? (
        <HelperText>Tu cuenta aún no tiene tipos de notificación para configurar.</HelperText>
      ) : null}
      {visibles.map((item) => {
        const nombre = descripcion(item.tipo).nombre;
        return (
          <View key={item.tipo} style={styles.row}>
            <Text style={styles.label}>{nombre}</Text>
            <Switch
              accessibilityLabel={nombre}
              disabled={saving !== null}
              onValueChange={(value) => void cambiar(item.tipo, value)}
              thumbColor={palette.white}
              trackColor={{ false: '#C9D3CD', true: palette.green }}
              value={item.habilitada}
            />
          </View>
        );
      })}
      {visibles.length > 0 ? (
        <HelperText>Si desactivas un aviso, tampoco aparecerá en tu lista de notificaciones.</HelperText>
      ) : null}
      <TextButton title="Volver a mis notificaciones" onPress={() => router.replace('/notificaciones')} />
    </AuthPage>
  );
}

const styles = StyleSheet.create({
  row: {
    alignItems: 'center',
    backgroundColor: palette.white,
    borderColor: palette.border,
    borderRadius: 14,
    borderWidth: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginVertical: 5,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  label: { color: palette.dark, flex: 1, fontSize: 15, marginRight: 10 },
});
