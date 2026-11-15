import { jsonHeaders, requestJson } from './api';
import { withValidSession } from './auth';

export type MotivoReporte =
  | 'PEDIDO'
  | 'DOMICILIO'
  | 'PAGO'
  | 'COMPORTAMIENTO_USUARIO'
  | 'PLATAFORMA'
  | 'OTRO';
export type EstadoReporte = 'PENDIENTE' | 'EN_REVISION' | 'RESUELTO';
export type TipoEntidadReportada = 'PEDIDO' | 'DOMICILIO' | 'USUARIO';

export const motivoReporteNombres: Record<MotivoReporte, string> = {
  PEDIDO: 'Problema con un pedido',
  DOMICILIO: 'Problema con un domicilio',
  PAGO: 'Problema con un pago',
  COMPORTAMIENTO_USUARIO: 'Comportamiento de un usuario',
  PLATAFORMA: 'Falla de la aplicación',
  OTRO: 'Otro',
};

/** Estos motivos exigen relacionar un pedido, un domicilio o un usuario (RF-60). */
export const MOTIVOS_CON_ENTIDAD: MotivoReporte[] = ['PEDIDO', 'DOMICILIO', 'PAGO', 'COMPORTAMIENTO_USUARIO'];

export const estadoReporteNombres: Record<EstadoReporte, string> = {
  PENDIENTE: 'Pendiente',
  EN_REVISION: 'En revisión',
  RESUELTO: 'Resuelto',
};

export const DESCRIPCION_MINIMA = 10;
export const DESCRIPCION_MAXIMA = 500;

export type EntidadReporte = { pedidoId?: string; domicilioId?: string; usuarioId?: string };

export type ReporteCreado = {
  id: string;
  estado: EstadoReporte;
  motivo: MotivoReporte;
  descripcion: string;
  createdAt: string;
};

export type Persona = { id: string; nombre: string | null; rol: string | null };

export type ReporteItem = {
  id: string;
  estado: EstadoReporte;
  motivo: MotivoReporte;
  autor: Persona;
  entidad: { tipo: TipoEntidadReportada; id: string } | null;
  createdAt: string;
};

export type ReportesPagina = { content: ReporteItem[]; page: number; size: number; totalElements: number };

export type ReporteDetalle = {
  id: string;
  estado: EstadoReporte;
  motivo: MotivoReporte;
  descripcion: string;
  autor: Persona;
  createdAt: string;
  updatedAt: string;
  notaResolucion: string | null;
  resueltoPor: Persona | null;
  entidad: {
    tipo: TipoEntidadReportada;
    id: string;
    disponible: boolean;
    resumen: Record<string, unknown> | null;
  } | null;
};

/** RF-60: queda PENDIENTE y los administradores reciben un aviso. */
export function crearReporte(
  input: { motivo: MotivoReporte; descripcion: string } & EntidadReporte,
  idempotencyKey: string,
) {
  return withValidSession((session) =>
    requestJson<ReporteCreado>('/api/v1/reportes', {
      method: 'POST',
      headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
      body: JSON.stringify(cuerpoReporte(input)),
    }),
  );
}

/** El mismo cuerpo en línea y en la cola sin conexión (F041). */
export function cuerpoReporte(input: { motivo: MotivoReporte; descripcion: string } & EntidadReporte) {
  return { ...input, descripcion: input.descripcion.trim() };
}

/** El administrador ve todos (RF-61); cualquier otro rol, solo los suyos. */
export function listarReportes(
  filtros: { estado?: EstadoReporte; page?: number; size?: number; sort?: 'createdAt,asc' | 'createdAt,desc' } = {},
) {
  const params = new URLSearchParams();
  Object.entries(filtros).forEach(([name, value]) => {
    if (value !== undefined) params.set(name, String(value));
  });
  const text = params.toString();
  return withValidSession((session) =>
    requestJson<ReportesPagina>(`/api/v1/reportes${text ? `?${text}` : ''}`, {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}

/** RF-61. */
export function obtenerReporte(id: string) {
  return withValidSession((session) =>
    requestJson<ReporteDetalle>(`/api/v1/reportes/${encodeURIComponent(id)}`, {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}

/** RF-62: la nota (5 a 1000 caracteres) cierra el reporte y avisa a su autor. */
export function resolverReporte(id: string, nota: string, idempotencyKey: string) {
  return withValidSession((session) =>
    requestJson<{ id: string; estado: EstadoReporte; notaResolucion: string }>(
      `/api/v1/reportes/${encodeURIComponent(id)}/resolver`,
      {
        method: 'POST',
        headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
        body: JSON.stringify({ nota: nota.trim() }),
      },
    ),
  );
}
