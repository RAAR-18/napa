import type { guardarConsultaPedidos as guardarOriginal } from './pedidos-cliente-cache';
import type { PedidosClientePagina } from './pedidos';
import { actualizar, agregar, consultar } from './cache-cuenta.web';
export type { DetallePedidoGuardado } from './pedidos-cliente-cache';
type TipoConsulta = Parameters<typeof guardarOriginal>[1];
export function guardarConsultaPedidos(...[cuenta, tipo, clave, contenido, vigente]: Parameters<typeof guardarOriginal>) {
  return actualizar(cuenta, 'pedidos', (filas) => {
    const nuevas = agregar(filas, JSON.stringify([tipo, clave]), contenido);
    let cantidad = 0;
    return nuevas.filter((fila) => JSON.parse(fila.clave)[0] !== tipo || ++cantidad <= 50);
  }, vigente);
}
export async function leerConsultaPedidos<T>(cuenta: string, tipo: TipoConsulta, clave: string) {
  const fila = (await consultar(cuenta, 'pedidos')).find((fila) => fila.clave === JSON.stringify([tipo, clave]));
  return fila ? { data: JSON.parse(fila.contenido) as T, fecha: fila.fecha } : null;
}
export async function listarPaginasGuardadas(cuenta: string, prefijo: string): Promise<number[]> {
  return (await consultar(cuenta, 'pedidos')).map((fila) => JSON.parse(fila.clave) as [TipoConsulta, string])
    .filter(([tipo, clave]) => tipo === 'listado' && clave.startsWith(prefijo))
    .map(([, clave]) => Number(clave.slice(prefijo.length))).sort((a, b) => a - b);
}
export function borrarPedidosGuardados(cuenta: string) { return actualizar(cuenta, 'pedidos', () => []); }
export function quitarDetalleGuardado(cuenta: string, tipo: 'detalle-vendedor' | 'detalle-domiciliario', id: string) {
  return actualizar(cuenta, 'pedidos', (filas) => filas.filter((fila) => fila.clave !== JSON.stringify([tipo, id])));
}
export function quitarDetalleVendedorGuardado(cuenta: string, pedidoId: string) {
  return quitarDetalleGuardado(cuenta, 'detalle-vendedor', pedidoId);
}
export function quitarPedidoGuardado(cuenta: string, pedidoId: string) {
  return actualizar(cuenta, 'pedidos', (filas) => filas
    .filter((fila) => fila.clave !== JSON.stringify(['detalle', pedidoId]))
    .map((fila) => {
      if (JSON.parse(fila.clave)[0] !== 'listado') return fila;
      const data = JSON.parse(fila.contenido) as PedidosClientePagina;
      return { ...fila, contenido: JSON.stringify({ ...data, content: data.content.filter(({ id }) => id !== pedidoId) }) };
    }));
}
