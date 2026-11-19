import { ApiError, NetworkError } from './api';
import { getValidSession, readSession } from './auth';
import {
  guardarConsultaPedidos,
  leerConsultaPedidos,
  listarPaginasGuardadas,
  quitarPedidoGuardado,
} from './pedidos-cliente-cache';
import { consultarDetalleCliente, consultarListadoCliente } from './pedidos-cliente-consulta';
import { obtenerPedidoCliente, type PedidoClienteDetalle } from './pedidos';

jest.mock('./auth', () => ({
  getSessionGeneration: () => 0,
  readSession: jest.fn(),
  getValidSession: jest.fn(),
}));
jest.mock('./pedidos-cliente-cache', () => ({
  guardarConsultaPedidos: jest.fn().mockResolvedValue(undefined),
  leerConsultaPedidos: jest.fn(),
  listarPaginasGuardadas: jest.fn(),
  quitarPedidoGuardado: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('./pedidos', () => ({ obtenerPedidoCliente: jest.fn(), listarPedidosCliente: jest.fn() }));

const session = {
  usuarioId: 'cliente-1',
  rol: 'CLIENTE',
  accessToken: 'access',
  refreshToken: 'refresh',
  tokenType: 'Bearer',
  expiresIn: 900,
  expiresAt: 0,
};

const detalle: PedidoClienteDetalle = {
  id: 'pedido-1',
  estado: 'ACEPTADO',
  modalidad: 'DOMICILIO',
  reserva: null,
  createdAt: '2026-11-18T12:00:00Z',
  updatedAt: '2026-11-18T12:00:00Z',
  emprendimiento: { id: 'negocio', nombre: 'Frutas' },
  items: [],
  subtotal: 1000,
  tarifaDomicilio: 2000,
  total: 3000,
  medioPago: 'CUENTA_BANCARIA',
  pago: { estado: 'PENDIENTE_CONFIRMACION' },
  ubicacionEntrega: null,
  domicilio: { id: 'domicilio-1', estado: 'ASIGNADO', motivoCancelacion: null },
};

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(readSession).mockResolvedValue(session);
  jest.mocked(getValidSession).mockResolvedValue(session);
});

it('guarda un detalle reducido y conserva la respuesta completa para la vista en línea', async () => {
  jest.mocked(obtenerPedidoCliente).mockResolvedValueOnce(detalle);
  const resultado = await consultarDetalleCliente(detalle.id, () => true);

  expect(resultado).toEqual({ origen: 'servidor', data: detalle });
  expect(guardarConsultaPedidos).toHaveBeenCalledWith(
    'cliente-1', 'detalle', detalle.id, {
      id: detalle.id, estado: detalle.estado, modalidad: detalle.modalidad, reserva: null,
      updatedAt: detalle.updatedAt, total: 3000, tarifaDomicilio: 2000,
      emprendimiento: detalle.emprendimiento, pago: detalle.pago, items: [],
    }, expect.any(Function),
  );
});

it('permite leer un detalle de la cuenta cuando falla por red la renovación del token', async () => {
  jest.mocked(getValidSession).mockRejectedValueOnce(new NetworkError('Sin conexión'));
  const copia = { data: { id: detalle.id }, fecha: 123 };
  jest.mocked(leerConsultaPedidos).mockResolvedValueOnce(copia);
  await expect(consultarDetalleCliente(detalle.id, () => true)).resolves.toEqual({
    origen: 'copia', ...copia,
  });
  expect(leerConsultaPedidos).toHaveBeenCalledWith('cliente-1', 'detalle', detalle.id);
});

it('distingue una página sin copia de una respuesta vacía guardada', async () => {
  jest.mocked(getValidSession).mockRejectedValue(new NetworkError('Sin conexión'));
  const copiaVacia = { data: { page: 0, size: 20, content: [], totalElements: 0 }, fecha: 123 };
  jest.mocked(leerConsultaPedidos).mockResolvedValueOnce(copiaVacia).mockResolvedValueOnce(null);
  jest.mocked(listarPaginasGuardadas).mockResolvedValueOnce([2]);

  await expect(consultarListadoCliente('ACEPTADO', 0, 20, () => true)).resolves.toEqual({
    origen: 'copia', ...copiaVacia,
  });
  await expect(consultarListadoCliente('ACEPTADO', 1, 20, () => true)).resolves.toEqual({
    origen: 'sin-copia', paginas: [2],
  });
  expect(leerConsultaPedidos).toHaveBeenLastCalledWith('cliente-1', 'listado', 'ACEPTADO:20:1');
});

it('invalida un pedido inaccesible y propaga el 404 del servidor', async () => {
  const causa = new ApiError('No disponible', 404, 'NOT_FOUND');
  jest.mocked(obtenerPedidoCliente).mockRejectedValueOnce(causa);

  await expect(consultarDetalleCliente(detalle.id, () => true)).rejects.toBe(causa);
  expect(quitarPedidoGuardado).toHaveBeenCalledWith('cliente-1', detalle.id);
  expect(leerConsultaPedidos).not.toHaveBeenCalled();
});
