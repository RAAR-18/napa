import { useCallback, useState } from 'react';
import { useFocusEffect } from 'expo-router';
import { AppState } from 'react-native';

import { getValidSession } from '../lib/auth';
import {
  listCatalogo, listMisProductos, productErrorMessage,
  type OrdenProductos, type ProductosPage,
} from '../lib/productos';

export function useProductosPage(mode: 'seller' | 'client', businessId?: string) {
  const [data, setData] = useState<ProductosPage | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState<{ page: number; sort: OrdenProductos }>({ page: 0, sort: 'nombre,asc' });

  useFocusEffect(useCallback(() => {
    let active = true;
    let generation = 0;
    async function load() {
      const requestGeneration = ++generation;
      setBusy(true);
      setError(null);
      setData(null);
      try {
        const session = await getValidSession();
        const seller = session.rol === 'VENDEDOR_AMBULANTE' || session.rol === 'VENDEDOR_PUNTO_FIJO';
        if (mode === 'seller' ? !seller : session.rol !== 'CLIENTE') {
          throw new Error(mode === 'seller'
            ? 'Solo los vendedores pueden gestionar productos.'
            : 'Ingresa como cliente para consultar el catálogo.');
        }
        if (!active || requestGeneration !== generation) return;
        if (mode === 'client' && !businessId) throw new Error('Selecciona un emprendimiento para ver sus productos.');
        const result = mode === 'seller'
          ? await listMisProductos(query.page, query.sort)
          : await listCatalogo(businessId!, query.page, query.sort);
        if (active && requestGeneration === generation) {
          const lastPage = Math.max(0, Math.ceil(result.totalElements / result.size) - 1);
          if (query.page > lastPage) {
            setQuery((current) => ({ ...current, page: lastPage }));
          } else {
            setData(result);
          }
        }
      } catch (cause) {
        if (active && requestGeneration === generation) {
          const message = await productErrorMessage(cause);
          if (active && requestGeneration === generation) setError(message);
        }
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
    data, busy, error, page: query.page, sort: query.sort,
    setPage: (page: number) => setQuery((current) => ({ ...current, page })),
    refresh: () => setQuery((current) => ({ ...current })),
    changeSort: (sort: OrdenProductos) => setQuery({ page: 0, sort }),
  };
}
