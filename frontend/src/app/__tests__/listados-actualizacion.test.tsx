import { act, render, waitFor } from '@testing-library/react-native';
import { AppState } from 'react-native';
import { router } from 'expo-router';
import * as Location from 'expo-location';
import Disponibles from '../domicilios-disponibles';
import EnCurso from '../domicilios-en-curso';
import MisPedidos, { REFRESCO_MIS_PEDIDOS_MS } from '../mis-pedidos';
import { ApiError } from '../../lib/api';
import { clearSession, getValidSession } from '../../lib/auth';
import { listarDomiciliosDisponibles, listarDomiciliosEnCurso } from '../../lib/domicilios';
import { consultarListadoCliente } from '../../lib/pedidos-cliente-consulta';

let mockEnfocada = true;
jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn() },
  useFocusEffect: (effect: () => void | (() => void)) => {
    const React = jest.requireActual<typeof import('react')>('react');
    const enfocada = mockEnfocada;
    React.useEffect(() => enfocada ? effect() : undefined, [effect, enfocada]);
  },
}));
jest.mock('expo-location', () => ({
  Accuracy: { Balanced: 3 }, requestForegroundPermissionsAsync: jest.fn(), getCurrentPositionAsync: jest.fn(),
}));
jest.mock('../../lib/auth', () => ({
  clearSession: jest.fn(), getValidSession: jest.fn(), readSession: jest.fn(async () => null), getSessionGeneration: () => 0,
}));
jest.mock('../../hooks/useOutbox', () => ({ useAccionesPendientes: () => [] }));
jest.mock('../../lib/push', () => ({ escucharNovedades: () => () => undefined }));
jest.mock('../../lib/pedidos-cliente-consulta', () => ({ consultarListadoCliente: jest.fn() }));
jest.mock('../../lib/pedidos-cliente-cache', () => ({ guardarConsultaPedidos: jest.fn(async () => undefined), leerConsultaPedidos: jest.fn(async () => null) }));
jest.mock('../../lib/domicilios', () => ({
  ...jest.requireActual('../../lib/domicilios'), listarDomiciliosDisponibles: jest.fn(), listarDomiciliosEnCurso: jest.fn(),
}));

const original = AppState.currentState;
const pagina = { content: [], page: 0, size: 20, totalElements: 0, radioMetros: 5000 };
let listeners: ((estado: 'active' | 'background') => void)[];
beforeEach(() => {
  jest.useFakeTimers();
  jest.clearAllMocks();
  mockEnfocada = true;
  listeners = [];
  Object.defineProperty(AppState, 'currentState', { configurable: true, writable: true, value: 'active' });
  jest.spyOn(AppState, 'addEventListener').mockImplementation((_tipo, listener) => {
    listeners.push(listener);
    return { remove: () => { listeners = listeners.filter((item) => item !== listener); } };
  });
  jest.mocked(getValidSession).mockResolvedValue({ rol: 'DOMICILIARIO', accessToken: 'test', refreshToken: 'test', expiresIn: 900, expiresAt: Date.now() + 900000, tokenType: 'Bearer' });
  jest.mocked(Location.requestForegroundPermissionsAsync).mockResolvedValue({ status: 'granted' } as never);
  jest.mocked(Location.getCurrentPositionAsync).mockResolvedValue({ coords: { latitude: 11.2, longitude: -74.2 } } as never);
  jest.mocked(listarDomiciliosDisponibles).mockResolvedValue(pagina);
  jest.mocked(listarDomiciliosEnCurso).mockResolvedValue([]);
  jest.mocked(consultarListadoCliente).mockResolvedValue({ origen: 'servidor', data: pagina });
});
afterEach(() => {
  jest.useRealTimers();
  jest.restoreAllMocks();
  Object.defineProperty(AppState, 'currentState', { configurable: true, writable: true, value: original });
});
async function avanzar(ms: number) { await act(async () => { jest.advanceTimersByTime(ms); }); }

it('pauses available deliveries on blur and background, and resumes on foreground', async () => {
  const view = await render(<Disponibles />);
  await waitFor(() => expect(listarDomiciliosDisponibles).toHaveBeenCalledTimes(1));
  await avanzar(15000);
  expect(listarDomiciliosDisponibles).toHaveBeenCalledTimes(2);
  AppState.currentState = 'background';
  await avanzar(30000);
  expect(listarDomiciliosDisponibles).toHaveBeenCalledTimes(2);
  AppState.currentState = 'active';
  await act(async () => { listeners.forEach((listener) => listener('active')); });
  expect(listarDomiciliosDisponibles).toHaveBeenCalledTimes(3);
  mockEnfocada = false;
  await view.rerender(<Disponibles />);
  await avanzar(30000);
  expect(listarDomiciliosDisponibles).toHaveBeenCalledTimes(3);
  mockEnfocada = true;
  await view.rerender(<Disponibles />);
  await waitFor(() => expect(listarDomiciliosDisponibles).toHaveBeenCalledTimes(4));
});
it('does not overlap available delivery requests while one is pending', async () => {
  let resolver!: (value: typeof pagina) => void;
  jest.mocked(listarDomiciliosDisponibles).mockReturnValueOnce(new Promise((resolve) => { resolver = resolve; }));
  await render(<Disponibles />);
  await waitFor(() => expect(listarDomiciliosDisponibles).toHaveBeenCalledTimes(1));
  await avanzar(45000);
  expect(listarDomiciliosDisponibles).toHaveBeenCalledTimes(1);
  await act(async () => { resolver(pagina); });
  await avanzar(15000);
  expect(listarDomiciliosDisponibles).toHaveBeenCalledTimes(2);
});
it('ignores an old unauthorized response after leaving available deliveries', async () => {
  let rechazar!: (error: Error) => void;
  jest.mocked(listarDomiciliosDisponibles).mockReturnValueOnce(new Promise((_resolve, reject) => { rechazar = reject; }));
  const view = await render(<Disponibles />);
  await waitFor(() => expect(listarDomiciliosDisponibles).toHaveBeenCalledTimes(1));
  mockEnfocada = false;
  await view.rerender(<Disponibles />);
  await act(async () => { rechazar(new ApiError('Sesión anterior', 401, 'UNAUTHORIZED')); });
  expect(clearSession).not.toHaveBeenCalled();
  expect(router.replace).not.toHaveBeenCalled();
});
it.each([
  ['client orders', MisPedidos, () => jest.mocked(consultarListadoCliente), REFRESCO_MIS_PEDIDOS_MS],
  ['active deliveries', EnCurso, () => jest.mocked(listarDomiciliosEnCurso), 30000],
] as const)('refreshes %s and reloads on returning to the screen', async (_nombre, Screen, consulta, intervalo) => {
  const view = await render(<Screen />);
  await waitFor(() => expect(consulta()).toHaveBeenCalledTimes(1));
  await avanzar(intervalo);
  await waitFor(() => expect(consulta()).toHaveBeenCalledTimes(2));
  mockEnfocada = false;
  await view.rerender(<Screen />);
  await avanzar(60000);
  expect(consulta()).toHaveBeenCalledTimes(2);
  mockEnfocada = true;
  await view.rerender(<Screen />);
  await waitFor(() => expect(consulta()).toHaveBeenCalledTimes(3));
});
