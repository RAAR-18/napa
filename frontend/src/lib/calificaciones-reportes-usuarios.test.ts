import { jsonHeaders, requestJson, requestVoid } from './api';
import { withValidSession, type StoredSession } from './auth';
import {
  calificar,
  listarCalificaciones,
  obtenerOpcionesCalificacion,
  obtenerResumenes,
  textoPromedio,
} from './calificaciones';
import { crearReporte, listarReportes } from './reportes';
import { describirPendientes, eliminarUsuario, urlFoto } from './usuarios';

jest.mock('expo-router', () => ({ router: { replace: jest.fn() } }));
jest.mock('./api', () => ({
  ...jest.requireActual('./api'),
  apiBaseUrl: 'http://api.test',
  requestJson: jest.fn(),
  requestVoid: jest.fn(),
}));
jest.mock('./auth', () => ({ withValidSession: jest.fn() }));

const session: StoredSession = {
  accessToken: 'access',
  refreshToken: 'refresh',
  rol: 'CLIENTE',
  tokenType: 'Bearer',
  expiresIn: 900,
  expiresAt: Date.now() + 900000,
};

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(withValidSession).mockImplementation((operation) => operation(session));
  jest.mocked(requestJson).mockResolvedValue({});
  jest.mocked(requestVoid).mockResolvedValue(undefined);
});

describe('calificaciones', () => {
  it('asks rating options by order or, for the courier, by delivery', async () => {
    await obtenerOpcionesCalificacion({ pedidoId: 'p-1' });
    await obtenerOpcionesCalificacion({ domicilioId: 'd-1' });

    expect(requestJson).toHaveBeenNthCalledWith(1, '/api/v1/calificaciones/opciones?pedidoId=p-1', {
      method: 'GET',
      headers: jsonHeaders({ accessToken: 'access' }),
    });
    expect(jest.mocked(requestJson).mock.calls[1][0]).toBe('/api/v1/calificaciones/opciones?domicilioId=d-1');
  });

  it('sends the idempotency key and omits a blank comment', async () => {
    await calificar(
      { pedidoId: 'p-1', tipoEntidad: 'PRODUCTO', entidadId: 'prod-1', puntaje: 4, comentario: '   ' },
      'key-1',
    );

    const [path, init] = jest.mocked(requestJson).mock.calls[0];
    expect(path).toBe('/api/v1/calificaciones');
    expect(init.headers).toEqual(jsonHeaders({ accessToken: 'access', idempotencyKey: 'key-1' }));
    expect(JSON.parse(String(init.body))).toEqual({
      pedidoId: 'p-1',
      tipoEntidad: 'PRODUCTO',
      entidadId: 'prod-1',
      puntaje: 4,
    });
  });

  it('builds the listing query with only the given filters', async () => {
    await listarCalificaciones({ tipoEntidad: 'EMPRENDIMIENTO', entidadId: 'e-1', page: 0, size: 20 });

    expect(jest.mocked(requestJson).mock.calls[0][0]).toBe(
      '/api/v1/calificaciones?tipoEntidad=EMPRENDIMIENTO&entidadId=e-1&page=0&size=20',
    );
  });

  it('asks summaries for unique ids in one request and indexes them by entity', async () => {
    jest.mocked(requestJson).mockResolvedValue({
      resumenes: [
        { entidadId: 'p-1', promedio: 4.3, total: 4 },
        { entidadId: 'p-2', promedio: null, total: 0 },
      ],
    });

    const resumenes = await obtenerResumenes('PRODUCTO', ['p-1', 'p-2', 'p-1']);

    expect(requestJson).toHaveBeenCalledTimes(1);
    expect(jest.mocked(requestJson).mock.calls[0][0]).toBe(
      '/api/v1/calificaciones/resumenes?tipoEntidad=PRODUCTO&ids=p-1%2Cp-2',
    );
    expect(resumenes).toEqual({ 'p-1': { promedio: 4.3, total: 4 }, 'p-2': { promedio: null, total: 0 } });
    expect(await obtenerResumenes('PRODUCTO', [])).toEqual({});
    expect(requestJson).toHaveBeenCalledTimes(1);
  });

  it('describes the average or the lack of ratings', () => {
    expect(textoPromedio({ promedio: null, total: 0 })).toBe('Aún sin calificaciones');
    expect(textoPromedio({ promedio: 4.5, total: 2 })).toMatch(/4,5 de 5 · 2 calificaciones/);
  });
});

describe('reportes', () => {
  it('creates a report with a trimmed description and the related entity', async () => {
    await crearReporte({ motivo: 'DOMICILIO', descripcion: '  No contestó en la dirección  ', domicilioId: 'd-1' }, 'k');

    const [path, init] = jest.mocked(requestJson).mock.calls[0];
    expect(path).toBe('/api/v1/reportes');
    expect(JSON.parse(String(init.body))).toEqual({
      motivo: 'DOMICILIO',
      descripcion: 'No contestó en la dirección',
      domicilioId: 'd-1',
    });
  });

  it('lists reports newest first when asked', async () => {
    await listarReportes({ size: 50, sort: 'createdAt,desc' });

    expect(jest.mocked(requestJson).mock.calls[0][0]).toBe('/api/v1/reportes?size=50&sort=createdAt%2Cdesc');
  });
});

describe('usuarios', () => {
  it('turns pending-operation counts into readable warnings', () => {
    expect(
      describirPendientes({ pedidosPendientes: 2, domiciliosActivos: 1, emprendimientoActivo: true }),
    ).toEqual(['2 pedidos pendientes', '1 domicilio activo', 'un emprendimiento activo']);
  });

  it('resolves photo paths served by the API', () => {
    expect(urlFoto('/media/perfiles/a.png')).toBe('http://api.test/media/perfiles/a.png');
    expect(urlFoto('https://cdn.test/a.png')).toBe('https://cdn.test/a.png');
    expect(urlFoto(null)).toBeNull();
  });

  it('deletes a user with explicit confirmation and the force flag', async () => {
    await eliminarUsuario('u-1', true, 'key');

    expect(requestVoid).toHaveBeenCalledWith('/api/v1/usuarios/u-1/eliminar', {
      method: 'POST',
      headers: jsonHeaders({ accessToken: 'access', idempotencyKey: 'key' }),
      body: JSON.stringify({ confirmar: true, forzar: true }),
    });
  });
});
