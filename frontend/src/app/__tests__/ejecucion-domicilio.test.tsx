import { act, fireEvent, render, waitFor } from '@testing-library/react-native';
import { router } from 'expo-router';
import * as Crypto from 'expo-crypto';
import { Linking } from 'react-native';

import DomicilioDomiciliarioScreen from '../domicilio-domiciliario';
import MisPedidosScreen, { REFRESCO_MIS_PEDIDOS_MS } from '../mis-pedidos';
import PedidoClienteScreen from '../pedido-cliente';
import PedidoVendedorScreen from '../pedido-vendedor';
import TrazabilidadDomicilioScreen from '../trazabilidad-domicilio';
import { outbox } from '../../database/outbox';
import { leerConsultaPedidos } from '../../lib/pedidos-cliente-cache';
import { ApiError, NetworkError } from '../../lib/api';
import { getValidSession, readSession } from '../../lib/auth';
import {
  confirmarEntrega,
  confirmarRecogida,
  confirmarSalida,
  obtenerCodigoConfirmacion,
  obtenerDomicilioDomiciliario,
  obtenerTrazabilidad,
  type DomicilioDomiciliarioDetalle,
} from '../../lib/domicilios';
import {
  listarPedidosCliente,
  obtenerDomiciliarioPedido,
  obtenerPedidoCliente,
  obtenerPedidoVendedor,
  type PedidoClienteDetalle,
  type PedidoVendedorDetalle,
} from '../../lib/pedidos';

