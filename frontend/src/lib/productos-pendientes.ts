import type { AccionPendiente } from '../database/outbox/types';
import type { CrearProductoInput, EditarProductoInput } from './productos';

export const RUTA_REGISTRO_PRODUCTO = '/api/v1/productos';

export function rutaEdicionProducto(id: string): string {
  return `${RUTA_REGISTRO_PRODUCTO}/${encodeURIComponent(id)}`;
}

export function rutaAjusteInventario(id: string): string {
  return `${rutaEdicionProducto(id)}/inventario`;
}

export function cambioPendienteProducto(accion: AccionPendiente) {
  if (!accion.ifMatch || !accion.body) return null;
  const coincidencia = /^\/api\/v1\/productos\/([^/]+)(\/inventario)?$/.exec(accion.path);
  if (!coincidencia) return null;
  const inventario = Boolean(coincidencia[2]);
  if (accion.method !== (inventario ? 'POST' : 'PATCH')) return null;
  try {
    return {
      productoId: decodeURIComponent(coincidencia[1]), inventario,
      cambios: JSON.parse(accion.body) as EditarProductoInput & { cantidadDisponible?: number },
    };
  } catch {
    return null;
  }
}

export function edicionDescartableSinRevision(accion: AccionPendiente) {
  return accion.errorStatus === 403 || accion.errorStatus === 404 || accion.errorStatus === 410;
}

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
