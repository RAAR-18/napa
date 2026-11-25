import type { OrdenProductos, ProductosPage } from './productos';
import { actualizar, agregar, consultar } from './cache-cuenta.web';
export function guardarPaginaCatalogo(cuentaId: string, emprendimientoId: string,
  orden: OrdenProductos, data: ProductosPage, sigueVigente: () => boolean): Promise<void> {
  return actualizar(cuentaId, 'catalogo', (filas) =>
    agregar(filas, JSON.stringify([emprendimientoId, orden, data.page]), data).slice(0, 50), sigueVigente);
}
export async function leerPaginaCatalogo(cuentaId: string, emprendimientoId: string, orden: OrdenProductos, pagina: number) {
  const clave = JSON.stringify([emprendimientoId, orden, pagina]);
  const fila = (await consultar(cuentaId, 'catalogo')).find((fila) => fila.clave === clave);
  return fila ? { data: JSON.parse(fila.contenido) as ProductosPage, updatedAt: fila.fecha } : null;
}
export async function listarPaginasGuardadasCatalogo(cuentaId: string, emprendimientoId: string, orden: OrdenProductos) {
  return (await consultar(cuentaId, 'catalogo')).map((fila) => JSON.parse(fila.clave) as [string, OrdenProductos, number])
    .filter(([negocio, ordenGuardado]) => negocio === emprendimientoId && ordenGuardado === orden)
    .map(([, , pagina]) => pagina).sort((a, b) => a - b);
}
export function borrarCatalogoGuardado(cuentaId: string, emprendimientoId?: string) {
  return actualizar(cuentaId, 'catalogo', (filas) => emprendimientoId === undefined ? []
    : filas.filter((fila) => JSON.parse(fila.clave)[0] !== emprendimientoId));
}
