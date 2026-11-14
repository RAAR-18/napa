import { Platform } from 'react-native';

import { apiBaseUrl, jsonHeaders, requestJson, requestVoid } from './api';
import { withValidSession } from './auth';

export type UbicacionPerfil = {
  latitud: number;
  longitud: number;
  direccion: string;
  referencia?: string | null;
};

export type PerfilUsuario = {
  id: string;
  nombre: string;
  celular: string | null;
  correo: string | null;
  fotoUrl: string | null;
  rol: string;
  estado: string;
  celularVerificado: boolean;
  createdAt: string;
  ubicacion: UbicacionPerfil | null;
};

export type UsuarioResumen = { id: string; nombre: string; rol: string; estado: string; createdAt: string };
export type UsuariosPagina = { content: UsuarioResumen[]; page: number; size: number; totalElements: number };

export type FiltrosUsuarios = {
  rol?: string;
  estado?: 'PENDIENTE_VERIFICACION' | 'ACTIVA' | 'ELIMINADA';
  q?: string;
  page?: number;
  size?: number;
  sort?: 'createdAt,desc' | 'createdAt,asc' | 'nombre,asc' | 'nombre,desc';
};

export const rolNombres: Record<string, string> = {
  CLIENTE: 'Cliente',
  VENDEDOR_AMBULANTE: 'Vendedor ambulante',
  VENDEDOR_PUNTO_FIJO: 'Vendedor de punto fijo',
  DOMICILIARIO: 'Domiciliario',
  ADMINISTRADOR: 'Administrador',
};

export const estadoCuentaNombres: Record<string, string> = {
  PENDIENTE_VERIFICACION: 'Sin verificar',
  ACTIVA: 'Activa',
  ELIMINADA: 'Eliminada',
};

/** Textos para los conteos de `HAS_PENDING_OPERATIONS` (RF-11, RF-67, RF-68). */
export function describirPendientes(details: Record<string, unknown>): string[] {
  const avisos: string[] = [];
  const contar = (clave: string, singular: string, plural: string) => {
    const valor = Number(details[clave] ?? 0);
    if (valor > 0) avisos.push(valor === 1 ? `1 ${singular}` : `${valor} ${plural}`);
  };
  contar('pedidosPendientes', 'pedido pendiente', 'pedidos pendientes');
  contar('domiciliosActivos', 'domicilio activo', 'domicilios activos');
  contar('tarifasPendientes', 'tarifa de domicilio sin pagar', 'tarifas de domicilio sin pagar');
  if (details.emprendimientoActivo === true) avisos.push('un emprendimiento activo');
  return avisos;
}

/** Las fotos guardadas en el servidor llegan como ruta relativa (`/media/...`). */
export function urlFoto(fotoUrl: string | null) {
  if (!fotoUrl) return null;
  return /^https?:\/\//.test(fotoUrl) ? fotoUrl : `${apiBaseUrl}${fotoUrl}`;
}

function get<T>(path: string) {
  return withValidSession((session) =>
    requestJson<T>(path, { method: 'GET', headers: jsonHeaders({ accessToken: session.accessToken }) }),
  );
}

/** RF-08. */
export function obtenerMiPerfil() {
  return get<PerfilUsuario>('/api/v1/usuarios/me');
}

/** RF-08: nombre y ubicación (al menos uno). */
export function editarMiPerfil(cambios: { nombre?: string; ubicacion?: UbicacionPerfil }) {
  return withValidSession((session) =>
    requestJson<PerfilUsuario>('/api/v1/usuarios/me', {
      method: 'PATCH',
      headers: jsonHeaders({ accessToken: session.accessToken }),
      body: JSON.stringify(cambios),
    }),
  );
}

/** RF-08 US2, paso 1: código al número nuevo; el anterior sigue vigente hasta confirmar. */
export function solicitarCambioCelular(celular: string, idempotencyKey: string, canal?: 'WHATSAPP' | 'SMS') {
  return withValidSession((session) =>
    requestJson<{ mensajeId: string; canal: string; reenvioDisponibleEn: number }>(
      '/api/v1/usuarios/me/celular/solicitar-cambio',
      {
        method: 'POST',
        headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
        body: JSON.stringify({ celular: celular.trim(), canal }),
      },
    ),
  );
}

/** RF-08 US2, paso 2. */
export function confirmarCambioCelular(codigo: string, idempotencyKey: string) {
  return withValidSession((session) =>
    requestJson<PerfilUsuario>('/api/v1/usuarios/me/celular/confirmar', {
      method: 'POST',
      headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
      body: JSON.stringify({ codigo: codigo.trim() }),
    }),
  );
}

export const FOTO_MAXIMA_BYTES = 2 * 1024 * 1024;
export const TIPOS_FOTO = ['image/jpeg', 'image/png', 'image/webp'];

/** RF-08: la foto se sube como multipart (JPEG, PNG o WebP de hasta 2 MB); el servidor la valida por su contenido. */
export function subirFotoPerfil(archivo: { uri: string; name: string; type: string }) {
  return withValidSession(async (session) => {
    const form = new FormData();
    if (Platform.OS === 'web') {
      // En el navegador la imagen elegida es un blob: o data: URI; se envía como Blob.
      const blob = await (await fetch(archivo.uri)).blob();
      form.append('foto', blob, archivo.name);
    } else {
      // React Native acepta { uri, name, type } como parte de archivo.
      form.append('foto', archivo as unknown as Blob);
    }
    return requestJson<{ fotoUrl: string }>('/api/v1/usuarios/me/foto', {
      method: 'PUT',
      headers: { Authorization: `Bearer ${session.accessToken}` },
      body: form,
    });
  });
}

/** RF-11: requiere confirmación explícita; responde 409 con los conteos si hay operaciones pendientes. */
export function eliminarMiCuenta(idempotencyKey: string) {
  return withValidSession((session) =>
    requestVoid('/api/v1/usuarios/me/eliminar', {
      method: 'POST',
      headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
      body: JSON.stringify({ confirmar: true }),
    }),
  );
}

/** RF-69. */
export function listarUsuarios(filtros: FiltrosUsuarios = {}) {
  const params = new URLSearchParams();
  Object.entries(filtros).forEach(([name, value]) => {
    if (value !== undefined && value !== '') params.set(name, String(value));
  });
  const text = params.toString();
  return get<UsuariosPagina>(`/api/v1/usuarios${text ? `?${text}` : ''}`);
}

/** RF-66. */
export function obtenerUsuario(id: string) {
  return get<PerfilUsuario>(`/api/v1/usuarios/${encodeURIComponent(id)}`);
}

/** RF-67: cambiar el rol cierra las sesiones del usuario. */
export function editarUsuario(
  id: string,
  cambios: { nombre?: string; celular?: string; correo?: string; rol?: string; ubicacion?: UbicacionPerfil },
) {
  return withValidSession((session) =>
    requestJson<PerfilUsuario>(`/api/v1/usuarios/${encodeURIComponent(id)}`, {
      method: 'PATCH',
      headers: jsonHeaders({ accessToken: session.accessToken }),
      body: JSON.stringify(cambios),
    }),
  );
}

/** RF-68: sin `forzar`, la actividad en curso responde 409 como advertencia. */
export function eliminarUsuario(id: string, forzar: boolean, idempotencyKey: string) {
  return withValidSession((session) =>
    requestVoid(`/api/v1/usuarios/${encodeURIComponent(id)}/eliminar`, {
      method: 'POST',
      headers: jsonHeaders({ accessToken: session.accessToken, idempotencyKey }),
      body: JSON.stringify({ confirmar: true, forzar }),
    }),
  );
}
