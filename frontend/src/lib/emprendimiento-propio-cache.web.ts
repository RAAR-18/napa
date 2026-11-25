import type { Emprendimiento } from './emprendimientos';
import { actualizar, agregar, consultar } from './cache-cuenta.web';
export function guardarEmprendimientoPropio(cuenta: string, valor: Emprendimiento, vigente: () => boolean) {
  return actualizar(cuenta, 'negocio', (filas) => agregar(filas, 'propio', valor).slice(0, 1), vigente);
}
export async function leerEmprendimientoPropio(cuenta: string) {
  const fila = (await consultar(cuenta, 'negocio'))[0];
  return fila ? { valor: JSON.parse(fila.contenido) as Emprendimiento, fecha: fila.fecha } : null;
}
export function borrarEmprendimientoPropio(cuenta: string) { return actualizar(cuenta, 'negocio', () => []); }
