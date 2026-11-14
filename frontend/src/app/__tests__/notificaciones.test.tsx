import { fireEvent, render, waitFor } from '@testing-library/react-native';
import * as Notifications from 'expo-notifications';
import { router } from 'expo-router';
import { Platform } from 'react-native';

import NotificacionesScreen from '../notificaciones';
import PreferenciasNotificacionesScreen from '../preferencias-notificaciones';
import { getValidSession } from '../../lib/auth';
import {
  abrirNotificacion,
  actualizarPreferencias,
  darDeBajaDispositivo,
  destinoNotificacion,
  listarNotificaciones,
  listarPreferencias,
  registrarDispositivo,
  type Notificacion,
} from '../../lib/notificaciones';
import { darDeBajaPush, registrarPush } from '../../lib/push';

jest.mock('expo-router', () => ({
  router: { back: jest.fn(), push: jest.fn(), replace: jest.fn() },
}));
jest.mock('expo-notifications', () => ({
  getPermissionsAsync: jest.fn(),
  requestPermissionsAsync: jest.fn(),
  getDevicePushTokenAsync: jest.fn(),
  setNotificationHandler: jest.fn(),
}));
jest.mock('../../lib/auth', () => ({ clearSession: jest.fn(), getValidSession: jest.fn() }));
jest.mock('../../lib/notificaciones', () => ({
  ...jest.requireActual('../../lib/notificaciones'),
  abrirNotificacion: jest.fn(),
  actualizarPreferencias: jest.fn(),
  darDeBajaDispositivo: jest.fn(),
  listarNotificaciones: jest.fn(),
  listarPreferencias: jest.fn(),
  registrarDispositivo: jest.fn(),
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

const sinLeer: Notificacion = {
  id: 'n-1',
  tipo: 'pedidos.pedido-aceptado',
  titulo: 'Pedido aceptado',
  cuerpo: 'El vendedor aceptó tu pedido. Transfiere el total para continuar.',
  leida: false,
  leidaAt: null,
  createdAt: '2026-11-13T18:00:00Z',
  referencia: { tipo: 'PEDIDO', id: 'order-id' },
};
const leida: Notificacion = {
  id: 'n-2',
  tipo: 'reportes.reporte-resuelto',
  titulo: 'Reporte resuelto',
  cuerpo: 'El administrador resolvió tu reporte.',
  leida: true,
  leidaAt: '2026-11-12T10:00:00Z',
  createdAt: '2026-11-12T09:00:00Z',
  referencia: null,
};

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(getValidSession).mockResolvedValue(session('CLIENTE'));
});

describe('notificaciones (RF-38)', () => {
  it('lists the notifications marking the unread ones', async () => {
    jest.mocked(listarNotificaciones).mockResolvedValue({ content: [sinLeer, leida], page: 0, size: 20, totalElements: 2 });
    const view = await render(<NotificacionesScreen />);

    await waitFor(() => expect(view.getByRole('button', { name: 'Sin leer: Pedido aceptado' })).toBeTruthy());
    expect(view.getByRole('button', { name: 'Reporte resuelto' })).toBeTruthy();
    expect(listarNotificaciones).toHaveBeenCalledWith({ page: 0, size: 20 });

    await fireEvent.press(view.getByRole('radio', { name: 'No leídas' }));
    await waitFor(() => expect(listarNotificaciones).toHaveBeenLastCalledWith({ page: 0, size: 20, leida: false }));
  });

  it('shows an empty message', async () => {
    jest.mocked(listarNotificaciones).mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0 });
    const view = await render(<NotificacionesScreen />);

    await waitFor(() => expect(view.getByText('No tienes notificaciones.')).toBeTruthy());
  });

  it('opening one marks it as read, shows its content and leads to the order', async () => {
    jest.mocked(listarNotificaciones).mockResolvedValue({ content: [sinLeer], page: 0, size: 20, totalElements: 1 });
    jest.mocked(abrirNotificacion).mockResolvedValue({ ...sinLeer, leida: true, leidaAt: '2026-11-13T18:05:00Z' });
    const view = await render(<NotificacionesScreen />);

    await waitFor(() => expect(view.getByRole('button', { name: 'Sin leer: Pedido aceptado' })).toBeTruthy());
    await fireEvent.press(view.getByRole('button', { name: 'Sin leer: Pedido aceptado' }));

    expect(abrirNotificacion).toHaveBeenCalledWith('n-1');
    await waitFor(() => expect(view.getByRole('button', { name: 'Pedido aceptado' })).toBeTruthy());
    expect(view.getByText(/Transfiere el total/)).toBeTruthy();
    await fireEvent.press(view.getByRole('button', { name: 'Ver pedido' }));
    expect(router.push).toHaveBeenCalledWith({ pathname: '/pedido-cliente', params: { pedidoId: 'order-id' } });
  });

  it('does not mark again a notification already read', async () => {
    jest.mocked(listarNotificaciones).mockResolvedValue({ content: [leida], page: 0, size: 20, totalElements: 1 });
    const view = await render(<NotificacionesScreen />);

    await waitFor(() => expect(view.getByRole('button', { name: 'Reporte resuelto' })).toBeTruthy());
    await fireEvent.press(view.getByRole('button', { name: 'Reporte resuelto' }));

    expect(abrirNotificacion).not.toHaveBeenCalled();
    expect(view.getByText('El administrador resolvió tu reporte.')).toBeTruthy();
    expect(view.queryByRole('button', { name: 'Ver pedido' })).toBeNull();
  });

  it('routes each reference according to the role', () => {
    const domicilio = { referencia: { tipo: 'DOMICILIO' as const, id: 'delivery-id' } };
    expect(destinoNotificacion(domicilio, 'DOMICILIARIO')).toEqual({
      pathname: '/domicilio-domiciliario', params: { domicilioId: 'delivery-id' },
    });
    expect(destinoNotificacion(domicilio, 'CLIENTE')).toBeNull();
    expect(destinoNotificacion({ referencia: { tipo: 'PEDIDO', id: 'order-id' } }, 'VENDEDOR_PUNTO_FIJO')).toEqual({
      pathname: '/pedido-vendedor', params: { pedidoId: 'order-id' },
    });
    expect(destinoNotificacion({ referencia: null }, 'CLIENTE')).toBeNull();
  });
});

