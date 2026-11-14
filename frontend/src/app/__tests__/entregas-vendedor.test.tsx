import * as Location from 'expo-location';
import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { router } from 'expo-router';

import PedidoVendedorScreen from '../pedido-vendedor';
import UbicacionEntregaScreen from '../ubicacion-entrega';
import { ApiError } from '../../lib/api';
import { getValidSession } from '../../lib/auth';
import {
  confirmarEntregaDirecta,
  confirmarRetiro,
  iniciarEntregaDirecta,
  marcarListoParaRecoger,
  obtenerPedidoVendedor,
  type PedidoVendedorDetalle,
} from '../../lib/pedidos';

jest.mock('expo-router', () => ({
  router: { back: jest.fn(), push: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: () => ({ pedidoId: 'order-id' }),
}));
jest.mock('expo-crypto', () => ({ randomUUID: jest.fn(() => 'action-key') }));
jest.mock('expo-location', () => ({
  Accuracy: { Balanced: 3 },
  getCurrentPositionAsync: jest.fn(),
  requestForegroundPermissionsAsync: jest.fn(),
}));
jest.mock('react-native-maps', () => {
  const { Text, View } = jest.requireActual('react-native');
  const MapView = ({ children, accessibilityLabel }: { children: unknown; accessibilityLabel: string }) => (
    <View accessibilityLabel={accessibilityLabel}>{children as never}</View>
  );
  const Marker = ({ title }: { title: string }) => <Text>{`marcador ${title}`}</Text>;
  return { __esModule: true, default: MapView, Marker };
});
jest.mock('../../lib/auth', () => ({ clearSession: jest.fn(), getValidSession: jest.fn() }));
jest.mock('../../lib/pedidos', () => ({
  ...jest.requireActual('../../lib/pedidos'),
  confirmarEntregaDirecta: jest.fn(),
  confirmarRetiro: jest.fn(),
  iniciarEntregaDirecta: jest.fn(),
  marcarListoParaRecoger: jest.fn(),
  obtenerPedidoVendedor: jest.fn(),
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

function order(overrides: Partial<PedidoVendedorDetalle>): PedidoVendedorDetalle {
  return {
    id: 'order-id',
    estado: 'ACEPTADO',
    modalidad: 'ENTREGA_DIRECTA',
    reserva: null,
    createdAt: '2026-11-13T12:00:00Z',
    updatedAt: '2026-11-13T12:00:00Z',
    cliente: { nombre: 'Ana Pérez' },
    items: [{
      productoId: 'product-id', nombre: 'Mango', unidad: 'KG', cantidad: 1,
      precioUnitario: 3500, subtotal: 3500, productoEliminado: false,
    }],
    subtotal: 3500,
    tarifaDomicilio: null,
    total: 3500,
    medioPago: 'EFECTIVO',
    pago: { estado: 'PENDIENTE_CONFIRMACION' },
    ubicacionEntrega: { latitud: 4.711, longitud: -74.0721, direccion: 'Calle 15 # 3-20', referencia: 'Casa azul' },
    domicilio: null,
    ...overrides,
  };
}

beforeEach(() => {
  jest.clearAllMocks();
});

describe('entrega directa (RF-36, RF-37)', () => {
  beforeEach(() => {
    jest.mocked(getValidSession).mockResolvedValue(session('VENDEDOR_AMBULANTE'));
  });

  it('starts the delivery after confirming and refreshes the order', async () => {
    jest.mocked(obtenerPedidoVendedor)
      .mockResolvedValueOnce(order({}))
      .mockResolvedValueOnce(order({ estado: 'EN_CAMINO' }));
    jest.mocked(iniciarEntregaDirecta).mockResolvedValue({
      id: 'order-id', estado: 'EN_CAMINO', modalidad: 'ENTREGA_DIRECTA', reserva: null, updatedAt: '2026-11-13T12:10:00Z',
    });
    const view = await render(<PedidoVendedorScreen />);

    await fireEvent.press(await view.findByText('Iniciar entrega'));
    expect(view.getByText('¿Sales a entregar el pedido? El cliente verá que vas en camino.')).toBeTruthy();
    await fireEvent.press(view.getByText('Sí, voy en camino'));

    expect(iniciarEntregaDirecta).toHaveBeenCalledWith('order-id', 'action-key');
    await waitFor(() => expect(view.getByText('Confirmar entrega y cobro')).toBeTruthy());
    expect(view.getByText('Entrega iniciada. Avisamos al cliente que vas en camino.')).toBeTruthy();
  });

  it('explains from when an early reservation can start', async () => {
    jest.mocked(obtenerPedidoVendedor).mockResolvedValue(order({ reserva: '2026-11-20T15:00:00Z' }));
    jest.mocked(iniciarEntregaDirecta).mockRejectedValue(
      new ApiError('No disponible', 409, 'RESERVATION_NOT_DUE', { disponibleDesde: '2026-11-20T14:45:00Z' }),
    );
    const view = await render(<PedidoVendedorScreen />);

    await fireEvent.press(await view.findByText('Iniciar entrega'));
    await fireEvent.press(view.getByText('Sí, voy en camino'));

    const expected = new Date('2026-11-20T14:45:00Z').toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
    await waitFor(() => expect(view.getByText(`Podrás iniciar la entrega desde ${expected}.`)).toBeTruthy());
  });

  it('confirms delivery and cash payment with the amount in the question', async () => {
    jest.mocked(obtenerPedidoVendedor).mockResolvedValue(order({ estado: 'EN_CAMINO' }));
    jest.mocked(confirmarEntregaDirecta).mockResolvedValue({
      id: 'order-id', estado: 'ENTREGADO', modalidad: 'ENTREGA_DIRECTA', updatedAt: '2026-11-13T12:30:00Z',
      pago: { id: 'pay', estado: 'CONFIRMADO', medio: 'EFECTIVO', monto: 3500, confirmadoAt: '2026-11-13T12:30:00Z' },
    });
    const view = await render(<PedidoVendedorScreen />);

    await fireEvent.press(await view.findByText('Confirmar entrega y cobro'));
    expect(view.getByText(/recibiste \$3\.500 COP en efectivo\?/)).toBeTruthy();
    await fireEvent.press(view.getByText('Sí, entregado y cobrado'));

    expect(confirmarEntregaDirecta).toHaveBeenCalledWith('order-id', 'action-key');
  });

  it('opens the customer location only for an active direct delivery', async () => {
    jest.mocked(obtenerPedidoVendedor).mockResolvedValue(order({}));
    const view = await render(<PedidoVendedorScreen />);

    await fireEvent.press(await view.findByText('Ver ubicación en el mapa'));
    expect(router.push).toHaveBeenCalledWith({ pathname: '/ubicacion-entrega', params: { pedidoId: 'order-id' } });
  });
});

describe('recogida en el punto fijo (RF-64, RF-65)', () => {
  beforeEach(() => {
    jest.mocked(getValidSession).mockResolvedValue(session('VENDEDOR_PUNTO_FIJO'));
  });

  it('marks a pickup order as ready', async () => {
    jest.mocked(obtenerPedidoVendedor).mockResolvedValue(order({ modalidad: 'RECOGIDA', ubicacionEntrega: null }));
    jest.mocked(marcarListoParaRecoger).mockResolvedValue({
      id: 'order-id', estado: 'LISTO_PARA_RECOGER', modalidad: 'RECOGIDA', reserva: null, updatedAt: '2026-11-13T12:10:00Z',
    });
    const view = await render(<PedidoVendedorScreen />);

    expect(await view.findByText('Marcar listo para recoger')).toBeTruthy();
    expect(view.queryByText('Iniciar entrega')).toBeNull();
    await fireEvent.press(view.getByText('Marcar listo para recoger'));
    await fireEvent.press(view.getByText('Sí, está listo'));

    expect(marcarListoParaRecoger).toHaveBeenCalledWith('order-id', 'action-key');
  });

  it('confirms the pickup of a ready order paid by transfer', async () => {
    jest.mocked(obtenerPedidoVendedor).mockResolvedValue(order({
      modalidad: 'RECOGIDA', estado: 'LISTO_PARA_RECOGER', medioPago: 'CUENTA_BANCARIA', ubicacionEntrega: null,
    }));
    jest.mocked(confirmarRetiro).mockRejectedValue(
      new ApiError('El pedido no está en un estado que permita este cambio.', 409, 'INVALID_STATE', { estadoActual: 'ENTREGADO' }),
    );
    const view = await render(<PedidoVendedorScreen />);

    await fireEvent.press(await view.findByText('Confirmar retiro y cobro'));
    expect(view.getByText(/la transferencia por \$3\.500 COP en tu cuenta/)).toBeTruthy();
    await fireEvent.press(view.getByText('Sí, retirado y cobrado'));

    expect(confirmarRetiro).toHaveBeenCalledWith('order-id', 'action-key');
    await waitFor(() =>
      expect(view.getByText('El pedido no está en un estado que permita este cambio.')).toBeTruthy(),
    );
  });
});

describe('ubicación de entrega (RF-35)', () => {
  beforeEach(() => {
    jest.mocked(getValidSession).mockResolvedValue(session('VENDEDOR_AMBULANTE'));
  });

  it('shows the customer on the map with the address and the distance', async () => {
    jest.mocked(obtenerPedidoVendedor).mockResolvedValue(order({ estado: 'EN_CAMINO' }));
    jest.mocked(Location.requestForegroundPermissionsAsync).mockResolvedValue({ status: 'granted' } as never);
    jest.mocked(Location.getCurrentPositionAsync).mockResolvedValue({
      coords: { latitude: 4.72, longitude: -74.0721 },
    } as never);
    const view = await render(<UbicacionEntregaScreen />);

    await waitFor(() => expect(view.getByLabelText('Mapa con la ubicación del cliente')).toBeTruthy());
    expect(view.getByText('marcador Cliente')).toBeTruthy();
    expect(view.getByText('Calle 15 # 3-20')).toBeTruthy();
    expect(view.getByText('Referencia: Casa azul')).toBeTruthy();
    expect(view.getByText('Estás a 1 km en línea recta.')).toBeTruthy();
  });

  it('keeps the address when there is no location permission', async () => {
    jest.mocked(obtenerPedidoVendedor).mockResolvedValue(order({}));
    jest.mocked(Location.requestForegroundPermissionsAsync).mockResolvedValue({ status: 'denied' } as never);
    const view = await render(<UbicacionEntregaScreen />);

    await waitFor(() => expect(view.getByText('Calle 15 # 3-20')).toBeTruthy());
    expect(view.getByText(/Sin permiso de ubicación/)).toBeTruthy();
    expect(view.queryByText(/en línea recta/)).toBeNull();
  });

  it('does not show the location of a finished order', async () => {
    jest.mocked(obtenerPedidoVendedor).mockResolvedValue(order({ estado: 'ENTREGADO', ubicacionEntrega: null }));
    jest.mocked(Location.requestForegroundPermissionsAsync).mockResolvedValue({ status: 'denied' } as never);
    const view = await render(<UbicacionEntregaScreen />);

    await waitFor(() => expect(view.getByText('El pedido no está disponible.')).toBeTruthy());
    expect(view.queryByLabelText('Mapa con la ubicación del cliente')).toBeNull();
  });
});
