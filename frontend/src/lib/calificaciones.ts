import { jsonHeaders, requestJson } from './api';
import { withValidSession } from './auth';

export type TipoEntidadCalificada = 'EMPRENDIMIENTO' | 'PRODUCTO' | 'USUARIO';
export type EstadoCalificacion = 'ACTIVA' | 'ELIMINADA_AUTOR' | 'ELIMINADA_MODERACION';

export const COMENTARIO_MAXIMO = 300;

export const estadoCalificacionNombres: Record<EstadoCalificacion, string> = {
  ACTIVA: 'Visible',
  ELIMINADA_AUTOR: 'Eliminada por su autor',
  ELIMINADA_MODERACION: 'Eliminada por moderación',
};

/** Lo que el usuario puede calificar en un pedido entregado; `calificacionId` indica que ya lo calificó. */
export type OpcionCalificacion = {
  tipoEntidad: TipoEntidadCalificada;
  entidadId: string;
  nombre: string | null;
  rol: 'CLIENTE' | 'DOMICILIARIO' | null;
  calificacionId: string | null;
};

export type OpcionesCalificacion = {
  pedidoId: string;
  estadoPedido: string;
  opciones: OpcionCalificacion[];
};

export type CalificacionCreada = {
  id: string;
  pedidoId: string;
  tipoEntidad: TipoEntidadCalificada;
  entidadId: string;
  puntaje: number;
  comentario: string | null;
  estado: EstadoCalificacion;
  createdAt: string;
};

export type ResumenCalificaciones = { promedio: number | null; total: number };

export type CalificacionPublica = {
  id: string;
  puntaje: number;
  comentario: string | null;
  autorNombre: string | null;
  esMia: boolean;
  createdAt: string;
};

export type CalificacionSupervisada = {
  id: string;
  puntaje: number;
  comentario: string | null;
  estado: EstadoCalificacion;
  moderada: boolean;
  autor: { id: string; nombre: string | null; rol: string | null };
  entidad: { tipo: TipoEntidadCalificada; id: string; nombre: string | null };
  pedidoId: string;
  createdAt: string;
  updatedAt: string;
};

export type PaginaCalificaciones<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  resumen: ResumenCalificaciones | null;
};

export type FiltrosCalificaciones = {
  tipoEntidad?: TipoEntidadCalificada;
  entidadId?: string;
  estado?: EstadoCalificacion;
  page?: number;
  size?: number;
  sort?: 'createdAt,desc' | 'createdAt,asc' | 'puntaje,desc' | 'puntaje,asc';
};

function query(filtros: Record<string, string | number | undefined>) {
  const params = new URLSearchParams();
  Object.entries(filtros).forEach(([name, value]) => {
    if (value !== undefined && value !== '') params.set(name, String(value));
  });
  const text = params.toString();
  return text ? `?${text}` : '';
}

