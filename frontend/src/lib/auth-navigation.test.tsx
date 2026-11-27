import { Stack } from 'expo-router';
import { act, renderRouter, screen, testRouter } from 'expo-router/testing-library';
import { Text } from 'react-native';

import { replaceAuthHistory } from './auth-navigation';

const Page = () => <Text>Pantalla</Text>;

describe('Historial al cambiar de sesión', () => {
  it.each([
    ['iniciar sesión', ['/login'], '/emprendimientos'],
    ['verificar el registro', ['/register', '/verify'], '/emprendimiento'],
    ['cerrar sesión', ['/emprendimientos', '/mis-pedidos', '/session'], '/'],
    ['eliminar una cuenta', ['/emprendimientos', '/session', '/perfil'], '/'],
    ['caducar la sesión', ['/emprendimientos', '/session'], '/login'],
  ])('impide retroceder al historial anterior al %s', async (_caso, historial, destino) => {
    const view = await renderRouter({
      _layout: () => <Stack screenOptions={{ headerShown: false, animation: 'none' }} />,
      index: Page,
      login: Page,
      register: Page,
      verify: Page,
      emprendimientos: Page,
      emprendimiento: Page,
      'mis-pedidos': Page,
      session: Page,
      perfil: Page,
    }, { initialUrl: '/' });

    for (const ruta of historial) await testRouter.push(ruta);
    expect(testRouter.canGoBack()).toBe(true);

    await act(() => replaceAuthHistory(destino));

    expect(screen).toHavePathname(destino);
    expect(testRouter.canGoBack()).toBe(false);
    expect(view.getRouterState()?.routes).toHaveLength(1);
  });
});
