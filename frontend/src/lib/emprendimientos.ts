import * as Crypto from 'expo-crypto';

import { jsonHeaders, requestJson } from './api';
import type { StoredSession } from './auth';

export type TipoVendedor = 'AMBULANTE' | 'PUNTO_FIJO';

export type CategoriaEmprendimiento =
  | 'FRUTAS_VERDURAS'
  | 'PESCADO_MARISCOS'
  | 'ALIMENTOS_PREPARADOS'
  | 'BEBIDAS'
  | 'SNACKS'
  | 'OTROS';

export type CrearEmprendimientoInput = {
  nombre: string;
  categoria: CategoriaEmprendimiento;
  descripcion: string;
  horaApertura: string;
  horaCierre: string;
  ubicacion: {
    latitud: number;
    longitud: number;
    direccion: string | null;
    referencia: string | null;
  };
};

export type Emprendimiento = CrearEmprendimientoInput & {
  id: string;
  tipoVendedor: string;
  estado: string;
};

export type EmprendimientoListado = {
  id: string;
  nombre: string;
  categoria: CategoriaEmprendimiento;
  tipoVendedor: TipoVendedor;
  abierto: boolean;
  horaApertura: string;
  horaCierre: string;
  distanciaMetros: number | null;
  ubicacion: {
    latitud: number;
    longitud: number;
    direccion: string | null;
  };
};

export type EmprendimientosPage = {
  content: EmprendimientoListado[];
  page: number;
  size: number;
  totalElements: number;
};

export type EmprendimientosFilter = {
  page: number;
  size: number;
  tipoVendedor?: TipoVendedor;
  lat?: number;
  lng?: number;
  radioMetros?: number;
  cercania?: boolean;
};

export async function createEmprendimiento(
  session: StoredSession,
  input: CrearEmprendimientoInput,
): Promise<Emprendimiento> {
  return requestJson<Emprendimiento>('/api/v1/emprendimientos', {
    method: 'POST',
    headers: jsonHeaders({
      accessToken: session.accessToken,
      idempotencyKey: Crypto.randomUUID(),
    }),
    body: JSON.stringify(input),
  });
}

export async function listEmprendimientos(
  session: StoredSession,
  filter: EmprendimientosFilter,
): Promise<EmprendimientosPage> {
  const query = new URLSearchParams({
    page: String(filter.page),
    size: String(filter.size),
  });
  if (filter.tipoVendedor) {
    query.set('tipoVendedor', filter.tipoVendedor);
  }
  if (filter.lat !== undefined && filter.lng !== undefined) {
    query.set('lat', String(filter.lat));
    query.set('lng', String(filter.lng));
  }
  if (filter.radioMetros !== undefined) {
    query.set('radioMetros', String(filter.radioMetros));
  }
  if (filter.cercania) {
    query.set('orden', 'cercania');
  }
  return requestJson<EmprendimientosPage>(
    `/api/v1/emprendimientos?${query.toString()}`,
    {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    },
  );
}
