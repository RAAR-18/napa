import { jsonHeaders, requestJson } from './api';
import {
  createEmprendimiento,
  listEmprendimientos,
  type Emprendimiento,
} from './emprendimientos';
import { withValidSession, type StoredSession } from './auth';

jest.mock('expo-crypto', () => ({ randomUUID: () => 'idempotency-id' }));
jest.mock('./api', () => ({
  jsonHeaders: jest.fn(() => ({ Authorization: 'Bearer access' })),
  requestJson: jest.fn(),
}));

jest.mock('./auth', () => ({ withValidSession: jest.fn() }));

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(withValidSession).mockImplementation(async (request) =>
    request({
      accessToken: 'access',
      refreshToken: 'refresh',
      expiresIn: 900,
      expiresAt: Date.now() + 900_000,
      tokenType: 'Bearer',
      rol: 'CLIENTE',
    }),
  );
});

describe('createEmprendimiento', () => {
  it('sends the authenticated creation request with an idempotency key', async () => {
    const session: StoredSession = {
      accessToken: 'access',
      refreshToken: 'refresh',
      expiresIn: 900,
      expiresAt: Date.now() + 900_000,
      tokenType: 'Bearer',
      rol: 'VENDEDOR_PUNTO_FIJO',
    };
    const input = {
      nombre: 'Frutas de Ana',
      categoria: 'FRUTAS_VERDURAS' as const,
      descripcion: 'Frutas frescas de temporada.',
      horaApertura: '08:00',
      horaCierre: '18:00',
      ubicacion: {
        latitud: 4.711,
        longitud: -74.0721,
        direccion: 'Carrera 7 # 10-20',
        referencia: 'Frente al parque',
      },
    };
    const created: Emprendimiento = {
      ...input,
      id: 'business-id',
      tipoVendedor: 'VENDEDOR_PUNTO_FIJO',
      estado: 'ACTIVO',
    };
    jest.mocked(requestJson).mockResolvedValue(created);

    jest.mocked(withValidSession).mockImplementation(async (request) => request(session));
    await expect(createEmprendimiento(input)).resolves.toEqual(created);

    expect(jsonHeaders).toHaveBeenCalledWith({
      accessToken: 'access',
      idempotencyKey: 'idempotency-id',
    });
    expect(requestJson).toHaveBeenCalledWith(
      '/api/v1/emprendimientos',
      expect.objectContaining({
        method: 'POST',
        headers: { Authorization: 'Bearer access' },
        body: JSON.stringify(input),
      }),
    );
  });
});

describe('listEmprendimientos', () => {
  it('sends paging, seller, and proximity filters with the client session', async () => {
    const page = { content: [], page: 1, size: 20, totalElements: 0 };
    jest.mocked(requestJson).mockResolvedValue(page);

    await expect(
      listEmprendimientos({
        page: 1,
        size: 20,
        tipoVendedor: 'AMBULANTE',
        lat: 4.711,
        lng: -74.0721,
        radioMetros: 2_000,
        cercania: true,
      }),
    ).resolves.toEqual(page);

    expect(jsonHeaders).toHaveBeenCalledWith({ accessToken: 'access' });
    expect(requestJson).toHaveBeenCalledWith(
      '/api/v1/emprendimientos?page=1&size=20&tipoVendedor=AMBULANTE&lat=4.711&lng=-74.0721&radioMetros=2000&orden=cercania',
      expect.objectContaining({ method: 'GET' }),
    );
  });
});
