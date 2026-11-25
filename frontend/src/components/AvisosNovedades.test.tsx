import { act, render, waitFor } from '@testing-library/react-native';
import { AppState } from 'react-native';
import { AvisosNovedades, INTERVALO_AVISOS_MS } from './AvisosNovedades';
import { listarNotificaciones } from '../lib/notificaciones';
import { readSession } from '../lib/auth';

let mockGeneracion = 1;
jest.mock('expo-router', () => ({ usePathname: () => '/session' }));
jest.mock('../lib/auth', () => ({ readSession: jest.fn(), getSessionGeneration: () => mockGeneracion }));
jest.mock('../lib/notificaciones', () => ({ listarNotificaciones: jest.fn() }));
jest.mock('../lib/push', () => ({ escucharNovedades: () => () => {} }));
jest.mock('./Toast', () => ({ Toast: ({ message }: { message: string }) => {
  const { Text } = jest.requireActual('react-native');
  return <Text>{message}</Text>;
} }));
const anterior = AppState.currentState;
const aviso = { id: 'viejo', titulo: 'Pedido aceptado', cuerpo: 'Ya lo estamos preparando',
  tipo: 'pedidos.pedido-aceptado', leida: false, leidaAt: null, createdAt: '2026-11-25T12:00:00Z', referencia: null };
beforeEach(() => {
  jest.useFakeTimers(); jest.clearAllMocks(); mockGeneracion = 1;
  Object.defineProperty(AppState, 'currentState', { configurable: true, writable: true, value: 'active' });
  jest.mocked(readSession).mockResolvedValue({ usuarioId: 'A' } as never);
  jest.mocked(listarNotificaciones).mockResolvedValue({ page: 0, size: 50, totalElements: 1, content: [aviso] });
});
afterEach(() => { jest.useRealTimers(); Object.defineProperty(AppState, 'currentState', { configurable: true, value: anterior }); });
it('does not alert on old history, alerts on new events and does not repeat them', async () => {
  const view = await render(<AvisosNovedades />);
  await waitFor(() => expect(listarNotificaciones).toHaveBeenCalledTimes(1));
  expect(view.queryByText(/Pedido aceptado/)).toBeNull();
  jest.mocked(listarNotificaciones).mockResolvedValue({ page: 0, size: 50, totalElements: 2,
    content: [{ ...aviso, id: 'nuevo', titulo: 'Pedido en camino' }, aviso] });
  await act(async () => { jest.advanceTimersByTime(INTERVALO_AVISOS_MS); });
  expect(view.getByText(/Pedido en camino/)).toBeTruthy();
  await view.unmount();
});
it('does not query in background or without an account', async () => {
  AppState.currentState = 'background';
  await render(<AvisosNovedades />);
  await act(async () => { jest.advanceTimersByTime(INTERVALO_AVISOS_MS * 2); });
  expect(listarNotificaciones).not.toHaveBeenCalled();
});
it('discards notifications fetched for an old session', async () => {
  let resolver!: (value: Awaited<ReturnType<typeof listarNotificaciones>>) => void;
  jest.mocked(listarNotificaciones).mockReturnValueOnce(new Promise((resolve) => { resolver = resolve; }));
  const view = await render(<AvisosNovedades />);
  await waitFor(() => expect(listarNotificaciones).toHaveBeenCalledTimes(1));
  mockGeneracion += 1;
  jest.mocked(readSession).mockResolvedValue({ usuarioId: 'B' } as never);
  await act(async () => { resolver({ page: 0, size: 50, totalElements: 1, content: [aviso] }); });
  expect(view.queryByText(/Pedido aceptado/)).toBeNull();
});