describe('preferencias de notificación (RF-39)', () => {
  const preferencias = [
    { tipo: 'pedidos.pedido-aceptado', habilitada: true },
    { tipo: 'pedidos.pedido-rechazado', habilitada: false },
    { tipo: 'domicilios.domicilio-publicado', habilitada: true },
  ];

  it('shows the types of the role, including disabled ones, and saves only the changed type', async () => {
    jest.mocked(listarPreferencias).mockResolvedValue(preferencias);
    jest.mocked(actualizarPreferencias).mockResolvedValue([
      { tipo: 'pedidos.pedido-aceptado', habilitada: false }, ...preferencias.slice(1),
    ]);
    const view = await render(<PreferenciasNotificacionesScreen />);

    await waitFor(() => expect(view.getByLabelText('Tu pedido fue aceptado')).toBeTruthy());
    expect(view.getByLabelText('Tu pedido fue rechazado').props.value).toBe(false);
    expect(view.queryByLabelText('Nuevos domicilios disponibles')).toBeNull();

    await fireEvent(view.getByLabelText('Tu pedido fue aceptado'), 'valueChange', false);

    expect(actualizarPreferencias).toHaveBeenCalledWith([{ tipo: 'pedidos.pedido-aceptado', habilitada: false }]);
    await waitFor(() => expect(view.getByLabelText('Tu pedido fue aceptado').props.value).toBe(false));
  });

  it('reverts the switch when saving fails', async () => {
    jest.mocked(listarPreferencias).mockResolvedValue(preferencias);
    jest.mocked(actualizarPreferencias).mockRejectedValue(new Error('Sin conexión.'));
    const view = await render(<PreferenciasNotificacionesScreen />);

    await waitFor(() => expect(view.getByLabelText('Tu pedido fue rechazado')).toBeTruthy());
    await fireEvent(view.getByLabelText('Tu pedido fue rechazado'), 'valueChange', true);

    await waitFor(() => expect(view.getByText('Sin conexión.')).toBeTruthy());
    expect(view.getByLabelText('Tu pedido fue rechazado').props.value).toBe(false);
  });
});

describe('registro del push', () => {
  const originalOS = Platform.OS;

  beforeEach(() => {
    Object.defineProperty(Platform, 'OS', { configurable: true, value: 'android' });
  });
  afterEach(() => {
    Object.defineProperty(Platform, 'OS', { configurable: true, value: originalOS });
  });

  it('registers the native token after the user allows notifications', async () => {
    jest.mocked(Notifications.getPermissionsAsync).mockResolvedValue({ status: 'undetermined' } as never);
    jest.mocked(Notifications.requestPermissionsAsync).mockResolvedValue({ status: 'granted' } as never);
    jest.mocked(Notifications.getDevicePushTokenAsync).mockResolvedValue({ type: 'android', data: 'fcm-token' });
    jest.mocked(registrarDispositivo).mockResolvedValue(undefined);

    await expect(registrarPush()).resolves.toBe(true);
    expect(registrarDispositivo).toHaveBeenCalledWith('fcm-token', 'ANDROID');
  });

  it('does nothing when the user denies notifications or the build has no push', async () => {
    jest.mocked(Notifications.getPermissionsAsync).mockResolvedValue({ status: 'denied' } as never);
    jest.mocked(Notifications.requestPermissionsAsync).mockResolvedValue({ status: 'denied' } as never);
    await expect(registrarPush()).resolves.toBe(false);

    jest.mocked(Notifications.getPermissionsAsync).mockResolvedValue({ status: 'granted' } as never);
    jest.mocked(Notifications.getDevicePushTokenAsync).mockRejectedValue(new Error('No Firebase'));
    await expect(registrarPush()).resolves.toBe(false);
    expect(registrarDispositivo).not.toHaveBeenCalled();
  });

  it('unregisters the device on logout without asking for permission', async () => {
    jest.mocked(Notifications.getPermissionsAsync).mockResolvedValue({ status: 'granted' } as never);
    jest.mocked(Notifications.getDevicePushTokenAsync).mockResolvedValue({ type: 'android', data: 'fcm-token' });
    jest.mocked(darDeBajaDispositivo).mockRejectedValue(new Error('offline'));

    await expect(darDeBajaPush()).resolves.toBeUndefined();
    expect(darDeBajaDispositivo).toHaveBeenCalledWith('fcm-token');
    expect(Notifications.requestPermissionsAsync).not.toHaveBeenCalled();
  });
});