jest.mock('react-native-maps', () => {
  const { Text, View } = jest.requireActual('react-native');
  const MapView = ({ children, accessibilityLabel }: { children: unknown; accessibilityLabel: string }) => (
    <View accessibilityLabel={accessibilityLabel}>{children as never}</View>
  );
  const Marker = ({ title }: { title: string }) => <Text>{`marcador ${title}`}</Text>;
  return { __esModule: true, default: MapView, Marker };
});
jest.mock('expo-router', () => ({
  useFocusEffect: (effect: () => void | (() => void)) => {
    const React = jest.requireActual<typeof import('react')>('react');
    React.useEffect(effect, [effect]);
  },
  router: { back: jest.fn(), push: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: () => ({ domicilioId: 'delivery-id', pedidoId: 'order-id' }),
}));
jest.mock('expo-crypto', () => ({ randomUUID: jest.fn() }));
jest.mock('expo-notifications', () => ({
  addNotificationReceivedListener: jest.fn(() => ({ remove: jest.fn() })),
}));
jest.mock('../../lib/auth', () => ({
  clearSession: jest.fn(),
  getValidSession: jest.fn(),
  getSessionGeneration: () => 0,
  readSession: jest.fn().mockResolvedValue(null),
}));
jest.mock('../../database/outbox', () => ({
  outbox: {
    listar: jest.fn().mockResolvedValue([]), suscribir: () => () => {},
    encolar: jest.fn().mockResolvedValue(undefined), propietario: jest.fn().mockResolvedValue('offline-account'),
  },
}));
jest.mock('../../lib/pedidos-cliente-cache', () => ({
  ...jest.requireActual('../../lib/pedidos-cliente-cache'),
  leerConsultaPedidos: jest.fn().mockResolvedValue(null),
}));
jest.mock('../../lib/domicilios', () => ({
  ...jest.requireActual('../../lib/domicilios'),
  confirmarEntrega: jest.fn(),
  confirmarRecogida: jest.fn(),
  confirmarSalida: jest.fn(),
  obtenerCodigoConfirmacion: jest.fn(),
  obtenerDomicilioDomiciliario: jest.fn(),
  obtenerTrazabilidad: jest.fn(),
}));
jest.mock('../../lib/pedidos', () => ({
  ...jest.requireActual('../../lib/pedidos'),
  listarPedidosCliente: jest.fn(),
  obtenerDomiciliarioPedido: jest.fn(),
  obtenerPedidoCliente: jest.fn(),
  obtenerPedidoVendedor: jest.fn(),
}));
jest.mock('../../lib/pagos', () => ({
  ...jest.requireActual('../../lib/pagos'),
  consultarPagoTarifa: jest.fn().mockResolvedValue({
    domicilioId: 'delivery-id', pagoId: 'payment-id', estado: 'CONFIRMADO', monto: 4500,
    medio: 'CUENTA_BANCARIA', registradoAt: null, confirmadoAt: null, cuentaDomiciliario: null,
  }),
}));

function session(rol: string) {
  return {
    accessToken: 'access',
    refreshToken: 'refresh',
    rol,
    expiresIn: 900,
    expiresAt: Date.now() + 900000,
    tokenType: 'Bearer',
  };
}

const exacto = { latitud: 11.2501, longitud: -74.2002, direccion: 'Calle 15 # 3-20', referencia: null, aproximado: false };
const mine: DomicilioDomiciliarioDetalle = {
  id: 'delivery-id',
  estado: 'ASIGNADO',
  ganancia: 4500,
  asignadoAMi: true,
  rutaDisponible: true,
  distanciaMetros: 1850,
  origen: { ...exacto, direccion: 'Carrera 5 # 10-20' },
  destino: exacto,
  receptor: { nombre: 'Ana Pérez', celular: '+573001112233' },
  resumenItems: { cantidadItems: 3 },
  asignadoAt: '2026-11-13T12:05:00Z',
};
const items = [{
  productoId: 'product-id',
  nombre: 'Mango',
  unidad: 'KILOGRAMO',
  cantidad: 2,
  precioUnitario: 3000,
  subtotal: 6000,
  productoEliminado: false,
}];
const vendorOrder: PedidoVendedorDetalle = {
  id: 'order-id',
  estado: 'ACEPTADO',
  modalidad: 'DOMICILIO',
  reserva: null,
  createdAt: '2026-11-13T12:00:00Z',
  updatedAt: '2026-11-13T12:10:00Z',
  cliente: { nombre: 'Ana Pérez' },
  items,
  subtotal: 6000,
  tarifaDomicilio: 4500,
  total: 10500,
  medioPago: 'CUENTA_BANCARIA',
  pago: { estado: 'CONFIRMADO' },
  ubicacionEntrega: null,
  domicilio: { id: 'delivery-id', estado: 'RECOGIDO', motivoCancelacion: null },
};
const clientOrder: PedidoClienteDetalle = {
  id: 'order-id',
  estado: 'ACEPTADO',
  modalidad: 'DOMICILIO',
  reserva: null,
  createdAt: '2026-11-13T12:00:00Z',
  updatedAt: '2026-11-13T12:10:00Z',
  emprendimiento: { id: 'business-id', nombre: 'Frutas Doña Ana' },
  items,
  subtotal: 6000,
  tarifaDomicilio: 4500,
  total: 10500,
  medioPago: 'CUENTA_BANCARIA',
  pago: { estado: 'CONFIRMADO' },
  ubicacionEntrega: null,
  domicilio: { id: 'delivery-id', estado: 'ASIGNADO', motivoCancelacion: null },
};

beforeEach(() => {
  jest.clearAllMocks();
  let keyCounter = 0;
  jest.mocked(Crypto.randomUUID).mockImplementation(() => `key-${++keyCounter}` as ReturnType<typeof Crypto.randomUUID>);
  jest.mocked(obtenerDomiciliarioPedido).mockResolvedValue({
    pedidoId: 'order-id',
    domicilioId: 'delivery-id',
    estadoDomicilio: 'ASIGNADO',
    domiciliario: { nombre: 'Carlos Ruiz', fotoUrl: null, calificacion: null },
  });
});

describe('domiciliario', () => {
  beforeEach(() => {
    jest.mocked(getValidSession).mockResolvedValue(session('DOMICILIARIO'));
  });

  it.each(['ASIGNADO', 'RECOGIDO', 'EN_CAMINO'] as const)('abre Google Maps directamente para un domicilio %s', async (estado) => {
    jest.mocked(obtenerDomicilioDomiciliario).mockResolvedValue({ ...mine, estado });
    const abrir = jest.spyOn(Linking, 'openURL').mockResolvedValue(undefined);
    try {
      const view = await render(<DomicilioDomiciliarioScreen />);
      await fireEvent.press(await view.findByRole('button', { name: 'Ver ruta en Google Maps' }));
      const url = new URL(abrir.mock.calls[0][0]);
      expect(url.searchParams.get('destination')).toBe(`${mine.destino.latitud},${mine.destino.longitud}`);
      expect(url.searchParams.has('waypoints')).toBe(estado === 'ASIGNADO');
      expect(router.push).not.toHaveBeenCalled();
    } finally { abrir.mockRestore(); }
  });

  it('antes de aceptar solo abre la recogida, sin compartir el destino aproximado', async () => {
    jest.mocked(obtenerDomicilioDomiciliario).mockResolvedValue({
      ...mine, estado: 'DISPONIBLE', asignadoAMi: false, rutaDisponible: false,
      destino: { ...mine.destino, aproximado: true, latitud: 11.2, longitud: -74.1, direccion: null },
    });
    const abrir = jest.spyOn(Linking, 'openURL').mockResolvedValue(undefined);
    try {
      const view = await render(<DomicilioDomiciliarioScreen />);
      await fireEvent.press(await view.findByRole('button', { name: 'Ver recogida en Google Maps' }));
      const url = new URL(abrir.mock.calls[0][0]);
      expect(url.searchParams.get('destination')).toBe(`${mine.origen.latitud},${mine.origen.longitud}`);
      expect(url.searchParams.has('waypoints')).toBe(false);
      expect(view.queryByRole('button', { name: 'Ver ruta en Google Maps' })).toBeNull();
    } finally { abrir.mockRestore(); }
  });

  it.each(['ENTREGADO', 'CANCELADO'] as const)('no ofrece ruta para un domicilio %s', async (estado) => {
    jest.mocked(obtenerDomicilioDomiciliario).mockResolvedValue({ ...mine, estado, rutaDisponible: false });
    const view = await render(<DomicilioDomiciliarioScreen />);
    await view.findByText(`Estado: ${estado === 'ENTREGADO' ? 'Entregado' : 'Cancelado'}`);
    expect(view.queryByRole('button', { name: 'Ver ruta en Google Maps' })).toBeNull();
    expect(view.queryByRole('button', { name: 'Ver recogida en Google Maps' })).toBeNull();
  });

  it('confirms the pickup of an assigned delivery', async () => {
    jest.mocked(obtenerDomicilioDomiciliario)
      .mockResolvedValueOnce(mine)
      .mockResolvedValueOnce({ ...mine, estado: 'RECOGIDO' });
    jest.mocked(confirmarRecogida).mockResolvedValue({
      id: 'delivery-id', estado: 'RECOGIDO', updatedAt: '2026-11-13T12:20:00Z',
    });
    const view = await render(<DomicilioDomiciliarioScreen />);

    await waitFor(() => expect(view.getByRole('button', { name: 'Confirmar recogida' })).toBeTruthy());
    await fireEvent.press(view.getByRole('button', { name: 'Confirmar recogida' }));

    await waitFor(() => expect(confirmarRecogida).toHaveBeenCalledWith('delivery-id', 'key-1'));
    await waitFor(() => expect(view.getByText(/Espera a que el vendedor confirme tu salida/)).toBeTruthy());
  });

  it('tells how many attempts remain and uses a new key for every attempt', async () => {
    jest.mocked(obtenerDomicilioDomiciliario).mockResolvedValue({ ...mine, estado: 'EN_CAMINO' });
    jest.mocked(confirmarEntrega)
      .mockRejectedValueOnce(new ApiError('El código no es válido.', 400, 'CODE_INVALID', { intentosRestantes: 4 }))
      .mockRejectedValueOnce(new ApiError('El código no es válido.', 400, 'CODE_INVALID', { intentosRestantes: 1 }));
    const view = await render(<DomicilioDomiciliarioScreen />);

    await waitFor(() => expect(view.getByLabelText('Código del cliente')).toBeTruthy());
    await fireEvent.changeText(view.getByLabelText('Código del cliente'), '111111');
    await fireEvent.press(view.getByRole('button', { name: 'Confirmar entrega' }));
    await waitFor(() => expect(view.getByText('El código no es válido. Te quedan 4 intentos.')).toBeTruthy());

    await fireEvent.press(view.getByRole('button', { name: 'Confirmar entrega' }));
    await waitFor(() => expect(view.getByText('El código no es válido. Te queda 1 intento.')).toBeTruthy());
    expect(confirmarEntrega).toHaveBeenNthCalledWith(1, 'delivery-id', '111111', 'key-1');
    expect(confirmarEntrega).toHaveBeenNthCalledWith(2, 'delivery-id', '111111', 'key-2');
  });

  it('explains the lock after five failures', async () => {
    jest.mocked(obtenerDomicilioDomiciliario).mockResolvedValue({ ...mine, estado: 'EN_CAMINO' });
    jest.mocked(confirmarEntrega).mockRejectedValue(new ApiError('Bloqueado', 429, 'CODE_ATTEMPTS_LOCKED'));
    const view = await render(<DomicilioDomiciliarioScreen />);

    await waitFor(() => expect(view.getByLabelText('Código del cliente')).toBeTruthy());
    await fireEvent.changeText(view.getByLabelText('Código del cliente'), '111111');
    await fireEvent.press(view.getByRole('button', { name: 'Confirmar entrega' }));

    await waitFor(() => expect(view.getByText(/Superaste los 5 intentos/)).toBeTruthy());
  });

  it('does not send a code without six digits', async () => {
    jest.mocked(obtenerDomicilioDomiciliario).mockResolvedValue({ ...mine, estado: 'EN_CAMINO' });
    const view = await render(<DomicilioDomiciliarioScreen />);

    await waitFor(() => expect(view.getByLabelText('Código del cliente')).toBeTruthy());
    await fireEvent.changeText(view.getByLabelText('Código del cliente'), '12a3');
    await fireEvent.press(view.getByRole('button', { name: 'Confirmar entrega' }));

    expect(view.getByText('El código tiene 6 dígitos. Pídeselo al cliente.')).toBeTruthy();
    expect(confirmarEntrega).not.toHaveBeenCalled();
    expect(Crypto.randomUUID).not.toHaveBeenCalled();
  });
});

describe('vendedor', () => {
  it('confirms the departure once the courier picked the order up', async () => {
    jest.mocked(getValidSession).mockResolvedValue(session('VENDEDOR_PUNTO_FIJO'));
    jest.mocked(obtenerPedidoVendedor).mockResolvedValue(vendorOrder);
    jest.mocked(confirmarSalida).mockResolvedValue({
      id: 'delivery-id', estado: 'EN_CAMINO', updatedAt: '2026-11-13T12:30:00Z',
    });
    const view = await render(<PedidoVendedorScreen />);

    await waitFor(() => expect(view.getByRole('button', { name: 'Confirmar salida' })).toBeTruthy());
    await fireEvent.press(view.getByRole('button', { name: 'Confirmar salida' }));
    expect(confirmarSalida).not.toHaveBeenCalled();
    await fireEvent.press(view.getByRole('button', { name: 'Sí, ya salió' }));

    await waitFor(() => expect(confirmarSalida).toHaveBeenCalledWith('delivery-id', 'key-1'));
  });

  it('does not offer the departure before the pickup', async () => {
    jest.mocked(getValidSession).mockResolvedValue(session('VENDEDOR_PUNTO_FIJO'));
    jest.mocked(obtenerPedidoVendedor).mockResolvedValue({
      ...vendorOrder,
      domicilio: { id: 'delivery-id', estado: 'ASIGNADO', motivoCancelacion: null },
    });
    const view = await render(<PedidoVendedorScreen />);

    await waitFor(() => expect(view.getByText(/viene a recoger el pedido/)).toBeTruthy());
    expect(view.queryByRole('button', { name: 'Confirmar salida' })).toBeNull();
  });

  it('shows who carries the order without the phone (RF-27)', async () => {
    jest.mocked(getValidSession).mockResolvedValue(session('VENDEDOR_PUNTO_FIJO'));
    jest.mocked(obtenerPedidoVendedor).mockResolvedValue(vendorOrder);
    const view = await render(<PedidoVendedorScreen />);

    await waitFor(() => expect(view.getByText('Domiciliario: Carlos Ruiz')).toBeTruthy());
    expect(view.getByText('Aún sin calificaciones')).toBeTruthy();
    expect(obtenerDomiciliarioPedido).toHaveBeenCalledWith(vendorOrder.id);
  });
});

describe('cliente', () => {
  beforeEach(() => {
    jest.mocked(getValidSession).mockResolvedValue(session('CLIENTE'));
  });

  it('actualiza estados automáticamente sin vaciar la lista, mantiene filtros y cancela al salir', async () => {
    jest.useFakeTimers();
    try {
      const pagina = {
        content: [{ id: 'order-id', emprendimiento: { id: 'business-id', nombre: 'Tienda de prueba' },
          modalidad: 'DOMICILIO' as const, createdAt: '2026-11-25T12:00:00Z',
          estado: 'PENDIENTE' as const, reserva: null, total: 10500 }],
        page: 0, size: 20, totalElements: 1,
      };
      jest.mocked(listarPedidosCliente).mockResolvedValue(pagina);
      const vista = await render(<MisPedidosScreen />);
      await vista.findByRole('button', { name: 'Ver pedido a Tienda de prueba, Pendiente' });
      jest.mocked(listarPedidosCliente).mockResolvedValue({ ...pagina,
        content: [{ ...pagina.content[0], estado: 'EN_CAMINO' }],
      });
      await act(async () => { jest.advanceTimersByTime(REFRESCO_MIS_PEDIDOS_MS); });
      expect(vista.getByRole('button', { name: 'Ver pedido a Tienda de prueba, En camino' })).toBeTruthy();
      expect(vista.queryByLabelText('Cargando pedidos')).toBeNull();
      await fireEvent.press(vista.getByRole('button', { name: 'Estado: Todos' }));
      await fireEvent.press(vista.getByRole('radio', { name: 'En camino' }));
      await vista.findByRole('button', { name: 'Ver pedido a Tienda de prueba, En camino' });
      await act(async () => { jest.advanceTimersByTime(REFRESCO_MIS_PEDIDOS_MS); });
      expect(listarPedidosCliente).toHaveBeenLastCalledWith({ estado: 'EN_CAMINO', page: 0, size: 20 });
      jest.mocked(listarPedidosCliente).mockRejectedValueOnce(new NetworkError('Sin conexión'));
      await act(async () => { jest.advanceTimersByTime(REFRESCO_MIS_PEDIDOS_MS); });
      expect(vista.getByText('Tienda de prueba')).toBeTruthy();
      await vista.unmount();
      const consultas = jest.mocked(listarPedidosCliente).mock.calls.length;
      await act(async () => { jest.advanceTimersByTime(REFRESCO_MIS_PEDIDOS_MS * 2); });
      expect(listarPedidosCliente).toHaveBeenCalledTimes(consultas);
    } finally {
      jest.useRealTimers();
    }
  });

  it('lists the orders and opens one', async () => {
    jest.mocked(listarPedidosCliente).mockResolvedValue({
      content: [{
        id: 'order-id',
        emprendimiento: { id: 'business-id', nombre: 'Frutas Doña Ana' },
        modalidad: 'DOMICILIO',
        createdAt: '2026-11-13T12:00:00Z',
        estado: 'EN_CAMINO',
        reserva: null,
        total: 10500,
      }],
      page: 0,
      size: 20,
      totalElements: 1,
    });
    const view = await render(<MisPedidosScreen />);

    await waitFor(() => expect(view.getByText('Frutas Doña Ana')).toBeTruthy());
    await fireEvent.press(view.getByRole('button', { name: 'Ver pedido a Frutas Doña Ana, En camino' }));
    expect(router.push).toHaveBeenCalledWith({ pathname: '/pedido-cliente', params: { pedidoId: 'order-id' } });
  });

  it('filters by state and shows an empty message', async () => {
    jest.mocked(listarPedidosCliente).mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0 });
    const view = await render(<MisPedidosScreen />);

    await waitFor(() => expect(view.getByText(/Aún no has hecho pedidos/)).toBeTruthy());
    expect(view.queryByRole('radio', { name: 'Entregados' })).toBeNull();
    await fireEvent.press(view.getByRole('button', { name: 'Estado: Todos' }));
    await fireEvent.press(view.getByRole('radio', { name: 'Entregados' }));

    await waitFor(() => expect(listarPedidosCliente).toHaveBeenLastCalledWith({
      estado: 'ENTREGADO', page: 0, size: 20,
    }));
    await waitFor(() => expect(view.getByText('No tienes pedidos con ese estado.')).toBeTruthy());
    expect(view.getByRole('button', { name: 'Estado: Entregados' }).props.accessibilityState.expanded).toBe(false);
    expect(view.queryByRole('radio', { name: 'Entregados' })).toBeNull();
    await fireEvent.press(view.getByRole('button', { name: 'Estado: Entregados' }));
    await fireEvent.press(view.getByRole('radio', { name: 'Todos' }));
    await waitFor(() => expect(listarPedidosCliente).toHaveBeenLastCalledWith({
      estado: undefined, page: 0, size: 20,
    }));
  });

  it('shows the delivery code once a courier accepted', async () => {
    jest.mocked(obtenerPedidoCliente).mockResolvedValue(clientOrder);
    jest.mocked(obtenerCodigoConfirmacion).mockResolvedValue({
      domicilioId: 'delivery-id', estado: 'ASIGNADO', codigo: '048213',
    });
    const view = await render(<PedidoClienteScreen />);

    await waitFor(() => expect(view.getByText('048213')).toBeTruthy());
    expect(view.getByText('Pedido a Frutas Doña Ana')).toBeTruthy();
    await waitFor(() => expect(view.getByText('Domiciliario: Carlos Ruiz')).toBeTruthy());
    await fireEvent.press(view.getByRole('button', { name: 'Ver trazabilidad' }));
    expect(router.push).toHaveBeenCalledWith({
      pathname: '/trazabilidad-domicilio',
      params: { domicilioId: 'delivery-id' },
    });
  });

  it('does not ask for the code while no courier accepted', async () => {
    jest.mocked(obtenerPedidoCliente).mockResolvedValue({
      ...clientOrder,
      domicilio: { id: 'delivery-id', estado: 'DISPONIBLE', motivoCancelacion: null },
    });
    const view = await render(<PedidoClienteScreen />);

    await waitFor(() => expect(view.getByText(/Buscando domiciliario/)).toBeTruthy());
    expect(obtenerCodigoConfirmacion).not.toHaveBeenCalled();
    expect(view.getByText('Aún no hay domiciliario asignado.')).toBeTruthy();
    expect(obtenerDomiciliarioPedido).not.toHaveBeenCalled();
  });

  it('says the order is not available when it belongs to someone else', async () => {
    jest.mocked(obtenerPedidoCliente).mockRejectedValue(new ApiError('No', 404, 'NOT_FOUND'));
    const view = await render(<PedidoClienteScreen />);

    await waitFor(() => expect(view.getByText('El pedido no está disponible.')).toBeTruthy());
  });

  it('shows the delivery timeline', async () => {
    jest.mocked(obtenerTrazabilidad).mockResolvedValue({
      domicilioId: 'delivery-id',
      estado: 'EN_CAMINO',
      motivoCancelacion: null,
      updatedAt: '2026-11-13T12:30:00Z',
      eventos: [
        { estado: 'DISPONIBLE', ocurridoAt: '2026-11-13T12:00:00Z' },
        { estado: 'ASIGNADO', ocurridoAt: '2026-11-13T12:05:00Z' },
        { estado: 'RECOGIDO', ocurridoAt: '2026-11-13T12:20:00Z' },
        { estado: 'EN_CAMINO', ocurridoAt: '2026-11-13T12:30:00Z' },
      ],
      ubicacionDomiciliario: null,
    });
    const view = await render(<TrazabilidadDomicilioScreen />);

    await waitFor(() => expect(view.getByText('Recogido')).toBeTruthy());
    expect(view.getAllByText('En camino').length).toBe(2);
    expect(view.getByText('Sin ubicación reciente del domiciliario.')).toBeTruthy();
  });
});

