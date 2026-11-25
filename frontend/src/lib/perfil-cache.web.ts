import type { PerfilUsuario } from './usuarios';
import type { PerfilGuardado } from './perfil-cache';
import { actualizar, agregar, consultar } from './cache-cuenta.web';
export type { PerfilGuardado } from './perfil-cache';
export function guardarPerfil(perfil: PerfilUsuario, vigente: () => boolean) {
  const { id, nombre, rol, celular, correo, ubicacion } = perfil;
  const contenido: PerfilGuardado = { nombre, rol, celular, correo, direccion: ubicacion?.direccion ?? null };
  return actualizar(id, 'perfil', (filas) => agregar(filas, 'perfil', contenido).slice(0, 1), vigente);
}
export async function leerPerfilGuardado(cuentaId: string) {
  const fila = (await consultar(cuentaId, 'perfil'))[0];
  return fila ? { perfil: JSON.parse(fila.contenido) as PerfilGuardado, fecha: fila.fecha } : null;
}
export function borrarPerfilGuardado(cuentaId: string) { return actualizar(cuentaId, 'perfil', () => []); }
