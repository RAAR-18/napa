import type { AccionPendiente } from '../database/outbox/types';
import type { EditarEmprendimientoInput, Emprendimiento } from './emprendimientos';

export const RUTA_EDICION_EMPRENDIMIENTO = '/api/v1/emprendimientos/me';

type CambiosBasicos = Omit<EditarEmprendimientoInput, 'ubicacion'>;
type EdicionConBase = CambiosBasicos & {
  base: { emprendimientoId: string; descripcion: string; horaApertura: string; horaCierre: string };
};

export function edicionConBase(cambios: CambiosBasicos, actual: Emprendimiento): EdicionConBase {
  return {
    ...cambios,
    base: {
      emprendimientoId: actual.id, descripcion: actual.descripcion,
      horaApertura: actual.horaApertura, horaCierre: actual.horaCierre,
    },
  };
}

export function edicionEmprendimientoPendiente(accion: AccionPendiente): EdicionConBase | null {
  if (accion.method !== 'PATCH' || accion.path !== RUTA_EDICION_EMPRENDIMIENTO || !accion.body) return null;
  try {
    const datos = JSON.parse(accion.body) as EdicionConBase | null;
    return typeof datos?.base?.emprendimientoId === 'string' ? datos : null;
  } catch {
    return null;
  }
}
