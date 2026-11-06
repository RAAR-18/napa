import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { router } from 'expo-router';

import RegisterScreen from '../register';
import { register } from '../../lib/auth';

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn() },
}));

jest.mock('../../lib/auth', () => ({
  register: jest.fn(),
}));

describe('RegisterScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.mocked(register).mockResolvedValue();
  });

  it('submits the selected account details and opens phone verification', async () => {
    const { getByLabelText, getByRole } = await render(<RegisterScreen />);

    await fireEvent.changeText(getByLabelText('Nombre'), 'Ana Vendedora');
    await fireEvent.changeText(getByLabelText('Celular'), '+573001234567');
    await fireEvent.changeText(
      getByLabelText('Correo (opcional)'),
      'ana@example.com',
    );
    await fireEvent.press(getByRole('radio', { name: 'Vendedor ambulante' }));
    await fireEvent.changeText(getByLabelText('Contraseña'), 'clave-segura');
    await fireEvent.changeText(
      getByLabelText('Confirma tu contraseña'),
      'clave-segura',
    );
    await fireEvent.press(
      getByRole('button', { name: 'Crear cuenta y verificar celular' }),
    );

    await waitFor(() => {
      expect(register).toHaveBeenCalledWith({
        nombre: 'Ana Vendedora',
        celular: '+573001234567',
        correo: 'ana@example.com',
        password: 'clave-segura',
        confirmacionPassword: 'clave-segura',
        rol: 'VENDEDOR_AMBULANTE',
      });
      expect(router.push).toHaveBeenCalledWith({
        pathname: '/verify',
        params: { celular: '+573001234567', proposito: 'REGISTRO' },
      });
    });
  });
});
