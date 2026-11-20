import { SqliteOutboxStore } from './sqlite-store';
import type { AccionPendiente } from './types';

const mockDatabase = {
  execAsync: jest.fn().mockResolvedValue(undefined),
  runAsync: jest.fn().mockResolvedValue({ changes: 1, lastInsertRowId: 1 }),
  getAllAsync: jest.fn().mockResolvedValue([]),
};

jest.mock('expo-sqlite', () => ({
  openDatabaseAsync: jest.fn(() => Promise.resolve(mockDatabase)),
}));

const accion: AccionPendiente = {
  id: 'id-1',
  usuarioId: 'cuenta-A',
  method: 'POST',
  path: '/api/v1/reportes',
  body: '{"motivo":"OTRO"}',
  idempotencyKey: 'key-1',
  descripcion: 'Reporte: Otro',
  status: 'PENDIENTE',
  attempts: 0,
  lastError: null,
  nextAttemptAt: 10,
  createdAt: 10,
};

beforeEach(() => jest.clearAllMocks());

it('creates the outbox table once with a unique idempotency key and ignores repeated inserts', async () => {
  const store = new SqliteOutboxStore();
  await store.insertar(accion);
  await store.insertar(accion);

  expect(mockDatabase.execAsync.mock.calls.filter(([sql]) => sql.includes('CREATE TABLE'))).toHaveLength(1);
  expect(mockDatabase.execAsync.mock.calls[0][0]).toContain('UNIQUE (usuario_id, idempotency_key)');
  expect(mockDatabase.runAsync.mock.calls[0][0]).toContain('INSERT OR IGNORE INTO outbox');
  expect(mockDatabase.runAsync.mock.calls[0].slice(1)).toEqual([
    'id-1', 'cuenta-A', 'POST', '/api/v1/reportes', '{"motivo":"OTRO"}', 'key-1', 'Reporte: Otro', 'PENDIENTE', 0, null, 10, 10, null,
  ]);
});

it('reads rows oldest first and maps the columns', async () => {
  mockDatabase.getAllAsync.mockResolvedValue([
    {
      id: 'id-1',
      usuario_id: 'cuenta-A',
      method: 'POST',
      path: '/api/v1/reportes',
      body: null,
      idempotency_key: 'key-1',
      descripcion: 'Reporte: Otro',
      status: 'FALLIDA',
      attempts: 2,
      last_error: 'Rechazado',
      next_attempt_at: 50,
      created_at: 10,
    },
  ]);

  const filas = await new SqliteOutboxStore().listar();

  expect(mockDatabase.getAllAsync).toHaveBeenCalledWith('SELECT * FROM outbox_por_cuenta ORDER BY created_at, id');
  expect(filas).toEqual([{ ...accion, body: null, status: 'FALLIDA', attempts: 2, lastError: 'Rechazado', nextAttemptAt: 50 }]);
});
