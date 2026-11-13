import * as Location from 'expo-location';
import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { router } from 'expo-router';

import EmprendimientoDetalleScreen from '../emprendimiento-detalle';
import MiEmprendimientoScreen from '../mi-emprendimiento';
import { ApiError } from '../../lib/api';
import { getValidSession } from '../../lib/auth';
import {
  editarMiEmprendimiento,
  eliminarMiEmprendimiento,
  obtenerEmprendimiento,
  obtenerMiEmprendimiento,
  obtenerVendedorEmprendimiento,
  type Emprendimiento,
  type EmprendimientoDetalle,
} from '../../lib/emprendimientos';

jest.mock('expo-router', () => ({
  router: { back: jest.fn(), push: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: () => ({ emprendimientoId: 'business-id' }),
}));
jest.mock('expo-crypto', () => ({ randomUUID: jest.fn(() => 'delete-key') }));
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
jest.mock('../../lib/emprendimientos', () => ({
  ...jest.requireActual('../../lib/emprendimientos'),
  editarMiEmprendimiento: jest.fn(),
  eliminarMiEmprendimiento: jest.fn(),
  obtenerEmprendimiento: jest.fn(),
  obtenerMiEmprendimiento: jest.fn(),
  obtenerVendedorEmprendimiento: jest.fn(),
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

const mine: Emprendimiento = {
  id: 'business-id',
  nombre: 'Frutas Rosa',
  categoria: 'FRUTAS_VERDURAS',
  descripcion: 'Frutas frescas',
  horaApertura: '06:00:00',
  horaCierre: '18:00:00',
  tipoVendedor: 'VENDEDOR_PUNTO_FIJO',
  estado: 'ACTIVO',
  ubicacion: { latitud: 11.24, longitud: -74.21, direccion: 'Calle 15', referencia: null },
};

beforeEach(() => {
  jest.clearAllMocks();
});

describe('mi emprendimiento (RF-29, RF-30)', () => {
  it('saves only the changed fields', async () => {
    jest.mocked(getValidSession).mockResolvedValue(session('VENDEDOR_PUNTO_FIJO'));
    jest.mocked(obtenerMiEmprendimiento).mockResolvedValue(mine);
    jest.mocked(editarMiEmprendimiento).mockResolvedValue({ ...mine, horaCierre: '19:30:00' });
    const view = await render(<MiEmprendimientoScreen />);

    await waitFor(() => expect(view.getByLabelText('Cierra (HH:mm)').props.value).toBe('18:00'));
    await fireEvent.changeText(view.getByLabelText('Cierra (HH:mm)'), '19:30');
    await fireEvent.press(view.getByText('Guardar cambios'));

    expect(editarMiEmprendimiento).toHaveBeenCalledWith({ horaCierre: '19:30' });
    await waitFor(() => expect(view.getByText('Cambios guardados. Los clientes ya los ven.')).toBeTruthy());
  });

  it('a street seller updates the zone with the current position', async () => {
    jest.mocked(getValidSession).mockResolvedValue(session('VENDEDOR_AMBULANTE'));
    jest.mocked(obtenerMiEmprendimiento).mockResolvedValue({ ...mine, ubicacion: { ...mine.ubicacion, direccion: null } });
    jest.mocked(Location.requestForegroundPermissionsAsync).mockResolvedValue({ status: 'granted' } as never);
    jest.mocked(Location.getCurrentPositionAsync).mockResolvedValue({
      coords: { latitude: 11.25, longitude: -74.2 },
    } as never);
    jest.mocked(editarMiEmprendimiento).mockResolvedValue(mine);
    const view = await render(<MiEmprendimientoScreen />);

    await fireEvent.press(await view.findByText('Estoy aquí'));
    await waitFor(() => expect(view.getByLabelText('Latitud').props.value).toBe('11.250000'));
    await fireEvent.press(view.getByText('Guardar cambios'));

    expect(editarMiEmprendimiento).toHaveBeenCalledWith({
      ubicacion: { latitud: 11.25, longitud: -74.2, direccion: null, referencia: null },
    });
  });

  it('does not send anything when nothing changed', async () => {
    jest.mocked(getValidSession).mockResolvedValue(session('VENDEDOR_PUNTO_FIJO'));
    jest.mocked(obtenerMiEmprendimiento).mockResolvedValue(mine);
    const view = await render(<MiEmprendimientoScreen />);

    await fireEvent.press(await view.findByText('Guardar cambios'));

    expect(view.getByText('No has cambiado ningún dato.')).toBeTruthy();
    expect(editarMiEmprendimiento).not.toHaveBeenCalled();
  });

  it('explains that open orders block the deletion', async () => {
    jest.mocked(getValidSession).mockResolvedValue(session('VENDEDOR_PUNTO_FIJO'));
    jest.mocked(obtenerMiEmprendimiento).mockResolvedValue(mine);
    jest.mocked(eliminarMiEmprendimiento).mockRejectedValue(new ApiError(
      'Resuelve tus pedidos pendientes antes de eliminar tu emprendimiento.', 409, 'HAS_PENDING_ORDERS',
      { pedidosPendientes: 2 },
    ));
    const view = await render(<MiEmprendimientoScreen />);

    await fireEvent.press(await view.findByRole('button', { name: 'Eliminar emprendimiento' }));
    await fireEvent.press(view.getByText('Sí, eliminar'));

    expect(eliminarMiEmprendimiento).toHaveBeenCalledWith('delete-key');
    await waitFor(() => expect(view.getByText(/Tienes 2 pedidos sin cerrar\./)).toBeTruthy());
  });

  it('deletes after confirming and offers to create a new one', async () => {
    jest.mocked(getValidSession).mockResolvedValue(session('VENDEDOR_PUNTO_FIJO'));
    jest.mocked(obtenerMiEmprendimiento).mockResolvedValue(mine);
    jest.mocked(eliminarMiEmprendimiento).mockResolvedValue(undefined);
    const view = await render(<MiEmprendimientoScreen />);

    await fireEvent.press(await view.findByRole('button', { name: 'Eliminar emprendimiento' }));
    await fireEvent.press(view.getByText('Sí, eliminar'));

    await waitFor(() => expect(view.getByText('Crear mi emprendimiento')).toBeTruthy());
    expect(view.getByText('Eliminaste tu emprendimiento. Ya no aparece para los clientes.')).toBeTruthy();
  });

  it('offers to create one when the seller has none', async () => {
    jest.mocked(getValidSession).mockResolvedValue(session('VENDEDOR_AMBULANTE'));
    jest.mocked(obtenerMiEmprendimiento).mockRejectedValue(
      new ApiError('Aún no tienes un emprendimiento activo.', 404, 'NOT_FOUND'),
    );
    const view = await render(<MiEmprendimientoScreen />);

    await fireEvent.press(await view.findByText('Crear mi emprendimiento'));
    expect(router.push).toHaveBeenCalledWith('/emprendimiento');
  });
});

describe('detalle del emprendimiento (RF-32, RF-34)', () => {
  const detail: EmprendimientoDetalle = {
    id: 'business-id',
    nombre: 'Frutas Rosa',
    categoria: 'FRUTAS_VERDURAS',
    tipoVendedor: 'PUNTO_FIJO',
    descripcion: 'Frutas frescas',
    horaApertura: '06:00',
    horaCierre: '18:00',
    abierto: true,
    ubicacion: { latitud: 11.24, longitud: -74.21, direccion: 'Calle 15', referencia: 'Frente al parque' },
    modalidadesEntrega: ['RECOGIDA', 'DOMICILIO'],
    ofreceDomicilio: true,
  };

  beforeEach(() => {
    jest.mocked(getValidSession).mockResolvedValue(session('CLIENTE'));
  });

  it('shows a fixed point with its map, schedule, delivery modes and seller', async () => {
    jest.mocked(obtenerEmprendimiento).mockResolvedValue(detail);
    jest.mocked(obtenerVendedorEmprendimiento).mockResolvedValue({
      nombre: 'Rosa Martínez', fotoUrl: null, tipoVendedor: 'PUNTO_FIJO', miembroDesde: '2026-09-01',
    });
    const view = await render(<EmprendimientoDetalleScreen />);

    await waitFor(() => expect(view.getByText('Abierto ahora')).toBeTruthy());
    expect(view.getByLabelText('Mapa del punto de venta')).toBeTruthy();
    expect(view.getByText('Calle 15')).toBeTruthy();
    expect(view.getByText('• Recogida en el punto')).toBeTruthy();
    expect(view.getByText('• Domicilio')).toBeTruthy();
    await waitFor(() => expect(view.getByText('Te atiende Rosa Martínez')).toBeTruthy());

    await fireEvent.press(view.getByText('Ver productos'));
    expect(router.push).toHaveBeenCalledWith({
      pathname: '/catalogo', params: { emprendimientoId: 'business-id', tipoVendedor: 'PUNTO_FIJO' },
    });
  });

  it('a street seller has no map and only direct delivery', async () => {
    jest.mocked(obtenerEmprendimiento).mockResolvedValue({
      ...detail, tipoVendedor: 'AMBULANTE', modalidadesEntrega: ['ENTREGA_DIRECTA'], ofreceDomicilio: false,
      abierto: false,
    });
    jest.mocked(obtenerVendedorEmprendimiento).mockRejectedValue(new Error('offline'));
    const view = await render(<EmprendimientoDetalleScreen />);

    await waitFor(() => expect(view.getByText('Cerrado')).toBeTruthy());
    expect(view.queryByLabelText('Mapa del punto de venta')).toBeNull();
    expect(view.getByText('• Entrega directa: el vendedor te lo lleva')).toBeTruthy();
    expect(view.getByText('Zona habitual')).toBeTruthy();
  });

  it('tells the client when the business is no longer available', async () => {
    jest.mocked(obtenerEmprendimiento).mockRejectedValue(
      new ApiError('El emprendimiento ya no está disponible.', 404, 'NOT_FOUND'),
    );
    const view = await render(<EmprendimientoDetalleScreen />);

    await waitFor(() => expect(view.getByText('El emprendimiento ya no está disponible.')).toBeTruthy());
    expect(view.queryByText('Reintentar')).toBeNull();
    expect(view.queryByText('Ver productos')).toBeNull();
  });
});
