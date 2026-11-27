import { fonts } from '../components/theme';
import { useEffect, useState } from 'react';
import { router } from 'expo-router';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  Field,
  HelperText,
  OptionButton,
  PageHeading,
  TextButton,
  palette,
} from '../components/AuthUI';
import { manejarError, mensajeDeCampo } from '../lib/errores';
import {
  estadoCuentaNombres,
  listarUsuarios,
  rolNombres,
  type FiltrosUsuarios,
  type UsuariosPagina,
} from '../lib/usuarios';

const ROLES: { valor?: string; nombre: string }[] = [
  { nombre: 'Todos' },
  { valor: 'CLIENTE', nombre: 'Clientes' },
  { valor: 'VENDEDOR', nombre: 'Vendedores' },
  { valor: 'DOMICILIARIO', nombre: 'Domiciliarios' },
];

function formatDate(value: string) {
  return new Date(value).toLocaleDateString('es-CO', { dateStyle: 'medium' });
}

/** RF-69: búsqueda por nombre, celular o correo y filtro por rol. */
export default function UsuariosAdminScreen() {
  const [rol, setRol] = useState<string | undefined>(undefined);
  const [texto, setTexto] = useState('');
  const [q, setQ] = useState<string | undefined>(undefined);
  const [page, setPage] = useState(0);
  const [pagina, setPagina] = useState<UsuariosPagina | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function cargar() {
      setLoading(true);
      setError(null);
      try {
        const filtros: FiltrosUsuarios = { rol, q, page, size: 20 };
        setPagina(await listarUsuarios(filtros));
      } catch (cause) {
        setError(mensajeDeCampo(cause) ?? (await manejarError(cause, 'No se pudieron cargar los usuarios.')));
      } finally {
        setLoading(false);
      }
    }
    void cargar();
  }, [rol, q, page]);

  // Busca medio segundo después de dejar de escribir; menos de 2 letras no filtra.
  useEffect(() => {
    const timer = setTimeout(() => {
      const limpio = texto.trim();
      setQ(limpio.length >= 2 ? limpio : undefined);
      setPage(0);
    }, 500);
    return () => clearTimeout(timer);
  }, [texto]);

  const totalPaginas = pagina ? Math.max(1, Math.ceil(pagina.totalElements / pagina.size)) : 1;

  return (
    <AuthPage>
      <PageHeading eyebrow="Administración" title="Usuarios" description={pagina ? `${pagina.totalElements} usuarios` : ''} />
      <Field label="Buscar" value={texto} onChangeText={setTexto} placeholder="Nombre, celular o correo" autoCapitalize="none" />
      <View style={styles.filters}>
        {ROLES.map((r) => (
          <OptionButton
            key={r.nombre}
            label={r.nombre}
            selected={rol === r.valor}
            onPress={() => {
              setRol(r.valor);
              setPage(0);
            }}
          />
        ))}
      </View>
      {loading ? <ActivityIndicator accessibilityLabel="Cargando usuarios" color={palette.green} /> : null}
      <ErrorBanner message={error} />
      {pagina?.content.map((u) => (
        <Pressable
          key={u.id}
          accessibilityRole="button"
          accessibilityLabel={`${u.nombre}, ${rolNombres[u.rol] ?? u.rol}`}
          onPress={() => router.push({ pathname: '/usuario-admin', params: { usuarioId: u.id } })}
          style={styles.card}
        >
          <Text style={styles.title}>{u.nombre}</Text>
          <Text style={styles.detail}>
            {rolNombres[u.rol] ?? u.rol} · {estadoCuentaNombres[u.estado] ?? u.estado} · Desde {formatDate(u.createdAt)}
          </Text>
        </Pressable>
      ))}
      {!loading && pagina && pagina.content.length === 0 ? <HelperText>No hay usuarios con este filtro.</HelperText> : null}
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
