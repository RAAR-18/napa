import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { router } from 'expo-router';

import LoginScreen from '../login';
import { ApiError } from '../../lib/api';
import { login } from '../../lib/auth';

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: () => ({}),
}));

jest.mock('../../lib/auth', () => ({
  login: jest.fn(),
}));

describe('LoginScreen', () => {
  it('takes an unverified account to choose how to verify it', async () => {
    jest.mocked(login).mockRejectedValue(
      new ApiError('Debes verificar tu cuenta antes de iniciar sesión.', 403, 'ACCOUNT_NOT_VERIFIED', {
        celular: '+573007080825',
        tieneCorreo: false,
      }),
    );
    const { getByLabelText, getByRole } = await render(<LoginScreen />);

    await fireEvent.changeText(getByLabelText('Celular o correo'), '3007080825');
    await fireEvent.changeText(getByLabelText('Contraseña'), 'ClaveSegura123');
    await fireEvent.press(getByRole('button', { name: 'Iniciar sesión' }));

    await waitFor(() => {
      expect(router.push).toHaveBeenCalledWith({
        pathname: '/verify',
        params: { celular: '+573007080825', proposito: 'REGISTRO', tieneCorreo: '0' },
      });
    });
  });
});
