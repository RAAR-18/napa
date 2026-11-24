import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { router } from 'expo-router';

import EmprendimientoScreen from '../emprendimiento';
import { getValidSession } from '../../lib/auth';
import { createEmprendimiento } from '../../lib/emprendimientos';
import type { Emprendimiento } from '../../lib/emprendimientos';
import { ApiError } from '../../lib/api';
import {
  LocationPermissionDeniedError,
  LocationUnavailableError,
  requestDeviceLocation,
} from '../../lib/location';

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

jest.mock('../../lib/location', () => ({
  LocationPermissionDeniedError: class LocationPermissionDeniedError extends Error {
    constructor(
      message = 'No se concedió permiso de ubicación. Puedes escribir las coordenadas manualmente.',
    ) {
      super(message);
      this.name = 'LocationPermissionDeniedError';
    }
  },
  LocationUnavailableError: class LocationUnavailableError extends Error {
    constructor(
      message = 'No se pudo obtener la ubicación del dispositivo.',
    ) {
      super(message);
      this.name = 'LocationUnavailableError';
    }
  },
  requestDeviceLocation: jest.fn(),
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
      expect(createEmprendimiento).toHaveBeenCalledWith({
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

  it('requests device location permission and populates coordinates automatically', async () => {
    jest.mocked(requestDeviceLocation).mockResolvedValue({
      latitude: 4.711,
      longitude: -74.0721,
    });
    const created: Emprendimiento = {
      id: 'business-id-2',
      nombre: 'Frutas Frescas',
      categoria: 'FRUTAS_VERDURAS',
      descripcion: 'Frutas recién cosechadas.',
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

    await fireEvent.changeText(
      getByLabelText('Nombre del emprendimiento'),
      'Frutas Frescas',
    );
    await fireEvent.press(getByRole('radio', { name: 'Frutas y verduras' }));
    await fireEvent.changeText(
      getByLabelText('Descripción'),
      'Frutas recién cosechadas.',
    );
    await fireEvent.changeText(getByLabelText('Hora de apertura'), '08:00');
    await fireEvent.changeText(getByLabelText('Hora de cierre'), '18:00');
    await fireEvent.changeText(
      getByLabelText('Dirección del punto (obligatoria)'),
      'Carrera 7 # 10-20',
    );

    await fireEvent.press(getByRole('button', { name: 'Usar mi ubicación actual' }));

    await waitFor(() => {
      expect(requestDeviceLocation).toHaveBeenCalledTimes(1);
      expect(getByLabelText('Latitud').props.value).toBe('4.711');
      expect(getByLabelText('Longitud').props.value).toBe('-74.0721');
    });

    await fireEvent.press(getByRole('button', { name: 'Publicar emprendimiento' }));

    await waitFor(() => {
      expect(createEmprendimiento).toHaveBeenCalledWith({
        nombre: 'Frutas Frescas',
        categoria: 'FRUTAS_VERDURAS',
        descripcion: 'Frutas recién cosechadas.',
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

  it('displays a friendly error when device location permission is denied', async () => {
    jest
      .mocked(requestDeviceLocation)
      .mockRejectedValue(new LocationPermissionDeniedError());

    const { getByLabelText, getByRole, getByText } = await render(
      <EmprendimientoScreen />,
    );

    await waitFor(() => expect(getByLabelText('Nombre del emprendimiento')).toBeTruthy());

    await fireEvent.press(getByRole('button', { name: 'Usar mi ubicación actual' }));

    await waitFor(() => {
      expect(
        getByText(
          'No se concedió permiso de ubicación. Puedes escribir las coordenadas manualmente.',
        ),
      ).toBeTruthy();
    });
  });

  it('displays error banner when obtaining device coordinates fails', async () => {
    jest
      .mocked(requestDeviceLocation)
      .mockRejectedValue(
        new LocationUnavailableError(
          'No se pudo obtener la ubicación del dispositivo: GPS timeout.',
        ),
      );

    const { getByLabelText, getByRole, getByText } = await render(
      <EmprendimientoScreen />,
    );

    await waitFor(() => expect(getByLabelText('Nombre del emprendimiento')).toBeTruthy());

    await fireEvent.press(getByRole('button', { name: 'Usar mi ubicación actual' }));

    await waitFor(() => {
      expect(
        getByText('No se pudo obtener la ubicación del dispositivo: GPS timeout.'),
      ).toBeTruthy();
    });
  });
  it('submits custom category text only when Other is selected', async () => {
    const view = await render(<EmprendimientoScreen />);
    await waitFor(() => expect(view.getByLabelText('Nombre del emprendimiento')).toBeTruthy());
    await fireEvent.changeText(view.getByLabelText('Nombre del emprendimiento'), 'Artesanías de Ana');
    await fireEvent.changeText(view.getByLabelText('Tipo de negocio (opcional)'), '  Artesanías  ');
    await fireEvent.changeText(view.getByLabelText('Descripción'), 'Hecho a mano');
    await fireEvent.changeText(view.getByLabelText('Hora de apertura'), '08:00');
    await fireEvent.changeText(view.getByLabelText('Hora de cierre'), '18:00');
    await fireEvent.changeText(view.getByLabelText('Latitud'), '11.24');
    await fireEvent.changeText(view.getByLabelText('Longitud'), '-74.21');
    await fireEvent.changeText(view.getByLabelText('Dirección del punto (obligatoria)'), 'Calle 1');
    await fireEvent.press(view.getByRole('button', { name: 'Publicar emprendimiento' }));
    await waitFor(() => expect(createEmprendimiento).toHaveBeenCalledWith(expect.objectContaining({
      categoria: 'OTROS', categoriaPersonalizada: 'Artesanías',
    })));
  });

});
