import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { router } from 'expo-router';

import VerifyScreen from '../verify';
import { confirmRegistration, requestVerification } from '../../lib/auth';

const mockParams: Record<string, string> = {};

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: () => mockParams,
}));

jest.mock('../../lib/auth', () => ({
  confirmRegistration: jest.fn(),
  requestVerification: jest.fn(),
  resetPassword: jest.fn(),
}));

describe('VerifyScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    for (const key of Object.keys(mockParams)) {
      delete mockParams[key];
    }
    Object.assign(mockParams, { celular: '+573007080825', proposito: 'REGISTRO', tieneCorreo: '1', canal: 'SMS' });
    jest.mocked(requestVerification).mockResolvedValue(120);
  });

  it.each(['VENDEDOR_PUNTO_FIJO', 'VENDEDOR_AMBULANTE', 'CLIENTE', 'DOMICILIARIO'])(
    'dirige el registro verificado de %s al siguiente paso correspondiente', async (rol) => {
      jest.mocked(confirmRegistration).mockResolvedValue({
        rol, accessToken: 'access', refreshToken: 'refresh', tokenType: 'Bearer',
        expiresIn: 900, expiresAt: Date.now() + 900000,
      });
      const view = await render(<VerifyScreen />);
      await fireEvent.press(view.getByRole('button', { name: 'Enviar código por SMS' }));
      await fireEvent.changeText(await view.findByLabelText('Código de verificación'), '123456');
      await fireEvent.press(view.getByRole('button', { name: 'Verificar y continuar' }));
      await waitFor(() => expect(router.replace).toHaveBeenCalledWith(
        rol.startsWith('VENDEDOR') ? '/emprendimiento' : '/session',
      ));
      expect(confirmRegistration).toHaveBeenCalledWith('+573007080825', '123456');
    },
  );

  it('asks for the channel before sending and then waits two minutes to resend', async () => {
    const { getByRole, getByText, queryByLabelText } = await render(<VerifyScreen />);

    expect(getByText('¿Cómo quieres recibir tu código?')).toBeTruthy();
    expect(queryByLabelText('Código de verificación')).toBeNull();
    expect(requestVerification).not.toHaveBeenCalled();

    await fireEvent.press(getByRole('radio', { name: 'Por correo' }));
    await fireEvent.press(getByRole('button', { name: 'Enviar código por tu correo' }));

    await waitFor(() => {
      expect(requestVerification).toHaveBeenCalledWith('+573007080825', 'REGISTRO', 'CORREO');
      expect(getByText('Te enviamos un código por tu correo.')).toBeTruthy();
      expect(getByText(/Podrás pedir otro código por tu correo en 2:00/)).toBeTruthy();
    });
  });

  it('does not offer email when the account has no email', async () => {
    mockParams.tieneCorreo = '0';
    const { getByRole, getByText } = await render(<VerifyScreen />);

    await fireEvent.press(getByRole('radio', { name: 'Por correo' }));

    expect(getByText(/solo puede llegar por SMS/)).toBeTruthy();
  });

  it('asks for the phone when opened from "problemas para iniciar sesión"', async () => {
    delete mockParams.celular;
    delete mockParams.tieneCorreo;
    const { getByLabelText, getByRole } = await render(<VerifyScreen />);

    await fireEvent.changeText(getByLabelText('Celular de tu cuenta'), '300 708 0825');
    await fireEvent.press(getByRole('button', { name: 'Enviar código por SMS' }));

    await waitFor(() => {
      expect(requestVerification).toHaveBeenCalledWith('+573007080825', 'REGISTRO', 'SMS');
    });
  });

  it('lets the account be found by its email when email is chosen', async () => {
    delete mockParams.celular;
    delete mockParams.tieneCorreo;
    const { getByLabelText, getByRole, queryByLabelText } = await render(<VerifyScreen />);

    await fireEvent.press(getByRole('radio', { name: 'Por correo' }));
    expect(queryByLabelText('Celular de tu cuenta')).toBeNull();
    await fireEvent.changeText(getByLabelText('Correo de tu cuenta'), 'Ana@Gmail.com');
    await fireEvent.press(getByRole('button', { name: 'Enviar código por tu correo' }));

    await waitFor(() => {
      expect(requestVerification).toHaveBeenCalledWith('ana@gmail.com', 'REGISTRO', 'CORREO');
    });
  });

  it('keeps a separate wait per channel so email can be requested right after SMS', async () => {
    const { getByRole } = await render(<VerifyScreen />);

    await fireEvent.press(getByRole('button', { name: 'Enviar código por SMS' }));
    await waitFor(() => expect(requestVerification).toHaveBeenCalledTimes(1));
    await fireEvent.press(getByRole('radio', { name: 'Por correo' }));
    await fireEvent.press(getByRole('button', { name: 'Enviar otro código por tu correo' }));

    await waitFor(() => {
      expect(requestVerification).toHaveBeenLastCalledWith('+573007080825', 'REGISTRO', 'CORREO');
    });
  });
});

it('recommends email when the registered account has one and keeps SMS available', async () => {
  jest.clearAllMocks();
  for (const key of Object.keys(mockParams)) delete mockParams[key];
  Object.assign(mockParams, { celular: '+573007080825', proposito: 'REGISTRO', tieneCorreo: '1' });
  jest.mocked(requestVerification).mockResolvedValue(120);
  const view = await render(<VerifyScreen />);
  expect(view.getByRole('radio', { name: 'Por correo' }).props.accessibilityState.checked).toBe(true);
  await fireEvent.press(view.getByRole('button', { name: 'Enviar código por tu correo' }));
  await waitFor(() => expect(requestVerification).toHaveBeenCalledWith('+573007080825', 'REGISTRO', 'CORREO'));
});
