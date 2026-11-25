import { act, fireEvent, render, waitFor } from '@testing-library/react-native';
import * as Notifications from 'expo-notifications';
import { router } from 'expo-router';
import { Platform } from 'react-native';

import NotificacionesScreen, { REFRESCO_NOTIFICACIONES_MS } from '../notificaciones';
import PreferenciasNotificacionesScreen from '../preferencias-notificaciones';
import { NetworkError } from '../../lib/api';
import { getValidSession, readSession } from '../../lib/auth';
import { leerPaginaNotificaciones, listarPaginasGuardadas } from '../../lib/notificaciones-cache';
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
  useFocusEffect: (effect: () => void | (() => void)) => {
    const React = jest.requireActual<typeof import('react')>('react');
    React.useEffect(effect, [effect]);
  },
  router: { back: jest.fn(), push: jest.fn(), replace: jest.fn() },
}));
jest.mock('expo-notifications', () => ({
  getPermissionsAsync: jest.fn(),
  requestPermissionsAsync: jest.fn(),
  getDevicePushTokenAsync: jest.fn(),
  setNotificationHandler: jest.fn(),
  setNotificationChannelAsync: jest.fn(),
  addNotificationReceivedListener: jest.fn(() => ({ remove: jest.fn() })),
  AndroidImportance: { DEFAULT: 3 },
}));
jest.mock('../../lib/auth', () => ({
  clearSession: jest.fn(), getValidSession: jest.fn(), getSessionGeneration: () => 0,
  readSession: jest.fn().mockResolvedValue(null),
}));
jest.mock('../../lib/notificaciones-cache', () => ({
  borrarNotificacionesGuardadas: jest.fn(),
  guardarPaginaNotificaciones: jest.fn(),
  leerPaginaNotificaciones: jest.fn(),
  listarPaginasGuardadas: jest.fn(),
}));
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
  jest.mocked(readSession).mockResolvedValue(null);
});

it('actualiza silenciosamente la bandeja y deja de consultar al salir', async () => {
  jest.useFakeTimers();
  try {
    jest.mocked(listarNotificaciones).mockResolvedValue({ content: [sinLeer], page: 0, size: 20, totalElements: 1 });
    jest.mocked(abrirNotificacion).mockResolvedValue({ ...sinLeer, leida: true });
    const vista = await render(<NotificacionesScreen />);
    await vista.findByRole('button', { name: 'Sin leer: Pedido aceptado' });
    await fireEvent.press(vista.getByRole('button', { name: 'Sin leer: Pedido aceptado' }));
    await vista.findByText(sinLeer.cuerpo);
    jest.mocked(listarNotificaciones).mockResolvedValue({
      content: [{ ...leida, titulo: 'Aviso nuevo' }, { ...sinLeer, leida: true }], page: 0, size: 20, totalElements: 2,
    });
    await act(async () => { jest.advanceTimersByTime(REFRESCO_NOTIFICACIONES_MS); });
    expect(vista.getByText('Aviso nuevo')).toBeTruthy();
    expect(vista.getByText(sinLeer.cuerpo)).toBeTruthy();
    expect(vista.queryByLabelText('Cargando notificaciones')).toBeNull();

    jest.mocked(listarNotificaciones).mockRejectedValueOnce(new NetworkError('Sin conexión'));
    await act(async () => { jest.advanceTimersByTime(REFRESCO_NOTIFICACIONES_MS); });
    expect(vista.getByText('Aviso nuevo')).toBeTruthy();
    await vista.unmount();
    const consultas = jest.mocked(listarNotificaciones).mock.calls.length;
    await act(async () => { jest.advanceTimersByTime(REFRESCO_NOTIFICACIONES_MS * 2); });
    expect(listarNotificaciones).toHaveBeenCalledTimes(consultas);
  } finally {
    jest.useRealTimers();
  }
});

it('consulta al recibir un push y conserva el filtro de no leídas', async () => {
  jest.mocked(listarNotificaciones).mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0 });
  const vista = await render(<NotificacionesScreen />);
  await vista.findByText('No tienes notificaciones.');
  await fireEvent.press(vista.getByRole('radio', { name: 'No leídas' }));
  await vista.findByText('No tienes notificaciones sin leer.');
  jest.mocked(listarNotificaciones).mockResolvedValue({ content: [sinLeer], page: 0, size: 20, totalElements: 1 });
  const listener = jest.mocked(Notifications.addNotificationReceivedListener).mock.calls.at(-1)![0];
  await act(async () => { listener({} as Notifications.Notification); });
  await vista.findByRole('button', { name: 'Sin leer: Pedido aceptado' });
  expect(listarNotificaciones).toHaveBeenLastCalledWith({ page: 0, size: 20, leida: false });
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

it('expands a saved notification without marking it read or exposing its link', async () => {
  jest.mocked(readSession).mockResolvedValue({ ...session('CLIENTE'), usuarioId: 'cliente', expiresAt: 0 });
  jest.mocked(getValidSession).mockRejectedValueOnce(new NetworkError('Sin conexión'));
  const { id, titulo, cuerpo, leida, createdAt } = sinLeer;
  jest.mocked(leerPaginaNotificaciones).mockResolvedValueOnce({
    data: { page: 0, size: 20, totalElements: 1, content: [{ id, titulo, cuerpo, leida, createdAt }] },
    fecha: Date.now(),
  });
  const view = await render(<NotificacionesScreen />);
  await waitFor(() => expect(view.getByText(/Datos guardados/)).toBeTruthy());
  await fireEvent.press(view.getByRole('button', { name: 'Sin leer: Pedido aceptado' }));
  expect(view.getByText(cuerpo)).toBeTruthy();
  expect(view.getByRole('button', { name: 'Sin leer: Pedido aceptado' })).toBeTruthy();
  expect(view.queryByText('Ver pedido')).toBeNull();
  expect(abrirNotificacion).not.toHaveBeenCalled();
  expect(leerPaginaNotificaciones).toHaveBeenCalledWith('cliente', false, 0);
}, 15000);

it('distinguishes an undownloaded filter from an inbox with no results', async () => {
  jest.mocked(readSession).mockResolvedValue({ ...session('CLIENTE'), usuarioId: 'cliente' });
  jest.mocked(getValidSession).mockRejectedValue(new NetworkError('Sin conexión'));
  jest.mocked(leerPaginaNotificaciones).mockResolvedValueOnce({
    data: { page: 0, size: 20, totalElements: 0, content: [] }, fecha: Date.now(),
  }).mockResolvedValueOnce(null);
  jest.mocked(listarPaginasGuardadas).mockResolvedValueOnce([]);
  const view = await render(<NotificacionesScreen />);
  await waitFor(() => expect(view.getByText('No tienes notificaciones.')).toBeTruthy());
  await fireEvent.press(view.getByRole('radio', { name: 'No leídas' }));
  await waitFor(() => expect(view.getByText(/No hay notificaciones descargadas/)).toBeTruthy());
  expect(leerPaginaNotificaciones).toHaveBeenLastCalledWith('cliente', true, 0);
}, 15000);
