import { guardarLectura, leerLectura, borrarLectura, borrarRutasGuardadas } from './cache-lectura-storage';

import type { RutaDomicilio } from './domicilios';

/**
 * RF-22 (RNF28): la última ruta de cada domicilio activo, para seguir mostrándola sin señal. Usa el mismo almacenamiento de
 * lecturas guardadas que el listado de emprendimientos. Se borra cuando el domicilio termina.
 */
const PREFIX = 'ruta-domicilio:';

function isRuta(value: unknown): value is RutaDomicilio {
  if (typeof value !== 'object' || value === null) return false;
  const ruta = value as Partial<RutaDomicilio>;
  return (
    typeof ruta.domicilioId === 'string' &&
    typeof ruta.estado === 'string' &&
    typeof ruta.origen?.latitud === 'number' &&
    typeof ruta.destino?.latitud === 'number' &&
    Array.isArray(ruta.tramos)
  );
}

function claveCuenta(usuarioId: string, domicilioId: string): string {
  return PREFIX + JSON.stringify([usuarioId, domicilioId]);
}

export async function guardarRuta(ruta: RutaDomicilio, usuarioId: string): Promise<void> {
  if (!usuarioId) return;
  await guardarLectura(claveCuenta(usuarioId, ruta.domicilioId), JSON.stringify(ruta));
}

export async function leerRuta(
  domicilioId: string,
  usuarioId: string,
): Promise<{ ruta: RutaDomicilio; updatedAt: string } | null> {
  if (!usuarioId) return null;
  const row = await leerLectura(claveCuenta(usuarioId, domicilioId));
  if (!row) return null;
  try {
    const ruta: unknown = JSON.parse(row.contenido);
    return isRuta(ruta) && ruta.domicilioId === domicilioId ? { ruta, updatedAt: row.actualizado_at } : null;
  } catch {
    return null;
  }
}

/** Al cerrar sesión: las rutas guardadas tienen la dirección exacta de los clientes. */
export async function borrarTodasLasRutas(): Promise<void> {
  await borrarRutasGuardadas();
}

export async function borrarRuta(domicilioId: string, usuarioId: string): Promise<void> {
  if (!usuarioId) return;
  await borrarLectura(claveCuenta(usuarioId, domicilioId));
}
