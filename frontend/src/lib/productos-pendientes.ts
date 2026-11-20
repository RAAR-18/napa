import type { AccionPendiente } from '../database/outbox/types';
import type { CrearProductoInput } from './productos';

export const RUTA_REGISTRO_PRODUCTO = '/api/v1/productos';

type RegistroVinculado = CrearProductoInput & { emprendimientoId: string };

export function productoPendiente(accion: AccionPendiente): RegistroVinculado | null {
  if (accion.method !== 'POST' || accion.path !== RUTA_REGISTRO_PRODUCTO || !accion.body) return null;
  try {
    const data = JSON.parse(accion.body) as RegistroVinculado | null;
    return data && typeof data.emprendimientoId === 'string' && data.emprendimientoId.length > 0
      ? data
      : null;
  } catch {
    return null;
  }
}
