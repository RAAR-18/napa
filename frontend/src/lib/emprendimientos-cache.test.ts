import {
  readEmprendimientosPage,
  saveEmprendimientosPage,
} from './emprendimientos-cache';
import type { EmprendimientosFilter, EmprendimientosPage } from './emprendimientos';

const mockDatabase = {
  execAsync: jest.fn().mockResolvedValue(undefined),
  runAsync: jest.fn().mockResolvedValue({ changes: 1, lastInsertRowId: 1 }),
  getFirstAsync: jest.fn(),
};

jest.mock('expo-sqlite', () => ({
  openDatabaseAsync: jest.fn(() => Promise.resolve(mockDatabase)),
}));

const page: EmprendimientosPage = {
  content: [
    {
      id: 'business-id',
      nombre: 'Frutas de Ana',
      categoria: 'FRUTAS_VERDURAS',
      tipoVendedor: 'AMBULANTE',
      abierto: true,
      horaApertura: '08:00',
      horaCierre: '18:00',
      distanciaMetros: 245,
      ubicacion: {
        latitud: 4.71,
        longitud: -74.07,
        direccion: null,
      },
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
};

const filter: EmprendimientosFilter = {
  page: 0,
  size: 20,
  tipoVendedor: 'AMBULANTE',
  lat: 4.711,
  lng: -74.0721,
  radioMetros: 2_000,
  cercania: true,
};

describe('emprendimientos cache', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('stores the page without client location or derived distances', async () => {
    await saveEmprendimientosPage(filter, page);

    const [sql, key, serializedPage] = mockDatabase.runAsync.mock.calls[0];
    expect(sql).toContain('INSERT INTO cache_lectura');
    expect(key).not.toContain('4.711');
    expect(key).not.toContain('-74.0721');
    expect(JSON.parse(serializedPage).content[0].distanciaMetros).toBeNull();
  });

  it('rejects malformed cached pages instead of returning invalid UI data', async () => {
    mockDatabase.getFirstAsync.mockResolvedValue({
      contenido: JSON.stringify({ content: null }),
      actualizado_at: '2025-01-15T10:30:00.000Z',
    });

    await expect(readEmprendimientosPage(filter)).rejects.toThrow(
      'El listado guardado no es válido y no se puede mostrar.',
    );
  });
});
