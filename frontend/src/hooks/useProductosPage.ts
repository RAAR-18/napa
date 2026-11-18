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
} from '../lib/inventario-cache'


export function useProductosPage(mode: 'seller' | 'client', businessId?: string) {
  const [data, setData] = useState<ProductosPage | null>(null);
  const [offlineUpdatedAt, setOfflineUpdatedAt] = useState<number | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState<{ page: number; sort: OrdenProductos }>({ page: 0, sort: 'nombre,asc' });

  useFocusEffect(useCallback(() => {
    let active = true;
    let generation = 0;
    async function load() {
      const requestGeneration = ++generation;
      const authGeneration = getSessionGeneration();
      const isCurrent = () => active && requestGeneration === generation;
      let localSession: StoredSession | null = null;

      setBusy(true);
      setError(null);
      setData(null);
      setOfflineUpdatedAt(null);

      try {
        if (mode === 'seller') {
          localSession = await readSession();
        }

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

        if (mode === 'seller') {
          const currentSession = await readSession();
          if (!isCurrent() || currentSession?.usuarioId !== session.usuarioId) return;

          if (session.usuarioId) {
            try {
              await guardarPaginaInventario(
                session.usuarioId,
                query.sort,
                result,
                () => getSessionGeneration() === authGeneration,
              );
            } catch (cacheError) {
              console.warn('No se pudo guardar la página de inventario.', cacheError);
            }
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

        if (mode === 'seller' && cuentaId && vendedorLocal && cause instanceof NetworkError) {
          try {
            const cached = await leerPaginaInventario(cuentaId, query.sort, query.page);
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
              setError('No hay datos descargados para esta página del inventario.');
            }
            return;
          } catch (cacheError) {
            console.warn('No se pudo leer el inventario guardado.', cacheError);
          }
        }

        if (
          mode === 'seller' &&
          cuentaId &&
          cause instanceof ApiError &&
          (cause.status === 401 || cause.status === 403)
        ) {
          try {
            await borrarInventarioGuardado(cuentaId);
          } catch (cacheError) {
            console.warn('No se pudo borrar el inventario guardado.', cacheError);
          }
        }

        if (!isCurrent()) return;
        const message = await productErrorMessage(cause);
        if (isCurrent()) setError(message);
      } finally {
        if (isCurrent()) setBusy(false);
      }
    }
    void load();
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') void load();
    });
    return () => { active = false; subscription.remove(); };
  }, [mode, businessId, query]));

  return {
    data, busy, error, offlineUpdatedAt, page: query.page, sort: query.sort,
    setPage: (page: number) => setQuery((current) => ({ ...current, page })),
    refresh: () => setQuery((current) => ({ ...current })),
    changeSort: (sort: OrdenProductos) => setQuery({ page: 0, sort }),
  };
}
