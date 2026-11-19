import { useCallback, useState } from 'react';
import { useFocusEffect } from 'expo-router';
import { AppState } from 'react-native';

import { ApiError, NetworkError } from '../lib/api';
import { getSessionGeneration, getValidSession, readSession, type StoredSession } from '../lib/auth';
import {
  listCatalogo, listMisProductos, productErrorMessage,
  type OrdenProductos, type ProductosPage,
} from '../lib/productos';
import {
  borrarInventarioGuardado,
  guardarPaginaInventario,
  leerPaginaInventario,
} from '../lib/inventario-cache';
import {
  borrarCatalogoGuardado,
  guardarPaginaCatalogo,
  leerPaginaCatalogo,
  listarPaginasGuardadasCatalogo,
} from '../lib/catalogo-cache';

export function useProductosPage(mode: 'seller' | 'client', businessId?: string) {
  const [data, setData] = useState<ProductosPage | null>(null);
  const [offlineUpdatedAt, setOfflineUpdatedAt] = useState<number | null>(null);
  const [paginasGuardadas, setPaginasGuardadas] = useState<number[]>([]);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState<{ page: number; sort: OrdenProductos }>({ page: 0, sort: 'nombre,asc' });

  useFocusEffect(useCallback(() => {
    let active = true;
    let generation = 0;
    async function load() {
      const requestGeneration = ++generation;
      const authGeneration = getSessionGeneration();
      const isCurrent = () =>
        active && requestGeneration === generation && getSessionGeneration() === authGeneration;
      let localSession: StoredSession | null = null;

      setBusy(true);
      setError(null);
      setData(null);
      setOfflineUpdatedAt(null);
      setPaginasGuardadas([]);

      try {
        localSession = await readSession();

        const session = await getValidSession();
        const seller =
          session.rol === 'VENDEDOR_AMBULANTE' ||
          session.rol === 'VENDEDOR_PUNTO_FIJO';

        if (mode === 'seller' ? !seller : session.rol !== 'CLIENTE') {
          throw new Error(
            mode === 'seller'
              ? 'Solo los vendedores pueden gestionar productos.'
              : 'Ingresa como cliente para consultar el catálogo.',
          );
        }

        if (!isCurrent()) return;
        if (mode === 'client' && !businessId) {
          throw new Error('Selecciona un emprendimiento para ver sus productos.');
        }

        const result =
          mode === 'seller'
            ? await listMisProductos(query.page, query.sort)
            : await listCatalogo(businessId!, query.page, query.sort);

        if (!isCurrent()) return;

        if (session.usuarioId) {
          try {
            if (mode === 'seller') {
              await guardarPaginaInventario(session.usuarioId, query.sort, result, isCurrent);
            } else if (businessId) {
              await guardarPaginaCatalogo(session.usuarioId, businessId, query.sort, result, isCurrent);
            }
          } catch (cacheError) {
            console.warn('No se pudo guardar la página de productos.', cacheError);
          }
        }

        if (!isCurrent()) return;
        const lastPage = Math.max(0, Math.ceil(result.totalElements / result.size) - 1);
        if (query.page > lastPage) {
          setQuery((current) => ({ ...current, page: lastPage }));
        } else {
          setData(result);
        }
      } catch (cause) {
        if (!isCurrent()) return;

        const cuentaId = localSession?.usuarioId;
        const vendedorLocal =
          localSession?.rol === 'VENDEDOR_AMBULANTE' ||
          localSession?.rol === 'VENDEDOR_PUNTO_FIJO';

        const puedeLeerCopia = mode === 'seller'
          ? vendedorLocal
          : localSession?.rol === 'CLIENTE' && Boolean(businessId);
        if (cuentaId && puedeLeerCopia && cause instanceof NetworkError) {
          try {
            const cached = mode === 'seller'
              ? await leerPaginaInventario(cuentaId, query.sort, query.page)
              : await leerPaginaCatalogo(cuentaId, businessId!, query.sort, query.page);
            const paginas = !cached && mode === 'client' && businessId
              ? await listarPaginasGuardadasCatalogo(cuentaId, businessId, query.sort)
              : [];
            const currentSession = await readSession();
            if (
              !isCurrent() ||
              currentSession?.usuarioId !== cuentaId ||
              currentSession.rol !== localSession?.rol
            ) {
              return;
            }

            if (cached) {
              setData(cached.data);
              setOfflineUpdatedAt(cached.updatedAt);
            } else {
              setPaginasGuardadas(paginas);
              setError(mode === 'seller'
                ? 'No hay datos descargados para esta página del inventario.'
                : 'Esta página del catálogo no está descargada o ya venció.');
            }
            return;
          } catch (cacheError) {
            console.warn('No se pudo leer la página de productos guardada.', cacheError);
          }
        }

        if (
          cuentaId &&
          cause instanceof ApiError &&
          (cause.status === 401 || cause.status === 403 || (mode === 'client' && cause.status === 404))
        ) {
          try {
            if (mode === 'seller') {
              await borrarInventarioGuardado(cuentaId);
            } else {
              await borrarCatalogoGuardado(cuentaId, cause.status === 404 ? businessId : undefined);
            }
          } catch (cacheError) {
            console.warn('No se pudo borrar la copia guardada de productos.', cacheError);
          }
        }

        if (!isCurrent()) return;
        const message = await productErrorMessage(cause);
        if (active && requestGeneration === generation) setError(message);
      } finally {
        if (active && requestGeneration === generation) setBusy(false);
      }
    }
    void load();
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') void load();
    });
    return () => { active = false; subscription.remove(); };
  }, [mode, businessId, query]));

  return {
    data, busy, error, offlineUpdatedAt, paginasGuardadas, page: query.page, sort: query.sort,
    setPage: (page: number) => setQuery((current) => ({ ...current, page })),
    refresh: () => setQuery((current) => ({ ...current })),
    changeSort: (sort: OrdenProductos) => setQuery({ page: 0, sort }),
  };
}
