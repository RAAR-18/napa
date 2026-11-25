import type { OrdenProductos, ProductosPage } from './productos';
import { actualizar, agregar, consultar } from './cache-cuenta.web';
export function guardarPaginaInventario(cuentaId: string, orden: OrdenProductos, pagina: ProductosPage,
  sesionVigente: () => boolean): Promise<void> {
  return actualizar(cuentaId, 'inventario', (filas) => {
    const negocio = pagina.emprendimiento?.id;
    if (!negocio) return [];
    const propias = filas.filter((fila) => JSON.parse(fila.clave)[0] === negocio);
    return agregar(propias, JSON.stringify([negocio, orden, pagina.page]), pagina).slice(0, 50);
  }, sesionVigente);
}
export async function leerPaginaInventario(cuentaId: string, orden: OrdenProductos, pagina: number) {
  const fila = (await consultar(cuentaId, 'inventario')).find((fila) => {
    const [, guardado, numero] = JSON.parse(fila.clave) as [string, OrdenProductos, number];
    return guardado === orden && numero === pagina;
  });
  return fila ? { data: JSON.parse(fila.contenido) as ProductosPage, updatedAt: fila.fecha } : null;
}
export async function leerProductoInventario(cuentaId: string, productoId: string) {
  for (const fila of await consultar(cuentaId, 'inventario')) {
    const pagina = JSON.parse(fila.contenido) as ProductosPage;
    const producto = pagina.content.find((producto) => producto.id === productoId);
    if (producto) return producto;
  }
  return null;
}
export function borrarInventarioGuardado(cuentaId: string): Promise<void> {
  return actualizar(cuentaId, 'inventario', () => []);
}
