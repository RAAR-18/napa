import { ApiError, NetworkError } from './api';
import { getSessionGeneration, getValidSession, readSession } from './auth';
import {
  borrarPedidosGuardados,
  guardarConsultaPedidos,
  leerConsultaPedidos,
  listarPaginasGuardadas,
  quitarPedidoGuardado,
  type DetallePedidoGuardado,
} from './pedidos-cliente-cache';
import {
  listarPedidosCliente,
  obtenerPedidoCliente,
  type EstadoPedido,
  type PedidoClienteDetalle,
  type PedidosClientePagina,
} from './pedidos';

async function consultar<TOnline, TCopia extends PedidosClientePagina | DetallePedidoGuardado>(
  tipo: 'listado' | 'detalle',
  clave: string,
  obtener: () => Promise<TOnline>,
  prepararCopia: (data: TOnline) => TCopia,
  activa: () => boolean,
  prefijoListado?: string,
) {
  const generacion = getSessionGeneration();
  const vigente = () => activa() && generacion === getSessionGeneration();
  const local = await readSession();

  try {
    const session = await getValidSession();
    if (!vigente()) return null;
    if (session.rol !== 'CLIENTE') throw new Error('Esta consulta es para cuentas de cliente.');

    const data = await obtener();
    if (!vigente()) return null;
    if (session.usuarioId) {
      await guardarConsultaPedidos(session.usuarioId, tipo, clave, prepararCopia(data), vigente)
        .catch((cause: unknown) => console.warn('No se pudo guardar la consulta de pedidos.', cause));
    }
    return vigente() ? { origen: 'servidor' as const, data } : null;
  } catch (cause) {
    if (!vigente()) return null;
    const cuenta = local?.rol === 'CLIENTE' ? local.usuarioId : undefined;
    if (cuenta && cause instanceof NetworkError) {
      const copia = await leerConsultaPedidos<TCopia>(cuenta, tipo, clave);
      const paginas = !copia && prefijoListado ? await listarPaginasGuardadas(cuenta, prefijoListado) : [];
      if (!vigente()) return null;
      return copia ? { origen: 'copia' as const, ...copia } : { origen: 'sin-copia' as const, paginas };
    }
    if (cuenta && cause instanceof ApiError) {
      if (cause.status === 401 || cause.status === 403) {
        await borrarPedidosGuardados(cuenta)
          .catch((error: unknown) => console.warn('No se pudo limpiar la caché de pedidos.', error));
      } else if (tipo === 'detalle' && cause.status === 404) {
        await quitarPedidoGuardado(cuenta, clave)
          .catch((error: unknown) => console.warn('No se pudo retirar el pedido guardado.', error));
      }
    }
    if (!vigente()) return null;
    throw cause;
  }
}

export function consultarListadoCliente(
  estado: EstadoPedido | undefined,
  page: number,
  size: number,
  activa: () => boolean,
) {
  const filtros = { estado, page, size };
  const prefijo = `${estado ?? 'TODOS'}:${size}:`;
  return consultar('listado', `${prefijo}${page}`, () => listarPedidosCliente(filtros), (data) => data, activa, prefijo);
}

function detalleGuardado(data: PedidoClienteDetalle): DetallePedidoGuardado {
  const { id, estado, modalidad, reserva, updatedAt, total, tarifaDomicilio } = data;
  return {
    id, estado, modalidad, reserva, updatedAt, total, tarifaDomicilio,
    emprendimiento: { id: data.emprendimiento.id, nombre: data.emprendimiento.nombre },
    pago: data.pago ? { estado: data.pago.estado } : null,
    items: data.items.map(({ productoId, nombre, unidad, cantidad, subtotal }) => ({
      productoId, nombre, unidad, cantidad, subtotal,
    })),
  };
}

export function consultarDetalleCliente(id: string, activa: () => boolean) {
  return consultar('detalle', id, () => obtenerPedidoCliente(id), detalleGuardado, activa);
}
