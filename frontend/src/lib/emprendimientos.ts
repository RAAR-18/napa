import * as Crypto from 'expo-crypto';

import { jsonHeaders, requestJson } from './api';
import type { StoredSession } from './auth';

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
