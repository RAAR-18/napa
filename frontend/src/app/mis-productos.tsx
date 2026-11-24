import { useEffect, useRef, useState } from 'react';
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
import { outbox } from '../database/outbox';
import { useAccionesPendientes } from '../hooks/useOutbox';
import { edicionDescartableSinRevision, cambioPendienteProducto, productoPendiente } from '../lib/productos-pendientes';

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
  const [errorPendiente, setErrorPendiente] = useState<string | null>(null);
  const acciones = useAccionesPendientes();
  const ediciones = acciones.flatMap((accion) => {
    const edicion = cambioPendienteProducto(accion);
    return edicion ? [{ accion, ...edicion }] : [];
  });
  const pendientes = acciones.flatMap((accion) => {
    const producto = productoPendiente(accion);
    return producto ? [{ accion, producto }] : [];
  });
  const cantidad = pendientes.length + ediciones.length;
  const cantidadAnterior = useRef(cantidad);
  useEffect(() => {
    const omitir = cantidadAnterior.current <= cantidad;
    cantidadAnterior.current = cantidad;
    if (!omitir) refresh();
  }, [cantidad, refresh]);
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
      {data?.emprendimiento ? (
        <PrimaryButton title="Agregar producto" onPress={() => router.push({
          pathname: '/nuevo-producto', params: { emprendimientoId: data.emprendimiento!.id },
        })} />
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
      {data?.emprendimiento ? (
        <TextButton title="Editar mi emprendimiento" onPress={() => router.push('/mi-emprendimiento')} />
      ) : null}
      <OrdenProductosControl value={sort} onChange={changeSort} />
      {notice ? <Text aria-live="polite" style={productStyles.status}>{notice}</Text> : null}
      <ErrorBanner message={error} />
      <ErrorBanner message={errorPendiente} />
      {pendientes.length > 0 ? (
        <CardGrid>
          {pendientes.map(({ accion, producto }) => (
            <GridCard key={accion.id}>
              <Text style={productStyles.name}>{producto.nombre}</Text>
              <Text style={productStyles.detail}>
                {producto.precio.toLocaleString('es-CO')} COP por {producto.unidad} · Cantidad inicial: {producto.cantidadDisponible}
              </Text>
              <Text style={productStyles.status}>
                {accion.status === 'FALLIDA' ? 'Requiere corrección' : 'Pendiente de sincronización'}
              </Text>
              {accion.status === 'FALLIDA' ? (
                <>
                  <HelperText>{accion.lastError ?? 'El servidor rechazó el registro.'}</HelperText>
                  <TextButton title="Corregir y reenviar" onPress={() => router.push({
                    pathname: '/nuevo-producto', params: { pendienteId: accion.id },
                  })} />
                  <TextButton title="Descartar" onPress={() => {
                    setErrorPendiente(null);
                    void outbox.descartar(accion.id).catch((cause: unknown) => {
                      setErrorPendiente(cause instanceof Error ? cause.message : 'No se pudo descartar el registro.');
                    });
                  }} />
                </>
              ) : null}
            </GridCard>
          ))}
        </CardGrid>
      ) : null}
      {ediciones.length ? (
        <CardGrid>
          {ediciones.map(({ accion, productoId, cambios, inventario }) => (
            <GridCard key={accion.id}>
              <Text style={productStyles.name}>{accion.descripcion}</Text>
              {cambios.cantidadDisponible !== undefined ? (
                <Text>Cantidad propuesta: {cambios.cantidadDisponible.toLocaleString('es-CO')}</Text>
              ) : null}
              {cambios.nombre !== undefined ? <Text>Nombre propuesto: {cambios.nombre}</Text> : null}
              {cambios.precio !== undefined ? <Text>Precio propuesto: {cambios.precio.toLocaleString('es-CO')} COP</Text> : null}
              {cambios.descripcion !== undefined ? <Text>Descripción propuesta: {cambios.descripcion ?? 'Sin descripción'}</Text> : null}
              <Text style={productStyles.status}>
                {accion.status === 'FALLIDA' ? 'Requiere revisión' : inventario ? 'Ajuste pendiente de sincronización' : 'Edición pendiente de sincronización'}
              </Text>
              {accion.status === 'FALLIDA' ? (
                <>
                  <HelperText>{accion.lastError ?? 'El servidor rechazó la edición.'}</HelperText>
                  {!edicionDescartableSinRevision(accion) ? (
                    <TextButton title={inventario ? 'Revisar ajuste' : 'Revisar edición'} onPress={() => router.push({
                      pathname: '/editar-producto', params: { productoId, pendienteId: accion.id },
                    })} />
                  ) : null}
                  <TextButton title="Descartar" onPress={() => {
                    void outbox.descartar(accion.id).catch((cause: unknown) => {
                      setErrorPendiente(cause instanceof Error ? cause.message : 'No se pudo descartar la edición.');
                    });
                  }} />
                </>
              ) : null}
            </GridCard>
          ))}
        </CardGrid>
      ) : null}
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
                <ProductoCard
                  key={`${item.id}:${item.updatedAt}:${showingOffline}`}
                  producto={item}
                  inventory
                  offline={showingOffline}
                  edicionBloqueada={ediciones.some(({ productoId }) => productoId === item.id)}
                  resumen={resumenes[item.id]}
                  onUpdated={() => {
                    setNotice('Catálogo actualizado.');
                    refresh();
                  }}
                />
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