it('queues pickup from the courier snapshot and keeps the last confirmed state', async () => {
  jest.clearAllMocks();
  jest.mocked(Crypto.randomUUID).mockReturnValue('offline-key');
  const local = { ...session('DOMICILIARIO'), usuarioId: 'offline-account' };
  jest.mocked(readSession).mockResolvedValue(local);
  jest.mocked(getValidSession).mockRejectedValue(new NetworkError('Sin conexión'));
  jest.mocked(leerConsultaPedidos).mockImplementation(async (_cuenta, tipo) =>
    tipo === 'detalle-domiciliario' ? { data: mine as never, fecha: Date.now() } : null,
  );
  try {
    const view = await render(<DomicilioDomiciliarioScreen />);
    await view.findByText(/Datos guardados/);
    await fireEvent.press(view.getByRole('button', { name: 'Confirmar recogida' }));
    await waitFor(() => expect(outbox.encolar).toHaveBeenCalledWith(expect.objectContaining({
      usuarioId: 'offline-account', path: '/api/v1/domicilios/delivery-id/confirmar-recogida',
      method: 'POST', idempotencyKey: 'offline-key', intentoPrevio: false,
    })));
    expect(confirmarRecogida).not.toHaveBeenCalled();
    expect(view.getByText('Estado: Asignado')).toBeTruthy();
    expect(view.queryByRole('button', { name: 'Confirmar entrega' })).toBeNull();
  } finally {
    jest.mocked(readSession).mockResolvedValue(null);
    jest.mocked(leerConsultaPedidos).mockResolvedValue(null);
  }
});

