import { jsonHeaders, requestJson } from './api';
import { createEmprendimiento, type Emprendimiento } from './emprendimientos';
import type { StoredSession } from './auth';

jest.mock('expo-crypto', () => ({ randomUUID: () => 'idempotency-id' }));
jest.mock('./api', () => ({
  jsonHeaders: jest.fn(() => ({ Authorization: 'Bearer access' })),
  requestJson: jest.fn(),
}));

describe('createEmprendimiento', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

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

    await expect(createEmprendimiento(session, input)).resolves.toEqual(created);

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
