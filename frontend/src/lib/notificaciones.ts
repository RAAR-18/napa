import { jsonHeaders, requestJson, requestVoid } from './api';
import { withValidSession } from './auth';

/** Los tipos son los `eventType` del catálogo de eventos (RF-38 FR-004). */
export type TipoNotificacion =
  | 'pedidos.pedido-creado'
  | 'pedidos.pedido-aceptado'
  | 'pedidos.pedido-rechazado'
  | 'pagos.pago-pedido-confirmado'
  | 'entregas.entrega-directa-iniciada'
  | 'pedidos.pedido-listo-para-recoger'
  | 'pedidos.pedido-entregado'
  | 'domicilios.domicilio-publicado'
  | 'domicilios.domicilio-asignado'
  | 'domicilios.domicilio-recogido'
  | 'domicilios.domicilio-en-camino'
  | 'domicilios.domicilio-entregado'
  | 'pagos.pago-tarifa-registrado'
  | 'domicilios.domicilio-cancelado'
  | 'domicilios.domicilio-sin-asignar'
  | 'reportes.reporte-resuelto'
  | 'calificaciones.comentario-moderado'
  | 'reportes.reporte-creado';

export type Rol = 'CLIENTE' | 'VENDEDOR_AMBULANTE' | 'VENDEDOR_PUNTO_FIJO' | 'DOMICILIARIO' | 'ADMINISTRADOR';

type DescripcionTipo = { nombre: string; roles: Rol[] };

const VENDEDORES: Rol[] = ['VENDEDOR_AMBULANTE', 'VENDEDOR_PUNTO_FIJO'];
const TODOS: Rol[] = ['CLIENTE', 'VENDEDOR_AMBULANTE', 'VENDEDOR_PUNTO_FIJO', 'DOMICILIARIO'];

/** Nombre de cada tipo en la pantalla de preferencias y qué roles lo reciben. */
export const tiposNotificacion: Record<TipoNotificacion, DescripcionTipo> = {
  'pedidos.pedido-creado': { nombre: 'Nuevo pedido recibido', roles: VENDEDORES },
  'pedidos.pedido-aceptado': { nombre: 'Tu pedido fue aceptado', roles: ['CLIENTE'] },
  'pedidos.pedido-rechazado': { nombre: 'Tu pedido fue rechazado', roles: ['CLIENTE'] },
  'pagos.pago-pedido-confirmado': { nombre: 'El vendedor confirmó tu pago', roles: ['CLIENTE'] },
  'entregas.entrega-directa-iniciada': { nombre: 'El vendedor salió a entregarte', roles: ['CLIENTE'] },
  'pedidos.pedido-listo-para-recoger': { nombre: 'Tu pedido está listo para recoger', roles: ['CLIENTE'] },
  'pedidos.pedido-entregado': { nombre: 'Tu pedido fue entregado', roles: ['CLIENTE'] },
  'domicilios.domicilio-publicado': { nombre: 'Nuevos domicilios disponibles', roles: ['DOMICILIARIO'] },
  'domicilios.domicilio-asignado': {
    nombre: 'Un domiciliario aceptó el domicilio',
    roles: ['CLIENTE', 'VENDEDOR_PUNTO_FIJO'],
  },
  'domicilios.domicilio-recogido': { nombre: 'El domiciliario recogió el pedido', roles: ['VENDEDOR_PUNTO_FIJO'] },
  'domicilios.domicilio-en-camino': { nombre: 'Tu domicilio va en camino', roles: ['CLIENTE'] },
  'domicilios.domicilio-entregado': {
    nombre: 'El domicilio fue entregado',
    roles: ['CLIENTE', 'VENDEDOR_PUNTO_FIJO'],
  },
  'pagos.pago-tarifa-registrado': { nombre: 'Te pagaron la tarifa de un domicilio', roles: ['DOMICILIARIO'] },
  'domicilios.domicilio-cancelado': {
    nombre: 'Un domicilio fue cancelado',
    roles: ['CLIENTE', 'VENDEDOR_PUNTO_FIJO', 'DOMICILIARIO'],
  },
  'domicilios.domicilio-sin-asignar': {
    nombre: 'Un domicilio sigue sin domiciliario',
    roles: ['CLIENTE', 'VENDEDOR_PUNTO_FIJO'],
  },
  'reportes.reporte-resuelto': { nombre: 'Se resolvió tu reporte', roles: TODOS },
  'calificaciones.comentario-moderado': { nombre: 'Se moderó uno de tus comentarios', roles: TODOS },
  'reportes.reporte-creado': { nombre: 'Nuevo reporte pendiente', roles: ['ADMINISTRADOR'] },
};

