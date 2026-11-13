import { jsonHeaders, requestJson, requestVoid } from './api';
import { withValidSession } from './auth';

export type EstadoDomicilio =
  | 'DISPONIBLE'
  | 'ASIGNADO'
  | 'RECOGIDO'
  | 'EN_CAMINO'
  | 'ENTREGADO'
  | 'CANCELADO';

export type MotivoCancelacionDomicilio = 'SIN_ASIGNAR' | 'ADMINISTRADOR';

export const estadoDomicilioNombres: Record<EstadoDomicilio, string> = {
  DISPONIBLE: 'Disponible',
  ASIGNADO: 'Asignado',
  RECOGIDO: 'Recogido',
  EN_CAMINO: 'En camino',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
};

export const motivoCancelacionNombres: Record<MotivoCancelacionDomicilio, string> = {
  SIN_ASIGNAR: 'Ningún domiciliario lo tomó a tiempo',
  ADMINISTRADOR: 'Lo canceló el administrador',
};

/** Antelación con la que se puede publicar el domicilio de una reserva (RF-14 FR-008). */
export const MARGEN_PUBLICACION_RESERVA_MS = 15 * 60 * 1000;

export type PuntoDomicilio = {
  latitud: number;
  longitud: number;
  direccion: string | null;
  referencia: string | null;
  barrio?: string | null;
};

export type DomicilioPublicado = {
  id: string;
  pedidoId: string;
  estado: 'DISPONIBLE';
  ganancia: number;
  createdAt: string;
  origen: PuntoDomicilio;
  destino: PuntoDomicilio;
};

export type DomicilioVendedorItem = {
  id: string;
  pedidoId: string;
  estado: EstadoDomicilio;
  tarifa: number;
  domiciliario: { nombre: string | null } | null;
  motivoCancelacion: MotivoCancelacionDomicilio | null;
  createdAt: string;
  updatedAt: string;
};

export type DomiciliosVendedorPagina = {
  content: DomicilioVendedorItem[];
  page: number;
  size: number;
  totalElements: number;
};

export type FiltrosDomiciliosVendedor = {
  estado?: EstadoDomicilio;
  /** Fecha `yyyy-MM-dd` (inclusive). */
  desde?: string;
  /** Fecha `yyyy-MM-dd` (inclusive). */
  hasta?: string;
  page?: number;
  size?: number;
  sort?: 'createdAt,asc' | 'createdAt,desc';
};

/** RF-14: publica el domicilio de un pedido aceptado y con el pago confirmado. Requiere conexión. */
export function publicarDomicilio(pedidoId: string, idempotencyKey: string) {
  return withValidSession((session) =>
    requestJson<DomicilioPublicado>(`/api/v1/pedidos/${encodeURIComponent(pedidoId)}/domicilio`, {
      method: 'POST',
      headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
    }),
  );
}

