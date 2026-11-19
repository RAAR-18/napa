import * as SQLite from 'expo-sqlite';

import { borrarPerfilGuardado, guardarPerfil, leerPerfilGuardado } from './perfil-cache';
import type { PerfilUsuario } from './usuarios';

jest.mock('expo-sqlite', () => ({ openDatabaseAsync: jest.fn() }));

const runAsync = jest.fn();
const getFirstAsync = jest.fn();
const perfil: PerfilUsuario = {
  id: 'cuenta-1', nombre: 'Ana', rol: 'CLIENTE', celular: '3001234567',
  correo: 'ana@example.com', fotoUrl: '/foto', estado: 'ACTIVA',
  celularVerificado: true, createdAt: '2026-11-18T12:00:00Z',
  ubicacion: { direccion: 'Calle 10', latitud: 11.2, longitud: -74.2, referencia: 'Puerta azul' },
};
const contenido = {
  nombre: perfil.nombre, rol: perfil.rol, celular: perfil.celular,
  correo: perfil.correo, direccion: 'Calle 10',
};

beforeAll(() => {
  jest.mocked(SQLite.openDatabaseAsync).mockResolvedValue({
    execAsync: jest.fn(),
    runAsync,
    getFirstAsync,
  } as unknown as SQLite.SQLiteDatabase);
});

beforeEach(() => jest.clearAllMocks());
afterEach(() => jest.restoreAllMocks());

it('guarda solo los campos aprobados bajo la cuenta del perfil', async () => {
  jest.spyOn(Date, 'now').mockReturnValue(1000);
  await guardarPerfil(perfil, () => true);
  expect(runAsync).toHaveBeenCalledWith(
    expect.stringContaining('INSERT INTO cache_perfil'),
    'cuenta-1', JSON.stringify(contenido), 1000,
  );
});

it('busca la copia por cuenta con un límite de siete días y conserva la fecha', async () => {
  const ahora = Date.parse('2026-11-18T12:00:00Z');
  jest.spyOn(Date, 'now').mockReturnValue(ahora);
  getFirstAsync.mockResolvedValueOnce({ contenido: JSON.stringify(contenido), actualizado_en: ahora - 1000 });

  await expect(leerPerfilGuardado('cuenta-1')).resolves.toEqual({ perfil: contenido, fecha: ahora - 1000 });
  expect(getFirstAsync).toHaveBeenCalledWith(
    expect.stringContaining('actualizado_en > ?'), 'cuenta-1', ahora - 7 * 24 * 60 * 60 * 1000,
  );
});

it('omite escrituras invalidadas y limpia solo la cuenta indicada', async () => {
  await guardarPerfil(perfil, () => false);
  expect(runAsync).not.toHaveBeenCalled();

  await borrarPerfilGuardado('cuenta-1');
  expect(runAsync).toHaveBeenCalledWith('DELETE FROM cache_perfil WHERE cuenta_id = ?', 'cuenta-1');
});
