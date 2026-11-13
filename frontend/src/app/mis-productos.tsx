import { useState } from 'react';
import { router } from 'expo-router';
import { ActivityIndicator, Text } from 'react-native';

import { AuthPage, ErrorBanner, HelperText, PageHeading, PrimaryButton, TextButton, palette } from '../components/AuthUI';
import { OrdenProductosControl, ProductoCard, ProductosPagination, productStyles } from '../components/ProductosUI';
import { useProductosPage } from '../hooks/useProductosPage';

export default function MisProductosScreen() {
  const { data, busy, error, sort, changeSort, refresh, setPage } = useProductosPage('seller');
  const [notice, setNotice] = useState<string | null>(null);
  return (
    <AuthPage>
      <PageHeading eyebrow="Tu catálogo" title="Mis productos"
        description={data?.emprendimiento?.nombre ?? 'Publica lo que vendes y mantén al día las cantidades disponibles.'} />
      {data?.emprendimiento ? <PrimaryButton title="Agregar producto" onPress={() => router.push('/nuevo-producto')} /> : null}
      {data && !data.emprendimiento ? (
        <>
          <HelperText>Primero crea tu emprendimiento para publicar productos.</HelperText>
          <PrimaryButton title="Crear mi emprendimiento" onPress={() => router.push('/emprendimiento')} />
        </>
      ) : null}
      <OrdenProductosControl value={sort} onChange={changeSort} />
      {notice ? <Text accessibilityRole="status" style={productStyles.status}>{notice}</Text> : null}
      <ErrorBanner message={error} />
      {busy ? <ActivityIndicator accessibilityLabel="Cargando productos" color={palette.green} /> : null}
      {!busy && data?.emprendimiento && data.content.length === 0 ? <HelperText>Aún no has publicado productos.</HelperText> : null}
      {!busy ? data?.content.map((item) => <ProductoCard key={`${item.id}:${item.updatedAt}`} producto={item} inventory
        onUpdated={() => { setNotice('Catálogo actualizado.'); refresh(); }} />) : null}
      {data ? <ProductosPagination data={data} busy={busy} onPage={setPage} /> : null}
      <TextButton title="Actualizar listado" disabled={busy} onPress={refresh} />
      <TextButton title="Volver a mi cuenta" onPress={() => router.replace('/session')} />
    </AuthPage>
  );
}
