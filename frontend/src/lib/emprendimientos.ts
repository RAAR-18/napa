import * as Crypto from 'expo-crypto';

import { jsonHeaders, requestJson, requestVoid } from './api';
import { withValidSession } from './auth';

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
  input: CrearEmprendimientoInput,
): Promise<Emprendimiento> {
  const idempotencyKey = Crypto.randomUUID();
  return withValidSession((session) =>
    requestJson<Emprendimiento>('/api/v1/emprendimientos', {
      method: 'POST',
      headers: jsonHeaders({
        accessToken: session.accessToken,
        idempotencyKey,
      }),
      body: JSON.stringify(input),
    }),
  );
}

export async function listEmprendimientos(
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
  return withValidSession((session) =>
    requestJson<EmprendimientosPage>(
      `/api/v1/emprendimientos?${query.toString()}`,
      {
        method: 'GET',
        headers: jsonHeaders({ accessToken: session.accessToken }),
      },
    ),
  );
}

/** RF-29: campos editables; los que no se envían se conservan. */
export type EditarEmprendimientoInput = {
  descripcion?: string;
  horaApertura?: string;
  horaCierre?: string;
  ubicacion?: {
    latitud: number;
    longitud: number;
    direccion: string | null;
    referencia: string | null;
  };
};

/** RF-32: lo que ve el cliente antes de pedir. */
export type EmprendimientoDetalle = {
  id: string;
  nombre: string;
  categoria: CategoriaEmprendimiento;
  tipoVendedor: TipoVendedor;
  descripcion: string;
  horaApertura: string;
  horaCierre: string;
  abierto: boolean;
  ubicacion: { latitud: number; longitud: number; direccion: string | null; referencia: string | null };
  modalidadesEntrega: ('ENTREGA_DIRECTA' | 'RECOGIDA' | 'DOMICILIO')[];
  ofreceDomicilio: boolean;
};

/** RF-34: solo datos públicos; nunca celular ni correo. */
export type VendedorPublico = {
  nombre: string;
  fotoUrl: string | null;
  tipoVendedor: TipoVendedor;
  miembroDesde: string | null;
};

export const categoriaNombres: Record<CategoriaEmprendimiento, string> = {
  FRUTAS_VERDURAS: 'Frutas y verduras',
  PESCADO_MARISCOS: 'Pescado y mariscos',
  ALIMENTOS_PREPARADOS: 'Alimentos preparados',
  BEBIDAS: 'Bebidas',
  SNACKS: 'Snacks',
  OTROS: 'Otros',
};

export function obtenerMiEmprendimiento() {
  return withValidSession((session) =>
    requestJson<Emprendimiento>('/api/v1/emprendimientos/me', {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}

export function editarMiEmprendimiento(input: EditarEmprendimientoInput) {
  return withValidSession((session) =>
    requestJson<Emprendimiento>('/api/v1/emprendimientos/me', {
      method: 'PATCH',
      headers: jsonHeaders({ accessToken: session.accessToken }),
      body: JSON.stringify(input),
    }),
  );
}

export function eliminarMiEmprendimiento(idempotencyKey: string) {
  return withValidSession((session) =>
    requestVoid('/api/v1/emprendimientos/me/eliminar', {
      method: 'POST',
      headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
      body: JSON.stringify({ confirmar: true }),
    }),
  );
}

export function obtenerEmprendimiento(emprendimientoId: string) {
  return withValidSession((session) =>
    requestJson<EmprendimientoDetalle>(`/api/v1/emprendimientos/${encodeURIComponent(emprendimientoId)}`, {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}

export function obtenerVendedorEmprendimiento(emprendimientoId: string) {
  return withValidSession((session) =>
    requestJson<VendedorPublico>(`/api/v1/emprendimientos/${encodeURIComponent(emprendimientoId)}/vendedor`, {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}
