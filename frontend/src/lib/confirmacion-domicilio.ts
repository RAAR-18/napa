import { ApiError, NetworkError } from './api';
import type { TransicionDomicilio } from './domicilios';

export type PasoDomicilio = 'confirmar-recogida' | 'confirmar-salida';

export const estadosConfirmacionDomicilio = {
  'confirmar-recogida': 'RECOGIDO',
  'confirmar-salida': 'EN_CAMINO',
} as const;

export function rutaConfirmacionDomicilio(id: string, paso: PasoDomicilio) {
  return `/api/v1/domicilios/${encodeURIComponent(id)}/${paso}`;
}

export function confirmacionDomicilio(path: string) {
  const coincidencia = /^\/api\/v1\/domicilios\/([^/]+)\/(confirmar-recogida|confirmar-salida)$/.exec(path);
  if (!coincidencia) return null;
  return { id: decodeURIComponent(coincidencia[1]), paso: coincidencia[2] as PasoDomicilio };
}

export function confirmacionReintentable(error: unknown) {
  return error instanceof NetworkError || (error instanceof ApiError &&
    (error.status >= 500 || error.status === 408 || error.status === 429 ||
      (error.status === 409 && error.code === 'IDEMPOTENCY_IN_PROGRESS')));
}

export function validarConfirmacionDomicilio(resultado: TransicionDomicilio, id: string, paso: PasoDomicilio) {
  if (resultado?.id !== id || resultado.estado !== estadosConfirmacionDomicilio[paso] ||
      !Number.isFinite(Date.parse(resultado.updatedAt))) {
    throw new ApiError('El servidor no devolvió la confirmación del domicilio.', 502);
  }
  return resultado;
}
