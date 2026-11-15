import { fireEvent, render, waitFor } from '@testing-library/react-native';
import * as Crypto from 'expo-crypto';

import CalificarPedidoScreen from '../calificar-pedido';
import ReportarScreen from '../reportar';
import UsuarioAdminScreen from '../usuario-admin';
import { outbox } from '../../database/outbox';
import { ApiError, NetworkError } from '../../lib/api';
import { calificar, obtenerOpcionesCalificacion } from '../../lib/calificaciones';
import { crearReporte } from '../../lib/reportes';
import { eliminarUsuario, obtenerUsuario } from '../../lib/usuarios';

let mockParams: Record<string, string> = {};

jest.mock('expo-router', () => ({
  router: { back: jest.fn(), push: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: () => mockParams,
}));
jest.mock('expo-crypto', () => ({ randomUUID: jest.fn() }));
jest.mock('../../lib/auth', () => ({ clearSession: jest.fn(), getValidSession: jest.fn() }));
jest.mock('../../lib/calificaciones', () => ({
  ...jest.requireActual('../../lib/calificaciones'),
  calificar: jest.fn(),
  obtenerOpcionesCalificacion: jest.fn(),
}));
jest.mock('../../lib/reportes', () => ({
  ...jest.requireActual('../../lib/reportes'),
  crearReporte: jest.fn(),
}));
jest.mock('../../database/outbox', () => ({
  outbox: { encolar: jest.fn().mockResolvedValue(undefined) },
}));
jest.mock('../../lib/usuarios', () => ({
  ...jest.requireActual('../../lib/usuarios'),
  eliminarUsuario: jest.fn(),
  obtenerUsuario: jest.fn(),
}));

beforeEach(() => {
  jest.clearAllMocks();
  let counter = 0;
  jest.mocked(Crypto.randomUUID).mockImplementation(() => `key-${++counter}` as ReturnType<typeof Crypto.randomUUID>);
});

describe('calificar un pedido (RF-01)', () => {
  beforeEach(() => {
    mockParams = { pedidoId: 'p-1' };
    jest.mocked(obtenerOpcionesCalificacion).mockResolvedValue({
      pedidoId: 'p-1',
      estadoPedido: 'ENTREGADO',
      opciones: [
        { tipoEntidad: 'EMPRENDIMIENTO', entidadId: 'e-1', nombre: 'Frutas Ana', rol: null, calificacionId: null },
        { tipoEntidad: 'USUARIO', entidadId: 'u-9', nombre: 'Carlos', rol: 'DOMICILIARIO', calificacionId: 'c-0' },
      ],
    });
  });

  it('rates a pending target with stars and keeps the key when retrying', async () => {
    jest
      .mocked(calificar)
      .mockRejectedValueOnce(new Error('Sin conexión'))
      .mockResolvedValueOnce({
        id: 'c-1',
        pedidoId: 'p-1',
        tipoEntidad: 'EMPRENDIMIENTO',
        entidadId: 'e-1',
        puntaje: 4,
        comentario: null,
        estado: 'ACTIVA',
        createdAt: '2026-11-14T10:00:00Z',
      });
    const view = await render(<CalificarPedidoScreen />);

    await waitFor(() => expect(view.getByText('Frutas Ana')).toBeTruthy());
    expect(view.getByText('Domiciliario: Carlos ✓')).toBeTruthy();
    await fireEvent.press(view.getByRole('radio', { name: '4 estrellas' }));
    await fireEvent.press(view.getByRole('button', { name: 'Enviar calificación' }));
    await waitFor(() => expect(view.getByText('Sin conexión')).toBeTruthy());
    await fireEvent.press(view.getByRole('button', { name: 'Enviar calificación' }));

    await waitFor(() => expect(view.getByText('¡Gracias! Ya calificaste todo en este pedido.')).toBeTruthy());
    expect(calificar).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({ tipoEntidad: 'EMPRENDIMIENTO', entidadId: 'e-1', puntaje: 4 }),
      'key-1',
    );
    // El primer intento falló antes de llegar al servidor: el reintento usa una clave nueva.
    expect(jest.mocked(calificar).mock.calls[1][1]).toBe('key-2');
  });

  it('waits for the order to be delivered', async () => {
    jest.mocked(obtenerOpcionesCalificacion).mockResolvedValue({ pedidoId: 'p-1', estadoPedido: 'EN_CAMINO', opciones: [] });
    const view = await render(<CalificarPedidoScreen />);

    await waitFor(() => expect(view.getByText('Podrás calificar cuando el pedido esté entregado.')).toBeTruthy());
  });
});

