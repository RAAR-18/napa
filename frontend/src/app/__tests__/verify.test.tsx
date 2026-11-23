import { fireEvent, render, waitFor } from '@testing-library/react-native';

import VerifyScreen from '../verify';
import { requestVerification } from '../../lib/auth';

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
    Object.assign(mockParams, { celular: '+573007080825', proposito: 'REGISTRO', tieneCorreo: '1' });
    jest.mocked(requestVerification).mockResolvedValue(120);
  });

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
      expect(getByText(/Podrás pedir otro código en 2:00/)).toBeTruthy();
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
      expect(requestVerification).toHaveBeenCalledWith('3007080825', 'REGISTRO', 'SMS');
    });
  });
});
