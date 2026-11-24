import { fireEvent, render, screen } from '@testing-library/react-native';
import { router, usePathname } from 'expo-router';
import * as ReactNative from 'react-native';

import { readSession, type StoredSession } from '../lib/auth';
import { AppShell, NapaNavigationProvider } from './AppShell';

jest.mock('expo-router', () => ({
  router: { back: jest.fn(), canGoBack: jest.fn(), replace: jest.fn(), push: jest.fn() },
  usePathname: jest.fn(),
}));
jest.mock('../lib/auth', () => ({ readSession: jest.fn() }));
jest.mock('react-native', () => {
  const native = jest.requireActual('react-native');
  Object.defineProperty(native, 'useWindowDimensions', {
    configurable: true,
    value: jest.fn(),
  });
  return native;
});

const backLabel = 'Volver a la pantalla anterior';
const session: StoredSession = {
  accessToken: 'test-access', refreshToken: 'test-refresh', expiresIn: 3600,
  expiresAt: Date.now() + 3600000, tokenType: 'Bearer', rol: 'CLIENTE',
};

async function renderPage(path: string, role = 'CLIENTE', width = 393) {
  jest.mocked(usePathname).mockReturnValue(path);
  jest.mocked(readSession).mockResolvedValue({ ...session, rol: role });
  jest.mocked(ReactNative.useWindowDimensions).mockReturnValue({
    width, height: 852, scale: 3, fontScale: 1,
  });
  return render(
    <NapaNavigationProvider>
      <AppShell><ReactNative.Text>Contenido de prueba</ReactNative.Text></AppShell>
    </NapaNavigationProvider>,
  );
}

describe('Regresar desde la cabecera movil', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.mocked(router.canGoBack).mockReturnValue(true);
    jest.spyOn(ReactNative.Keyboard, 'dismiss').mockImplementation(() => undefined);
  });
  afterEach(() => jest.restoreAllMocks());

  it('cierra el teclado y vuelve a la pantalla anterior', async () => {
    await renderPage('/perfil');
    await fireEvent.press(screen.getByRole('button', { name: backLabel }));

    expect(ReactNative.Keyboard.dismiss).toHaveBeenCalled();
    expect(router.back).toHaveBeenCalledTimes(1);
    expect(router.replace).not.toHaveBeenCalled();
  });

  it('vuelve al inicio del rol cuando un enlace no tiene historial', async () => {
    jest.mocked(router.canGoBack).mockReturnValue(false);
    await renderPage('/pedido-vendedor', 'VENDEDOR_PUNTO_FIJO');
    await fireEvent.press(screen.getByRole('button', { name: 'Abrir menú' }));
    await screen.findByRole('button', { name: 'Pedidos recibidos' });
    await fireEvent.press(screen.getByRole('button', { name: 'Cerrar menú de navegación' }));
    await fireEvent.press(screen.getByRole('button', { name: backLabel }));

    expect(router.replace).toHaveBeenCalledWith('/pedidos-vendedor');
    expect(router.back).not.toHaveBeenCalled();
  });

  it('ofrece regreso tambien en el formulario de inicio de sesion', async () => {
    jest.mocked(router.canGoBack).mockReturnValue(false);
    await renderPage('/login');
    await fireEvent.press(screen.getByRole('button', { name: backLabel }));

    expect(router.replace).toHaveBeenCalledWith('/');
  });

  it('cierra primero el menu abierto sin cambiar de pantalla', async () => {
    await renderPage('/perfil');
    await fireEvent.press(screen.getByRole('button', { name: 'Abrir menú' }));
    expect(screen.getByRole('button', { name: 'Cerrar menú de navegación' })).toBeTruthy();
    await fireEvent.press(screen.getByRole('button', { name: backLabel }));

    expect(screen.queryByRole('button', { name: 'Cerrar menú de navegación' })).toBeNull();
    expect(router.back).not.toHaveBeenCalled();
    expect(router.replace).not.toHaveBeenCalled();
  });

  it('no ofrece retroceso en el inicio del rol ni en la bienvenida', async () => {
    const view = await renderPage('/emprendimientos');
    expect(screen.queryByRole('button', { name: backLabel })).toBeNull();
    await view.unmount();
    await renderPage('/');
    expect(screen.queryByRole('button', { name: backLabel })).toBeNull();
  });

  it('mantiene la navegacion de escritorio sin la cabecera movil', async () => {
    await renderPage('/perfil', 'CLIENTE', 1280);
    expect(screen.queryByRole('button', { name: backLabel })).toBeNull();
  });
});
