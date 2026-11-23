import { fireEvent, render, waitFor } from '@testing-library/react-native';

import VerifyScreen from '../verify';
import { requestVerification } from '../../lib/auth';

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: () => ({
    celular: '+573007080825',
    proposito: 'REGISTRO',
    canal: 'SMS',
    envio: 'FALLIDO',
  }),
}));

jest.mock('../../lib/auth', () => ({
  confirmRegistration: jest.fn(),
  requestVerification: jest.fn(),
  resetPassword: jest.fn(),
}));

describe('VerifyScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.mocked(requestVerification).mockResolvedValue();
  });

  it('explains a failed send and lets the user ask for the code by email instead', async () => {
    const { getByRole, getByText } = await render(<VerifyScreen />);

    expect(getByText(/No pudimos enviar el código por SMS/)).toBeTruthy();

    await fireEvent.press(getByRole('radio', { name: 'Por correo' }));
    await fireEvent.press(getByRole('button', { name: 'Enviar otro código por tu correo' }));

    await waitFor(() => {
      expect(requestVerification).toHaveBeenCalledWith('+573007080825', 'REGISTRO', 'CORREO');
      expect(getByText('Te enviamos un código nuevo por tu correo.')).toBeTruthy();
    });
  });
});