/** RF-15: domicilios del vendedor de punto fijo, del más reciente al más antiguo. */
export function listarDomiciliosVendedor(filters: FiltrosDomiciliosVendedor = {}) {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([name, value]) => {
    if (value !== undefined && value !== '') query.set(name, String(value));
  });
  const queryString = query.toString();
  const suffix = queryString ? `?${queryString}` : '';
  return withValidSession((session) =>
    requestJson<DomiciliosVendedorPagina>(`/api/v1/domicilios${suffix}`, {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}

/** Punto como lo ve el domiciliario: con `aproximado` no hay dirección y las coordenadas van redondeadas. */
export type PuntoDomiciliario = PuntoDomicilio & { aproximado: boolean };

export type OrdenDisponibles = 'recientes' | 'cercania' | 'ganancia';

export type DomicilioDisponible = {
  id: string;
  ganancia: number;
  distanciaMetros: number;
  distanciaAlOrigenMetros: number | null;
  createdAt: string;
  origen: PuntoDomiciliario;
  destino: PuntoDomiciliario;
};

export type DomiciliosDisponiblesPagina = {
  content: DomicilioDisponible[];
  page: number;
  size: number;
  totalElements: number;
  /** Solo se listan los domicilios cuyo origen está a esta distancia o menos del domiciliario. */
  radioMetros: number;
};

export type DomicilioDomiciliarioDetalle = {
  id: string;
  estado: EstadoDomicilio;
  ganancia: number;
  asignadoAMi: boolean;
  rutaDisponible: boolean;
  distanciaMetros: number;
  origen: PuntoDomiciliario;
  destino: PuntoDomiciliario;
  receptor: { nombre: string | null; celular: string | null };
  resumenItems: { cantidadItems: number };
  asignadoAt: string | null;
};

export type DomicilioAceptado = {
  id: string;
  estado: 'ASIGNADO';
  ganancia: number;
  rutaDisponible: boolean;
  updatedAt: string;
};

export type DomicilioEnCurso = {
  id: string;
  estado: EstadoDomicilio;
  ganancia: number;
  asignadoAt: string;
  origen: PuntoDomiciliario;
  destino: PuntoDomiciliario;
};

/** Máximo de domicilios en curso por domiciliario (RF-19 FR-006). */
export const MAXIMO_DOMICILIOS_EN_CURSO = 3;

export function formatearDistancia(metros: number) {
  return metros < 1000
    ? `${metros.toLocaleString('es-CO')} m`
    : `${(metros / 1000).toLocaleString('es-CO', { maximumFractionDigits: 1 })} km`;
}

/** RF-16. La posición es obligatoria: el servidor filtra por el radio alrededor del domiciliario. */
export function listarDomiciliosDisponibles(options: {
  orden?: OrdenDisponibles;
  posicion: { lat: number; lng: number };
  page?: number;
  size?: number;
}) {
  const query = new URLSearchParams();
  if (options.orden && options.orden !== 'recientes') query.set('orden', options.orden);
  query.set('lat', String(options.posicion.lat));
  query.set('lng', String(options.posicion.lng));
  if (options.page !== undefined) query.set('page', String(options.page));
  if (options.size !== undefined) query.set('size', String(options.size));
  const queryString = query.toString();
  return withValidSession((session) =>
    requestJson<DomiciliosDisponiblesPagina>(
      `/api/v1/domicilios/disponibles${queryString ? `?${queryString}` : ''}`,
      { method: 'GET', headers: jsonHeaders({ accessToken: session.accessToken }) },
    ),
  );
}

/** RF-18. */
export function obtenerDomicilioDomiciliario(domicilioId: string) {
  return withValidSession((session) =>
    requestJson<DomicilioDomiciliarioDetalle>(`/api/v1/domicilios/${encodeURIComponent(domicilioId)}`, {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}

/** RF-19. */
export function aceptarDomicilio(domicilioId: string, idempotencyKey: string) {
  return withValidSession((session) =>
    requestJson<DomicilioAceptado>(`/api/v1/domicilios/${encodeURIComponent(domicilioId)}/aceptar`, {
      method: 'POST',
      headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
    }),
  );
}

/** RF-17: definitivo, no se puede deshacer. */
export function rechazarDomicilio(domicilioId: string, idempotencyKey: string) {
  return withValidSession((session) =>
    requestVoid(`/api/v1/domicilios/${encodeURIComponent(domicilioId)}/rechazar`, {
      method: 'POST',
      headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
    }),
  );
}

export function listarDomiciliosEnCurso() {
  return withValidSession((session) =>
    requestJson<DomicilioEnCurso[]>('/api/v1/domicilios/en-curso', {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}

/** RF-23 y RF-24: respuesta de cada paso del domicilio. */
export type TransicionDomicilio = {
  id: string;
  estado: EstadoDomicilio;
  updatedAt: string;
};

function postTransicion(domicilioId: string, paso: string, idempotencyKey: string, body?: unknown) {
  return withValidSession((session) =>
    requestJson<TransicionDomicilio>(`/api/v1/domicilios/${encodeURIComponent(domicilioId)}/${paso}`, {
      method: 'POST',
      headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
      body: body === undefined ? undefined : JSON.stringify(body),
    }),
  );
}

/** RF-23: el domiciliario recogió el pedido en el punto A. */
export function confirmarRecogida(domicilioId: string, idempotencyKey: string) {
  return postTransicion(domicilioId, 'confirmar-recogida', idempotencyKey);
}

/** RF-23: el vendedor vio salir al domiciliario con el pedido. */
export function confirmarSalida(domicilioId: string, idempotencyKey: string) {
  return postTransicion(domicilioId, 'confirmar-salida', idempotencyKey);
}

/**
 * RF-24. Cada intento debe llevar una clave nueva: con la misma clave el servidor repite la respuesta anterior
 * sin volver a revisar el código.
 */
export function confirmarEntrega(domicilioId: string, codigo: string, idempotencyKey: string) {
  return postTransicion(domicilioId, 'confirmar-entrega', idempotencyKey, { codigo });
}

export const INTENTOS_CODIGO_ENTREGA = 5;

export type CodigoConfirmacion = {
  domicilioId: string;
  estado: EstadoDomicilio;
  codigo: string;
};

/** RF-25: solo el cliente del pedido. */
export function obtenerCodigoConfirmacion(domicilioId: string) {
  return withValidSession((session) =>
    requestJson<CodigoConfirmacion>(
      `/api/v1/domicilios/${encodeURIComponent(domicilioId)}/codigo-confirmacion`,
      { method: 'GET', headers: jsonHeaders({ accessToken: session.accessToken }) },
    ),
  );
}

export type TrazabilidadDomicilio = {
  domicilioId: string;
  estado: EstadoDomicilio;
  motivoCancelacion: MotivoCancelacionDomicilio | null;
  updatedAt: string;
  eventos: { estado: EstadoDomicilio; ocurridoAt: string }[];
  ubicacionDomiciliario: { latitud: number; longitud: number; actualizadaAt: string } | null;
};

/** RF-26: cliente o vendedor del pedido. */
export function obtenerTrazabilidad(domicilioId: string) {
  return withValidSession((session) =>
    requestJson<TrazabilidadDomicilio>(
      `/api/v1/domicilios/${encodeURIComponent(domicilioId)}/trazabilidad`,
      { method: 'GET', headers: jsonHeaders({ accessToken: session.accessToken }) },
    ),
  );
}

export type TramoRuta = {
  tipo: 'HACIA_ORIGEN' | 'HACIA_DESTINO';
  distanciaMetros: number;
  /** Nulo cuando el tramo es aproximado. */
  duracionSegundos: number | null;
  /** Polilínea codificada (precisión 5); nula cuando el servicio de rutas no respondió. */
  polilinea: string | null;
  aproximado: boolean;
};

export type RutaDomicilio = {
  domicilioId: string;
  estado: EstadoDomicilio;
  origen: PuntoDomiciliario;
  destino: PuntoDomiciliario;
  tramos: TramoRuta[];
};

/** RF-22: solo el domiciliario asignado. Sin posición, el servidor devuelve solo el tramo desde el punto A. */
export function obtenerRutaDomicilio(domicilioId: string, posicion?: { lat: number; lng: number } | null) {
  const query = posicion ? `?lat=${posicion.lat}&lng=${posicion.lng}` : '';
  return withValidSession((session) =>
    requestJson<RutaDomicilio>(`/api/v1/domicilios/${encodeURIComponent(domicilioId)}/ruta${query}`, {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}

export function formatearDuracion(segundos: number) {
  const minutos = Math.max(1, Math.round(segundos / 60));
  if (minutos < 60) return `${minutos} min`;
  const horas = Math.floor(minutos / 60);
  const resto = minutos % 60;
  return resto ? `${horas} h ${resto} min` : `${horas} h`;
}

/** Decodifica una polilínea en el formato de Google (la que devuelve OpenRouteService), precisión 5. */
export function decodificarPolilinea(codificada: string): { latitude: number; longitude: number }[] {
  const puntos: { latitude: number; longitude: number }[] = [];
  let indice = 0;
  let lat = 0;
  let lng = 0;
  const siguiente = () => {
    let resultado = 0;
    let desplazamiento = 0;
    let byte: number;
    do {
      if (indice >= codificada.length) throw new Error('Polilínea incompleta.');
      byte = codificada.charCodeAt(indice++) - 63;
      resultado |= (byte & 0x1f) << desplazamiento;
      desplazamiento += 5;
    } while (byte >= 0x20);
    return resultado & 1 ? ~(resultado >> 1) : resultado >> 1;
  };
  while (indice < codificada.length) {
    lat += siguiente();
    lng += siguiente();
    puntos.push({ latitude: lat / 1e5, longitude: lng / 1e5 });
  }
  return puntos;
}

/** RF-12: punto como lo ve el administrador. */
export type PuntoSupervision = {
  latitud: number;
  longitud: number;
  direccion: string | null;
  barrio: string | null;
};

export type PersonaDomicilio = { id: string; nombre: string | null };

export type DomicilioAdminFila = {
  id: string;
  pedidoId: string;
  estado: EstadoDomicilio;
  tarifa: number;
  /** Solo en los disponibles. */
  minutosSinTomar: number | null;
  origen: PuntoSupervision;
  destino: PuntoSupervision;
  vendedor: PersonaDomicilio;
  cliente: PersonaDomicilio;
  domiciliario: PersonaDomicilio | null;
  motivoCancelacion: MotivoCancelacionDomicilio | null;
  createdAt: string;
  updatedAt: string;
};

export type DomiciliosAdminPagina = {
  content: DomicilioAdminFila[];
  page: number;
  size: number;
  totalElements: number;
};

export type DomicilioAdminDetalle = Omit<DomicilioAdminFila, 'motivoCancelacion'> & {
  pedido: { id: string; modalidad: string; estado: string; total: number; cantidadItems: number } | null;
  motivoCancelacion: MotivoCancelacionDomicilio | null;
  /** Lo que escribió el administrador al cancelar. */
  detalleCancelacion: string | null;
  pagoTarifa: { estado: 'REGISTRADO' | 'CONFIRMADO'; monto: number } | null;
  trazabilidad: { estado: EstadoDomicilio; ocurridoAt: string; autor: PersonaDomicilio | null }[];
};

export type FiltrosDomiciliosAdmin = {
  estado?: EstadoDomicilio;
  desde?: string;
  hasta?: string;
  vendedorId?: string;
  domiciliarioId?: string;
  page?: number;
  size?: number;
  sort?: 'createdAt,asc' | 'createdAt,desc' | 'updatedAt,asc' | 'updatedAt,desc';
};

/** A partir de este tiempo sin domiciliario la app resalta el domicilio (RF-12 Edge 2). */
export const MINUTOS_SIN_TOMAR_RESALTE = 30;

export function textoSinTomar(minutos: number) {
  if (minutos < 60) return `${minutos} min sin domiciliario`;
  const horas = Math.floor(minutos / 60);
  const resto = minutos % 60;
  return `${horas} h${resto ? ` ${resto} min` : ''} sin domiciliario`;
}

/** Longitud del motivo de cancelación del administrador (RF-13). */
export const MOTIVO_CANCELACION_MINIMO = 10;
export const MOTIVO_CANCELACION_MAXIMO = 300;

/** RF-12: todos los domicilios de la plataforma. */
export function listarDomiciliosAdmin(filters: FiltrosDomiciliosAdmin = {}) {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([name, value]) => {
    if (value !== undefined && value !== '') query.set(name, String(value));
  });
  const queryString = query.toString();
  return withValidSession((session) =>
    requestJson<DomiciliosAdminPagina>(`/api/v1/domicilios${queryString ? `?${queryString}` : ''}`, {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}

/** RF-12: detalle completo, con la cronología y sus autores. Nunca trae el código de confirmación. */
export function obtenerDomicilioAdmin(domicilioId: string) {
  return withValidSession((session) =>
    requestJson<DomicilioAdminDetalle>(`/api/v1/domicilios/${encodeURIComponent(domicilioId)}`, {
      method: 'GET',
      headers: jsonHeaders({ accessToken: session.accessToken }),
    }),
  );
}

export type DomicilioCanceladoAdmin = {
  id: string;
  estado: 'CANCELADO';
  motivoCancelacion: string;
  pedido: { id: string; estado: 'CANCELADO' };
};

/** RF-13: cancela el domicilio y su pedido. Requiere conexión. */
export function cancelarDomicilioAdmin(domicilioId: string, motivo: string, idempotencyKey: string) {
  return withValidSession((session) =>
    requestJson<DomicilioCanceladoAdmin>(`/api/v1/domicilios/${encodeURIComponent(domicilioId)}/cancelar`, {
      method: 'POST',
      headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
      body: JSON.stringify({ motivo }),
    }),
  );
}
