import { useLocalSearchParams } from 'expo-router';
import { ActivityIndicator } from 'react-native';

import { AuthPage, ErrorBanner, HelperText, PageHeading, TextButton, palette } from '../components/AuthUI';
import { OrdenProductosControl, ProductoCard, ProductosPagination } from '../components/ProductosUI';
import { PedidoCatalogo } from '../components/PedidoCatalogo';
import { useProductosPage } from '../hooks/useProductosPage';

export default function CatalogoScreen() {
  const params = useLocalSearchParams<{
    emprendimientoId?: string | string[];
    tipoVendedor?: string | string[];
    direccionRecogida?: string;
    pendienteId?: string;
  }>();
  const id = typeof params.emprendimientoId === 'string' ? params.emprendimientoId : undefined;
  const sellerType =
    params.tipoVendedor === 'AMBULANTE' || params.tipoVendedor === 'PUNTO_FIJO'
      ? params.tipoVendedor
      : undefined;
  const { data, busy, error, offlineUpdatedAt, paginasGuardadas, page, sort, changeSort, refresh, setPage } =
    useProductosPage('client', id);
  const anteriorGuardada = paginasGuardadas.filter((numero) => numero < page).at(-1);
  const siguienteGuardada = paginasGuardadas.find((numero) => numero > page);
  return (
    <AuthPage>
      <PageHeading eyebrow="Catálogo" title={data?.emprendimiento?.nombre ?? 'Productos del emprendimiento'}
        description="Consulta los precios por unidad y las cantidades disponibles. Los agotados están marcados como Solo reserva." />
      <OrdenProductosControl value={sort} onChange={changeSort} />
      <ErrorBanner message={error} />
      {busy ? <ActivityIndicator accessibilityLabel="Cargando catálogo" color={palette.green} /> : null}
      {!busy && data?.content.length === 0 ? <HelperText>Este emprendimiento aún no tiene productos publicados.</HelperText> : null}
      {data && offlineUpdatedAt !== null ? (
        <>
          <HelperText>
            Datos guardados. Última actualización: {new Date(offlineUpdatedAt).toLocaleString('es-CO')}.
            El precio y la disponibilidad pueden haber cambiado.
          </HelperText>
          {data.totalElements > data.size ? (
            <HelperText>Sin conexión solo están disponibles las páginas guardadas.</HelperText>
          ) : null}
          {!sellerType ? data.content.map((producto) => <ProductoCard key={producto.id} producto={producto} />) : null}
          {!sellerType ? <ProductosPagination data={data} busy={busy} onPage={setPage} /> : null}
        </>
      ) : null}
      {sellerType ? (
        <PedidoCatalogo
          key={`${id}:${params.pendienteId ?? ''}`}
          emprendimientoId={id ?? ''}
          offline={offlineUpdatedAt !== null}
          direccionRecogida={params.direccionRecogida}
          pendienteId={params.pendienteId}
          tipoVendedor={sellerType}
          productos={data?.content ?? []}
          enabled={!busy && ((data !== null && !error) || Boolean(params.pendienteId))}
          pageData={data}
          pageBusy={busy}
          onPage={setPage}
        />
      ) : !busy && data && offlineUpdatedAt === null ? (
        <HelperText>No se pudo identificar la modalidad de este emprendimiento. Vuelve al listado y selecciónalo de nuevo.</HelperText>
      ) : null}
      {!busy && !data && anteriorGuardada !== undefined ? (
        <TextButton icon="chevron-left" title="Página guardada anterior" onPress={() => setPage(anteriorGuardada)} />
      ) : null}
      {!busy && !data && siguienteGuardada !== undefined ? (
        <TextButton icon="chevron-right" title="Página guardada siguiente" onPress={() => setPage(siguienteGuardada)} />
      ) : null}
      <TextButton icon="refresh-cw" title="Actualizar catálogo" disabled={busy} onPress={refresh} />
    </AuthPage>
  );
}