it('requires a server-confirmed pickup snapshot before queuing departure as the seller', async () => {
  jest.clearAllMocks();
  jest.mocked(Crypto.randomUUID).mockReturnValue('offline-key');
  const local = { ...session('VENDEDOR_PUNTO_FIJO'), usuarioId: 'offline-account' };
  jest.mocked(readSession).mockResolvedValue(local);
  jest.mocked(getValidSession).mockRejectedValue(new NetworkError('Sin conexión'));
  let estado: 'ASIGNADO' | 'RECOGIDO' = 'ASIGNADO';
  jest.mocked(leerConsultaPedidos).mockImplementation(async (_cuenta, tipo) =>
    tipo === 'detalle-vendedor' ? {
      data: { ...vendorOrder, domicilio: { id: 'delivery-id', estado, motivoCancelacion: null } } as never,
      fecha: Date.now(),
    } : null,
  );
  try {
    const view = await render(<PedidoVendedorScreen />);
    await view.findByText(/Datos guardados/);
    expect(view.queryByRole('button', { name: 'Confirmar salida' })).toBeNull();
    estado = 'RECOGIDO';
    await fireEvent.press(view.getByRole('button', { name: 'Actualizar pedido' }));
    await fireEvent.press(await view.findByRole('button', { name: 'Confirmar salida' }));
    await fireEvent.press(view.getByRole('button', { name: 'Sí, ya salió' }));
    await waitFor(() => expect(outbox.encolar).toHaveBeenCalledWith(expect.objectContaining({
      usuarioId: 'offline-account', path: '/api/v1/domicilios/delivery-id/confirmar-salida',
      method: 'POST', idempotencyKey: 'offline-key', intentoPrevio: false,
    })));
    expect(confirmarSalida).not.toHaveBeenCalled();
  } finally {
    jest.mocked(readSession).mockResolvedValue(null);
    jest.mocked(leerConsultaPedidos).mockResolvedValue(null);
  }
});