/** RF-01: a quién puede calificar el usuario. El domiciliario entra por su domicilio. */
export function obtenerOpcionesCalificacion(ref: { pedidoId?: string; domicilioId?: string }) {
  return withValidSession((session) =>
    requestJson<OpcionesCalificacion>(`/api/v1/calificaciones/opciones${query(ref)}`, {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}

/** RF-01: puntaje de 1 a 5 y comentario opcional de hasta 300 caracteres. */
export function calificar(
  input: {
    pedidoId: string;
    tipoEntidad: TipoEntidadCalificada;
    entidadId: string;
    puntaje: number;
    comentario?: string | null;
  },
  idempotencyKey: string,
) {
  return withValidSession((session) =>
    requestJson<CalificacionCreada>('/api/v1/calificaciones', {
      method: 'POST',
      headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
      body: JSON.stringify(cuerpoCalificacion(input)),
    }),
  );
}

/** El mismo cuerpo en línea y en la cola sin conexión (F041), para que la clave de idempotencia coincida. */
export function cuerpoCalificacion(input: {
  pedidoId: string;
  tipoEntidad: TipoEntidadCalificada;
  entidadId: string;
  puntaje: number;
  comentario?: string | null;
}) {
  const comentario = input.comentario?.trim();
  return { ...input, comentario: comentario ? comentario : undefined };
}

/** RF-02 (participantes) o RF-03 (administrador): la API decide la rama por el rol de la sesión. */
export function listarCalificaciones<T = CalificacionPublica>(filtros: FiltrosCalificaciones) {
  return withValidSession((session) =>
    requestJson<PaginaCalificaciones<T>>(`/api/v1/calificaciones${query(filtros)}`, {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}

/** RF-04: un campo ausente no cambia. */
export function editarCalificacion(id: string, cambios: { puntaje?: number; comentario?: string }) {
  return withValidSession((session) =>
    requestJson<{ id: string; puntaje: number; comentario: string | null; estado: EstadoCalificacion }>(
      `/api/v1/calificaciones/${encodeURIComponent(id)}`,
      {
        method: 'PATCH',
        headers: jsonHeaders({ accessToken: session.accessToken }),
        body: JSON.stringify(cambios),
      },
    ),
  );
}

/** RF-05. */
export function eliminarCalificacion(id: string, idempotencyKey: string) {
  return withValidSession((session) =>
    requestJson<{ id: string; estado: EstadoCalificacion }>(
      `/api/v1/calificaciones/${encodeURIComponent(id)}/eliminar`,
      { method: 'POST', headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }) },
    ),
  );
}

/** RF-06: el motivo (5 a 200 caracteres) se le envía al autor. */
export function eliminarPorModeracion(id: string, motivo: string, idempotencyKey: string) {
  return withValidSession((session) =>
    requestJson<{ id: string; estado: EstadoCalificacion }>(
      `/api/v1/calificaciones/${encodeURIComponent(id)}/eliminar-moderacion`,
      {
        method: 'POST',
        headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
        body: JSON.stringify({ motivo: motivo.trim() }),
      },
    ),
  );
}

/** RF-06: solo cambia el texto; el puntaje no. */
export function moderarTexto(id: string, comentario: string, motivo: string) {
  return withValidSession((session) =>
    requestJson<{ id: string; estado: EstadoCalificacion; comentario: string }>(
      `/api/v1/calificaciones/${encodeURIComponent(id)}/moderar-texto`,
      {
        method: 'PATCH',
        headers: jsonHeaders({ accessToken: session.accessToken }),
        body: JSON.stringify({ comentario: comentario.trim(), motivo: motivo.trim() }),
      },
    ),
  );
}

export function textoPromedio(resumen: ResumenCalificaciones | null | undefined) {
  if (!resumen || resumen.promedio === null || resumen.total === 0) return 'Aún sin calificaciones';
  const total = resumen.total === 1 ? '1 calificación' : `${resumen.total} calificaciones`;
  return `${resumen.promedio.toLocaleString('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} de 5 · ${total}`;
}

export function estrellas(puntaje: number) {
  return '★'.repeat(puntaje) + '☆'.repeat(Math.max(0, 5 - puntaje));
}

/** RF-02 en listados: hasta 50 productos o emprendimientos por petición; el vendedor solo recibe los suyos. */
export async function obtenerResumenes(
  tipo: 'PRODUCTO' | 'EMPRENDIMIENTO',
  ids: string[],
): Promise<Record<string, ResumenCalificaciones>> {
  const unicos = [...new Set(ids)].slice(0, 50);
  if (unicos.length === 0) return {};
  const respuesta = await withValidSession((session) =>
    requestJson<{ resumenes: (ResumenCalificaciones & { entidadId: string })[] }>(
      `/api/v1/calificaciones/resumenes${query({ tipoEntidad: tipo, ids: unicos.join(',') })}`,
      { method: 'GET', headers: jsonHeaders({ accessToken: session.accessToken }) },
    ),
  );
  return Object.fromEntries(
    respuesta.resumenes.map((r) => [r.entidadId, { promedio: r.promedio, total: r.total }]),
  );
}
