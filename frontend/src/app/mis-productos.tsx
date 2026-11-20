import { useState } from 'react';
import { router } from 'expo-router';
import { ActivityIndicator, Text } from 'react-native';

import {
  AuthPage,
  ErrorBanner,
  HelperText,
  PageHeading,
  PrimaryButton,
  TextButton,
  palette,
} from '../components/AuthUI';
import {
  OrdenProductosControl,
  ProductoCard,
  ProductosPagination,
  productStyles,
} from '../components/ProductosUI';
import { useProductosPage } from '../hooks/useProductosPage';
import { useResumenesCalificacion } from '../hooks/useResumenesCalificacion';
import { CardGrid, GridCard } from '../components/NapaUI';

export default function MisProductosScreen() {
  const { data, busy, error, offlineUpdatedAt, sort, changeSort, refresh, setPage } =
    useProductosPage('seller');
  const showingOffline = offlineUpdatedAt !== null;
  // RF-02: el vendedor ve cómo califican sus productos.
  const resumenes = useResumenesCalificacion(
    'PRODUCTO',
    showingOffline ? [] : (data?.content.map((item) => item.id) ?? []),
  );
  const [notice, setNotice] = useState<string | null>(null);
  return (
    <AuthPage>
      <PageHeading
        eyebrow="Tu catálogo"
        title="Mis productos"
        description={
          data?.emprendimiento?.nombre ??
          'Publica lo que vendes y mantén al día las cantidades disponibles.'
        }
      />
      {!showingOffline && data?.emprendimiento ? (
        <PrimaryButton title="Agregar producto" onPress={() => router.push('/nuevo-producto')} />
      ) : null}
      {data && !data.emprendimiento ? (
        <>
          <HelperText>Primero crea tu emprendimiento para publicar productos.</HelperText>
          <PrimaryButton
            title="Crear mi emprendimiento"
            onPress={() => router.push('/emprendimiento')}
          />
        </>
      ) : null}
      <OrdenProductosControl value={sort} onChange={changeSort} />
      {notice ? <Text aria-live="polite" style={productStyles.status}>{notice}</Text> : null}
      <ErrorBanner message={error} />
      {offlineUpdatedAt !== null ? (
        <HelperText>
          Datos guardados. Última actualización:{' '}
          {new Date(offlineUpdatedAt).toLocaleString('es-CO')}. Las cantidades y los estados pueden
          haber cambiado.
        </HelperText>
      ) : null}
      {busy ? (
        <ActivityIndicator accessibilityLabel="Cargando productos" color={palette.green} />
      ) : null}
      {!busy && data?.emprendimiento && data.content.length === 0 ? (
        <HelperText>Aún no has publicado productos.</HelperText>
      ) : null}
      <CardGrid>
        {!busy
          ? data?.content.map((item) => (
              <GridCard key={item.id}>
                {showingOffline ? (
                  <Text>
                    {item.nombre} · {item.precio.toLocaleString('es-CO')} COP por {item.unidad}
                    {'\n'}Cantidad guardada: {item.cantidadDisponible} ·{' '}
                    {item.estado === 'AGOTADO' ? 'Agotado' : 'Disponible'}
                  </Text>
                ) : (
                  <ProductoCard
                    key={`${item.id}:${item.updatedAt}`}
                    producto={item}
                    inventory
                    resumen={resumenes[item.id]}
                    onUpdated={() => {
                      setNotice('Catálogo actualizado.');
                      refresh();
                    }}
                  />
                )}
              </GridCard>
            ))
          : null}
      </CardGrid>
      {data ? <ProductosPagination data={data} busy={busy} onPage={setPage} /> : null}
      <TextButton title="Actualizar listado" disabled={busy} onPress={refresh} />
      <TextButton title="Volver a mi cuenta" onPress={() => router.replace('/session')} />
    </AuthPage>
  );
}
