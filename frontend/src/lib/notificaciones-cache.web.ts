import type { NotificacionesPagina } from './notificaciones';
import type { PaginaNotificacionesGuardada } from './notificaciones-cache';
import { actualizar, agregar, consultar } from './cache-cuenta.web';
export type { NotificacionGuardada, PaginaNotificacionesGuardada } from './notificaciones-cache';
export function guardarPaginaNotificaciones(cuenta: string, soloNoLeidas: boolean,
  respuesta: NotificacionesPagina, vigente: () => boolean) {
  const { page, size, totalElements } = respuesta;
  const data: PaginaNotificacionesGuardada = { page, size, totalElements,
    content: respuesta.content.map(({ id, titulo, cuerpo, leida, createdAt }) => ({ id, titulo, cuerpo, leida, createdAt })) };
  return actualizar(cuenta, 'notificaciones', (filas) =>
    agregar(filas, JSON.stringify([soloNoLeidas, page]), data).slice(0, 50), vigente);
}
export async function leerPaginaNotificaciones(cuenta: string, soloNoLeidas: boolean, pagina: number) {
  const clave = JSON.stringify([soloNoLeidas, pagina]);
  const fila = (await consultar(cuenta, 'notificaciones')).find((fila) => fila.clave === clave);
  return fila ? { data: JSON.parse(fila.contenido) as PaginaNotificacionesGuardada, fecha: fila.fecha } : null;
}
export async function listarPaginasGuardadas(cuenta: string, soloNoLeidas: boolean) {
  return (await consultar(cuenta, 'notificaciones')).map((fila) => JSON.parse(fila.clave) as [boolean, number])
    .filter(([filtro]) => filtro === soloNoLeidas).map(([, pagina]) => pagina).sort((a, b) => a - b);
}
export function borrarNotificacionesGuardadas(cuenta: string) { return actualizar(cuenta, 'notificaciones', () => []); }
