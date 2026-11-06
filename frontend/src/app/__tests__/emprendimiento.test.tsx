import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { router } from 'expo-router';

import EmprendimientoScreen from '../emprendimiento';
import { getValidSession } from '../../lib/auth';
import {
  createEmprendimiento,
  type Emprendimiento,
} from '../../lib/emprendimientos';
import { ApiError } from '../../lib/api';

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn() },
}));

jest.mock('../../lib/auth', () => ({
  clearSession: jest.fn(),
  getValidSession: jest.fn(),
}));

jest.mock('../../lib/emprendimientos', () => ({
  createEmprendimiento: jest.fn(),
}));

const session = {
  accessToken: 'access',
  refreshToken: 'refresh',
  expiresIn: 900,
  expiresAt: Date.now() + 900_000,
  tokenType: 'Bearer',
  rol: 'VENDEDOR_PUNTO_FIJO',
} as const;

describe('EmprendimientoScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.mocked(getValidSession).mockResolvedValue(session);
  });

  it('validates required fields and publishes a fixed-location business', async () => {
    const created: Emprendimiento = {
      id: 'business-id',
      nombre: 'Frutas de Ana',
      categoria: 'FRUTAS_VERDURAS',
      descripcion: 'Frutas frescas.',
      horaApertura: '08:00',
      horaCierre: '18:00',
      tipoVendedor: 'VENDEDOR_PUNTO_FIJO',
      estado: 'ACTIVO',
      ubicacion: {
        latitud: 4.711,
        longitud: -74.0721,
        direccion: 'Carrera 7 # 10-20',
        referencia: null,
      },
    };
    jest.mocked(createEmprendimiento).mockResolvedValue(created);
    const { getByLabelText, getByRole, getByText } = await render(
      <EmprendimientoScreen />,
    );

    await waitFor(() => expect(getByLabelText('Nombre del emprendimiento')).toBeTruthy());
    await fireEvent.press(getByRole('button', { name: 'Publicar emprendimiento' }));
    expect(getByText(/Revisa los campos obligatorios/)).toBeTruthy();

    await fireEvent.changeText(
      getByLabelText('Nombre del emprendimiento'),
      ' Frutas de Ana ',
    );
    await fireEvent.press(getByRole('radio', { name: 'Frutas y verduras' }));
    await fireEvent.changeText(
      getByLabelText('Descripción'),
      ' Frutas frescas. ',
    );
    await fireEvent.changeText(getByLabelText('Hora de apertura'), '08:00');
    await fireEvent.changeText(getByLabelText('Hora de cierre'), '18:00');
    await fireEvent.changeText(getByLabelText('Latitud'), '4.711');
    await fireEvent.changeText(getByLabelText('Longitud'), '-74.0721');
    await fireEvent.changeText(
      getByLabelText('Dirección del punto (obligatoria)'),
      ' Carrera 7 # 10-20 ',
    );
    await fireEvent.press(getByRole('button', { name: 'Publicar emprendimiento' }));

    await waitFor(() => {
      expect(createEmprendimiento).toHaveBeenCalledWith(session, {
        nombre: 'Frutas de Ana',
        categoria: 'FRUTAS_VERDURAS',
        descripcion: 'Frutas frescas.',
        horaApertura: '08:00',
        horaCierre: '18:00',
        ubicacion: {
          latitud: 4.711,
          longitud: -74.0721,
          direccion: 'Carrera 7 # 10-20',
          referencia: null,
        },
      });
      expect(getByText('Creación exitosa')).toBeTruthy();
    });
  });

  it('shows the backend conflict message for an existing active business', async () => {
    jest
      .mocked(createEmprendimiento)
      .mockRejectedValue(new ApiError('Ya tienes un emprendimiento activo.', 409, 'CONFLICT'));
    const { getByLabelText, getByRole, getByText } = await render(
      <EmprendimientoScreen />,
    );

    await waitFor(() => expect(getByLabelText('Nombre del emprendimiento')).toBeTruthy());
    await fireEvent.changeText(getByLabelText('Nombre del emprendimiento'), 'Mi negocio');
    await fireEvent.changeText(getByLabelText('Descripción'), 'Descripción válida');
    await fireEvent.changeText(getByLabelText('Hora de apertura'), '08:00');
    await fireEvent.changeText(getByLabelText('Hora de cierre'), '18:00');
    await fireEvent.changeText(getByLabelText('Latitud'), '4.711');
    await fireEvent.changeText(getByLabelText('Longitud'), '-74.0721');
    await fireEvent.changeText(
      getByLabelText('Dirección del punto (obligatoria)'),
      'Carrera 7 # 10-20',
    );
    await fireEvent.press(getByRole('button', { name: 'Publicar emprendimiento' }));

    await waitFor(() =>
      expect(getByText('Ya tienes un emprendimiento activo.')).toBeTruthy(),
    );
    expect(router.replace).not.toHaveBeenCalled();
  });
});
