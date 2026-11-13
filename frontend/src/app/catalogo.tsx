import { router, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator } from 'react-native';

import { AuthPage, ErrorBanner, HelperText, PageHeading, TextButton, palette } from '../components/AuthUI';
import { OrdenProductosControl } from '../components/ProductosUI';
import { PedidoCatalogo } from '../components/PedidoCatalogo';
import { useProductosPage } from '../hooks/useProductosPage';

export default function CatalogoScreen() {
  const params = useLocalSearchParams<{
    emprendimientoId?: string | string[];
    tipoVendedor?: string | string[];
  }>();
  const id = typeof params.emprendimientoId === 'string' ? params.emprendimientoId : undefined;
  const sellerType =
    params.tipoVendedor === 'AMBULANTE' || params.tipoVendedor === 'PUNTO_FIJO'
      ? params.tipoVendedor
      : undefined;
  const { data, busy, error, sort, changeSort, refresh, setPage } = useProductosPage('client', id);
  return (
    <AuthPage>
      <PageHeading eyebrow="Catálogo" title={data?.emprendimiento?.nombre ?? 'Productos del emprendimiento'}
        description="Consulta los precios por unidad y las cantidades disponibles. Los agotados están marcados como Solo reserva." />
      <OrdenProductosControl value={sort} onChange={changeSort} />
      <ErrorBanner message={error} />
      {busy ? <ActivityIndicator accessibilityLabel="Cargando catálogo" color={palette.green} /> : null}
      {!busy && data?.content.length === 0 ? <HelperText>Este emprendimiento aún no tiene productos publicados.</HelperText> : null}
      {sellerType ? (
        <PedidoCatalogo
          emprendimientoId={id ?? ''}
          tipoVendedor={sellerType}
          productos={data?.content ?? []}
          enabled={!busy && !error && data !== null}
          pageData={data}
          pageBusy={busy}
          onPage={setPage}
        />
      ) : !busy && data ? (
        <HelperText>No se pudo identificar la modalidad de este emprendimiento. Vuelve al listado y selecciónalo de nuevo.</HelperText>
      ) : null}
      <TextButton title="Actualizar catálogo" disabled={busy} onPress={refresh} />
      <TextButton title="Volver a emprendimientos" onPress={() => router.replace('/emprendimientos')} />
    </AuthPage>
  );
}