describe('reportar un problema (RF-60)', () => {
  it('requires reason and description and sends the related delivery', async () => {
    mockParams = { domicilioId: 'd-1' };
    jest.mocked(crearReporte).mockResolvedValue({
      id: 'r-1',
      estado: 'PENDIENTE',
      motivo: 'DOMICILIO',
      descripcion: 'El cliente no contestó.',
      createdAt: '2026-11-14T10:00:00Z',
    });
    const view = await render(<ReportarScreen />);

    expect(view.getByRole('button', { name: 'Enviar reporte' }).props.accessibilityState.disabled).toBe(true);
    await fireEvent.press(view.getByRole('radio', { name: 'Problema con un domicilio' }));
    await fireEvent.changeText(view.getByLabelText('Descripción'), 'El cliente no contestó.');
    await fireEvent.press(view.getByRole('button', { name: 'Enviar reporte' }));

    await waitFor(() => expect(view.getByText('Recibimos tu reporte')).toBeTruthy());
    expect(crearReporte).toHaveBeenCalledWith(
      expect.objectContaining({ motivo: 'DOMICILIO', descripcion: 'El cliente no contestó.', domicilioId: 'd-1' }),
      'key-1',
    );
  });

  it('explains a duplicated open report', async () => {
    mockParams = { pedidoId: 'p-1' };
    jest.mocked(crearReporte).mockRejectedValue(new ApiError('Ya tienes un reporte abierto sobre esto.', 409, 'CONFLICT'));
    const view = await render(<ReportarScreen />);

    await fireEvent.press(view.getByRole('radio', { name: 'Problema con un pedido' }));
    await fireEvent.changeText(view.getByLabelText('Descripción'), 'Me llegó incompleto.');
    await fireEvent.press(view.getByRole('button', { name: 'Enviar reporte' }));

    await waitFor(() =>
      expect(view.getByText('Ya tienes un reporte abierto sobre esto. Te avisaremos cuando se resuelva.')).toBeTruthy(),
    );
  });
});

describe('sin conexión (F041)', () => {
  it('queues the report with the same idempotency key when the request does not reach the server', async () => {
    mockParams = { pedidoId: 'p-1' };
    jest.mocked(crearReporte).mockRejectedValue(new NetworkError('No se pudo conectar con la API'));
    const view = await render(<ReportarScreen />);

    await fireEvent.press(view.getByRole('radio', { name: 'Problema con un pedido' }));
    await fireEvent.changeText(view.getByLabelText('Descripción'), '  Me llegó incompleto.  ');
    await fireEvent.press(view.getByRole('button', { name: 'Enviar reporte' }));

    await waitFor(() => expect(view.getByText('Guardamos tu reporte')).toBeTruthy());
    expect(outbox.encolar).toHaveBeenCalledWith({
      method: 'POST',
      path: '/api/v1/reportes',
      body: { motivo: 'PEDIDO', descripcion: 'Me llegó incompleto.', pedidoId: 'p-1', domicilioId: undefined, usuarioId: undefined },
      idempotencyKey: 'key-1',
      descripcion: 'Reporte: Problema con un pedido',
    });
  });
});

describe('eliminar un usuario como administrador (RF-68)', () => {
  it('shows the pending activity and only then offers forcing the deletion', async () => {
    mockParams = { usuarioId: 'u-1' };
    jest.mocked(obtenerUsuario).mockResolvedValue({
      id: 'u-1',
      nombre: 'Vendedor Uno',
      celular: '+573001112233',
      correo: null,
      fotoUrl: null,
      rol: 'VENDEDOR_AMBULANTE',
      estado: 'ACTIVA',
      celularVerificado: true,
      createdAt: '2026-09-01T15:00:00Z',
      ubicacion: null,
    });
    jest
      .mocked(eliminarUsuario)
      .mockRejectedValueOnce(
        new ApiError('Actividad en curso', 409, 'HAS_PENDING_OPERATIONS', { pedidosPendientes: 1, emprendimientoActivo: true }),
      )
      .mockResolvedValueOnce(undefined);
    const view = await render(<UsuarioAdminScreen />);

    await waitFor(() => expect(view.getByText('Vendedor Uno')).toBeTruthy());
    await fireEvent.press(view.getByRole('button', { name: 'Eliminar esta cuenta' }));
    await fireEvent.press(view.getByRole('button', { name: 'Sí, eliminar' }));
    await waitFor(() => expect(view.getByText('• 1 pedido pendiente')).toBeTruthy());
    expect(view.getByText('• un emprendimiento activo')).toBeTruthy();
    await fireEvent.press(view.getByRole('button', { name: 'Eliminar de todas formas' }));

    await waitFor(() => expect(eliminarUsuario).toHaveBeenCalledTimes(2));
    expect(jest.mocked(eliminarUsuario).mock.calls[0][1]).toBe(false);
    expect(jest.mocked(eliminarUsuario).mock.calls[1][1]).toBe(true);
  });
});
