import { createElement } from 'react';
import { act, render } from '@testing-library/react-native';
import { AppState } from 'react-native';
import { useAutoRefresh, AUTO_REFRESH_MS } from './useAutoRefresh';

jest.mock('expo-router', () => ({
  useFocusEffect: (effect: () => void | (() => void)) => {
    const React = jest.requireActual<typeof import('react')>('react');
    React.useEffect(effect, [effect]);
  },
}));

const estadoOriginal = AppState.currentState;
beforeEach(() => {
  jest.useFakeTimers();
  Object.defineProperty(AppState, 'currentState', { configurable: true, writable: true, value: 'active' });
});
afterEach(() => {
  jest.useRealTimers();
  Object.defineProperty(AppState, 'currentState', { configurable: true, writable: true, value: estadoOriginal });
});

it('refreshes periodically and stops when disabled or unmounted', async () => {
  const refrescar = jest.fn();
  function Pantalla({ activo }: { activo: boolean }) {
    useAutoRefresh(refrescar, activo);
    return null;
  }
  const view = await render(createElement(Pantalla, { activo: true }));
  await act(async () => { jest.advanceTimersByTime(AUTO_REFRESH_MS); });
  expect(refrescar).toHaveBeenCalledTimes(1);
  await view.rerender(createElement(Pantalla, { activo: false }));
  await act(async () => { jest.advanceTimersByTime(AUTO_REFRESH_MS); });
  expect(refrescar).toHaveBeenCalledTimes(1);
  await view.unmount();
});
