import { guardarLectura, leerLectura } from './cache-lectura-storage';

import type {
  EmprendimientoListado,
  EmprendimientosFilter,
  EmprendimientosPage,
} from './emprendimientos';

const CACHE_KEY = 'emprendimientos-listado';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isListingItem(value: unknown): value is EmprendimientoListado {
  if (!isRecord(value) || !isRecord(value.ubicacion)) {
    return false;
  }
  const location = value.ubicacion;
  return (
    typeof value.id === 'string' &&
    typeof value.nombre === 'string' &&
    typeof value.categoria === 'string' &&
    typeof value.tipoVendedor === 'string' &&
    typeof value.abierto === 'boolean' &&
    typeof value.horaApertura === 'string' &&
    typeof value.horaCierre === 'string' &&
    (value.distanciaMetros === null ||
      typeof value.distanciaMetros === 'number') &&
    typeof location.latitud === 'number' &&
    typeof location.longitud === 'number' &&
    (typeof location.direccion === 'string' || location.direccion === null)
  );
}

function isEmprendimientosPage(value: unknown): value is EmprendimientosPage {
  return (
    isRecord(value) &&
    Array.isArray(value.content) &&
    Number.isInteger(value.page) &&
    Number.isInteger(value.size) &&
    typeof value.totalElements === 'number' &&
    value.content.every(isListingItem)
  );
}

function cacheKey(filter: EmprendimientosFilter): string {
  const order = filter.cercania ? 'CERCANIA' : 'RECIENTES';
  return `${CACHE_KEY}:${filter.tipoVendedor ?? 'TODOS'}:${order}:${filter.radioMetros ?? 'SIN_LIMITE'}:${filter.page}`;
}

export async function saveEmprendimientosPage(
  filter: EmprendimientosFilter,
  page: EmprendimientosPage,
): Promise<void> {
  const content = page.content.map((item) => ({
    ...item,
    distanciaMetros: null,
  }));
  await guardarLectura(cacheKey(filter), JSON.stringify({ ...page, content }));
}

export async function readEmprendimientosPage(
  filter: EmprendimientosFilter,
): Promise<{ page: EmprendimientosPage; updatedAt: string } | null> {
  const cached = await leerLectura(cacheKey(filter));
  if (!cached) {
    return null;
  }
  try {
    const page: unknown = JSON.parse(cached.contenido);
    if (!isEmprendimientosPage(page)) {
      throw new Error('El listado guardado no tiene el formato esperado.');
    }
    return {
      page,
      updatedAt: cached.actualizado_at,
    };
  } catch (cause) {
    throw new Error('El listado guardado no es válido y no se puede mostrar.', {
      cause,
    });
  }
}
