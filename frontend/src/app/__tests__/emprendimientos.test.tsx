import { fireEvent, render, waitFor } from '@testing-library/react-native';
import * as Location from 'expo-location';
import { router } from 'expo-router';

import EmprendimientosScreen from '../emprendimientos';
import { getValidSession } from '../../lib/auth';
import {
  readEmprendimientosPage,
  saveEmprendimientosPage,
} from '../../lib/emprendimientos-cache';
import {
  listEmprendimientos,
  type EmprendimientosPage,
} from '../../lib/emprendimientos';

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn() },
}));

jest.mock('expo-location', () => ({
  Accuracy: { Balanced: 3 },
  requestForegroundPermissionsAsync: jest.fn(),
  getCurrentPositionAsync: jest.fn(),
}));

jest.mock('../../lib/auth', () => ({
  clearSession: jest.fn(),
  getValidSession: jest.fn(),
}));

jest.mock('../../lib/emprendimientos', () => ({
  listEmprendimientos: jest.fn(),
}));

jest.mock('../../lib/emprendimientos-cache', () => ({
  readEmprendimientosPage: jest.fn(),
  saveEmprendimientosPage: jest.fn(),
}));

const session = {
  accessToken: 'access',
  refreshToken: 'refresh',
  expiresIn: 900,
  expiresAt: Date.now() + 900_000,
  tokenType: 'Bearer',
  rol: 'CLIENTE',
} as const;

const emptyPage: EmprendimientosPage = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
};

const oneBusinessPage: EmprendimientosPage = {
  content: [
    {
      id: 'business-id',
      nombre: 'Frutas de Ana',
      categoria: 'FRUTAS_VERDURAS',
      tipoVendedor: 'AMBULANTE',
      abierto: true,
      horaApertura: '08:00',
      horaCierre: '18:00',
      distanciaMetros: null,
      ubicacion: {
        latitud: 4.711,
        longitud: -74.0721,
        direccion: null,
      },
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
};

describe('EmprendimientosScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.mocked(getValidSession).mockResolvedValue(session);
    jest.mocked(listEmprendimientos).mockResolvedValue(emptyPage);
    jest.mocked(saveEmprendimientosPage).mockResolvedValue();
    jest.mocked(readEmprendimientosPage).mockResolvedValue(null);
    jest
      .mocked(Location.requestForegroundPermissionsAsync)
      .mockResolvedValue({ status: 'denied' } as Location.LocationPermissionResponse);
  });

  it('shows the empty-list message', async () => {
    const { getByText } = await render(<EmprendimientosScreen />);

    await waitFor(() =>
      expect(getByText('No hay emprendimientos disponibles.')).toBeTruthy(),
    );
  });

  it('applies the selected seller filter', async () => {
    const { getByRole } = await render(<EmprendimientosScreen />);

    await waitFor(() => expect(listEmprendimientos).toHaveBeenCalled());
    fireEvent.press(getByRole('radio', { name: 'Ambulantes' }));

    await waitFor(() =>
      expect(listEmprendimientos).toHaveBeenLastCalledWith(
        session,
        expect.objectContaining({ tipoVendedor: 'AMBULANTE', page: 0 }),
      ),
    );
  });

  it('keeps the default ordering when location permission is denied', async () => {
    const { getByRole, getByText } = await render(<EmprendimientosScreen />);

    await waitFor(() => expect(listEmprendimientos).toHaveBeenCalled());
    fireEvent.press(getByRole('button', { name: 'Ordenar por cercanía' }));

    await waitFor(() =>
      expect(
        getByText(
          'Sin permiso de ubicación; se muestra el listado en orden reciente.',
        ),
      ).toBeTruthy(),
    );
    expect(listEmprendimientos).toHaveBeenCalledTimes(1);
    expect(router.replace).not.toHaveBeenCalled();
  });

  it('requests location on demand and applies the selected radius', async () => {
    jest.mocked(Location.requestForegroundPermissionsAsync).mockResolvedValue({
      status: 'granted',
    } as Location.LocationPermissionResponse);
    jest.mocked(Location.getCurrentPositionAsync).mockResolvedValue({
      coords: { latitude: 4.711, longitude: -74.0721 },
    } as Location.LocationObject);
    const { getByRole } = await render(<EmprendimientosScreen />);

    await waitFor(() => expect(listEmprendimientos).toHaveBeenCalled());
    fireEvent.press(getByRole('button', { name: 'Ordenar por cercanía' }));

    await waitFor(() =>
      expect(listEmprendimientos).toHaveBeenLastCalledWith(
        session,
        expect.objectContaining({
          lat: 4.711,
          lng: -74.0721,
          cercania: true,
        }),
      ),
    );
    fireEvent.press(getByRole('radio', { name: '2 km' }));

    await waitFor(() =>
      expect(listEmprendimientos).toHaveBeenLastCalledWith(
        session,
        expect.objectContaining({ radioMetros: 2000, cercania: true }),
      ),
    );
  });

  it('shows the last cached page when the API is unreachable', async () => {
    jest
      .mocked(listEmprendimientos)
      .mockRejectedValue(new Error('No se pudo conectar con la API.'));
    jest.mocked(readEmprendimientosPage).mockResolvedValue({
      page: oneBusinessPage,
      updatedAt: '2025-01-15T10:30:00.000Z',
    });
    const { getByText } = await render(<EmprendimientosScreen />);

    await waitFor(() =>
      expect(getByText('Frutas de Ana')).toBeTruthy(),
    );
    expect(
      getByText('Sin conexión. Estás viendo el último listado guardado.'),
    ).toBeTruthy();
    expect(getByText(/Última actualización:/)).toBeTruthy();
  });
});
