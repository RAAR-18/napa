import { render } from '@testing-library/react-native';
import { useFonts } from 'expo-font';
import { Text } from 'react-native';

import { AppFonts } from './AppFonts';

jest.mock('expo-font', () => ({ useFonts: jest.fn() }));

it('espera las fuentes antes de mostrar las pantallas y luego retira la carga', async () => {
  jest.mocked(useFonts).mockReturnValue([false, null]);
  const view = await render(<AppFonts><Text>Pantalla lista</Text></AppFonts>);
  expect(view.getByLabelText('Cargando Ñapa')).toBeTruthy();
  expect(view.queryByText('Pantalla lista')).toBeNull();

  jest.mocked(useFonts).mockReturnValue([true, null]);
  await view.rerender(<AppFonts><Text>Pantalla lista</Text></AppFonts>);
  expect(view.getByText('Pantalla lista')).toBeTruthy();
  expect(view.queryByLabelText('Cargando Ñapa')).toBeNull();
});