export type Notificacion = {
  id: string;
  tipo: TipoNotificacion | string;
  titulo: string;
  cuerpo: string;
  leida: boolean;
  leidaAt: string | null;
  createdAt: string;
  /** Qué abre: el pedido (cliente y vendedor) o el domicilio (domiciliario). */
  referencia: { tipo: 'PEDIDO' | 'DOMICILIO'; id: string } | null;
};

export type NotificacionesPagina = {
  content: Notificacion[];
  page: number;
  size: number;
  totalElements: number;
};

export type PreferenciaNotificacion = { tipo: TipoNotificacion | string; habilitada: boolean };

/** RF-38: ordenadas de la más reciente a la más antigua. */
export function listarNotificaciones(filters: { leida?: boolean; page?: number; size?: number } = {}) {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([name, value]) => {
    if (value !== undefined) query.set(name, String(value));
  });
  const queryString = query.toString();
  return withValidSession((session) =>
    requestJson<NotificacionesPagina>(`/api/v1/notificaciones${queryString ? `?${queryString}` : ''}`, {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}

/** RF-38 FR-005: abrirla la marca como leída. */
export function abrirNotificacion(id: string) {
  return withValidSession((session) =>
    requestJson<Notificacion>(`/api/v1/notificaciones/${encodeURIComponent(id)}`, {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}

export async function contarNoLeidas() {
  const response = await withValidSession((session) =>
    requestJson<{ noLeidas: number }>('/api/v1/notificaciones/no-leidas', {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
  return response.noLeidas;
}

/** RF-39. */
export async function listarPreferencias() {
  const response = await withValidSession((session) =>
    requestJson<{ preferencias: PreferenciaNotificacion[] }>('/api/v1/notificaciones/preferencias', {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
  return response.preferencias;
}

/** RF-39: solo cambian los tipos enviados; los demás conservan su estado. */
export async function actualizarPreferencias(preferencias: PreferenciaNotificacion[]) {
  const response = await withValidSession((session) =>
    requestJson<{ preferencias: PreferenciaNotificacion[] }>('/api/v1/notificaciones/preferencias', {
      method: 'PATCH',
      headers: jsonHeaders({ accessToken: session.accessToken }),
      body: JSON.stringify({ preferencias }),
    }),
  );
  return response.preferencias;
}

export function registrarDispositivo(token: string, plataforma: 'ANDROID' | 'IOS') {
  return withValidSession((session) =>
    requestVoid('/api/v1/dispositivos', {
      method: 'POST',
      headers: jsonHeaders({ accessToken: session.accessToken }),
      body: JSON.stringify({ token, plataforma }),
    }),
  );
}

export function darDeBajaDispositivo(token: string) {
  return withValidSession((session) =>
    requestVoid('/api/v1/dispositivos', {
      method: 'DELETE',
      headers: jsonHeaders({ accessToken: session.accessToken }),
      body: JSON.stringify({ token }),
    }),
  );
}

/** A dónde lleva tocar la notificación, según su referencia y el rol de quien la abre. */
export function destinoNotificacion(
  notificacion: Pick<Notificacion, 'referencia'>,
  rol: string,
): { pathname: string; params: Record<string, string> } | null {
  const referencia = notificacion.referencia;
  if (!referencia) return null;
  if (referencia.tipo === 'DOMICILIO') {
    return rol === 'DOMICILIARIO'
      ? { pathname: '/domicilio-domiciliario', params: { domicilioId: referencia.id } }
      : null;
  }
  if (rol === 'CLIENTE') return { pathname: '/pedido-cliente', params: { pedidoId: referencia.id } };
  if (rol === 'VENDEDOR_AMBULANTE' || rol === 'VENDEDOR_PUNTO_FIJO') {
    return { pathname: '/pedido-vendedor', params: { pedidoId: referencia.id } };
  }
  return null;
}
