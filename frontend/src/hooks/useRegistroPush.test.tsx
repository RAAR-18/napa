import { act, render, waitFor } from '@testing-library/react-native';
import { AppState, type AppStateStatus } from 'react-native';
import { usePathname } from 'expo-router';

import { useRegistroPush } from './useRegistroPush';
import { getSessionGeneration, readSession } from '../lib/auth';
import { registrarPush } from '../lib/push';

jest.mock('expo-router', () => ({ usePathname: jest.fn(() => '/emprendimientos') }));
jest.mock('../lib/auth', () => ({ getSessionGeneration: jest.fn(() => 1), readSession: jest.fn() }));
jest.mock('../lib/push', () => ({ registrarPush: jest.fn(async () => true) }));

let cambioDeEstado: ((estado: AppStateStatus) => void) | undefined;

function Pantalla() {
  useRegistroPush();
  return null;
}

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(getSessionGeneration).mockReturnValue(1);
  jest.mocked(usePathname).mockReturnValue('/emprendimientos');
  jest.spyOn(AppState, 'addEventListener').mockImplementation((_tipo, listener) => {
    cambioDeEstado = listener as (estado: AppStateStatus) => void;
    return { remove: jest.fn() } as never;
  });
});

it('registra el celular al abrir la app con sesión, sin pasar por Mi cuenta', async () => {
  jest.mocked(readSession).mockResolvedValue({ usuarioId: 'u1' } as never);
  await render(<Pantalla />);
  await waitFor(() => expect(registrarPush).toHaveBeenCalledWith(true));
});

it('no registra nada sin sesión y lo hace al iniciar sesión', async () => {
  jest.mocked(readSession).mockResolvedValue(null);
  const vista = await render(<Pantalla />);
  await waitFor(() => expect(readSession).toHaveBeenCalled());
  expect(registrarPush).not.toHaveBeenCalled();

  // Iniciar sesión cambia la generación y lleva a la pantalla de inicio del rol.
  jest.mocked(readSession).mockResolvedValue({ usuarioId: 'u1' } as never);
  jest.mocked(getSessionGeneration).mockReturnValue(2);
  jest.mocked(usePathname).mockReturnValue('/pedidos-vendedor');
  await vista.rerender(<Pantalla />);
  await waitFor(() => expect(registrarPush).toHaveBeenCalledTimes(1));
});

it('pide el permiso una sola vez por sesión aunque se navegue', async () => {
  jest.mocked(readSession).mockResolvedValue({ usuarioId: 'u1' } as never);
  const vista = await render(<Pantalla />);
  await waitFor(() => expect(registrarPush).toHaveBeenCalledTimes(1));
  jest.mocked(usePathname).mockReturnValue('/notificaciones');
  await vista.rerender(<Pantalla />);
  await act(async () => { await Promise.resolve(); });
  expect(registrarPush).toHaveBeenCalledTimes(1);
});

it('al volver a primer plano reintenta sin volver a pedir permiso', async () => {
  jest.mocked(readSession).mockResolvedValue({ usuarioId: 'u1' } as never);
  await render(<Pantalla />);
  await waitFor(() => expect(registrarPush).toHaveBeenCalledTimes(1));
  await act(async () => { cambioDeEstado?.('active'); });
  await waitFor(() => expect(registrarPush).toHaveBeenLastCalledWith(false));
});
