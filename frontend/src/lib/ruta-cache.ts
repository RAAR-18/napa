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

export async function guardarRuta(ruta: RutaDomicilio): Promise<void> {
  await guardarLectura(PREFIX + ruta.domicilioId, JSON.stringify(ruta));
}

export async function leerRuta(
  domicilioId: string,
): Promise<{ ruta: RutaDomicilio; updatedAt: string } | null> {
  const row = await leerLectura(PREFIX + domicilioId);
  if (!row) return null;
  try {
    const ruta: unknown = JSON.parse(row.contenido);
    return isRuta(ruta) ? { ruta, updatedAt: row.actualizado_at } : null;
  } catch {
    return null;
  }
}

/** Al cerrar sesión: las rutas guardadas tienen la dirección exacta de los clientes. */
export async function borrarTodasLasRutas(): Promise<void> {
  await borrarRutasGuardadas();
}

export async function borrarRuta(domicilioId: string): Promise<void> {
  await borrarLectura(PREFIX + domicilioId);
}
