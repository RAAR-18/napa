import * as storage from './auth-storage';
export const oyentesUbicacion = new Set<() => void>();
export function suscribirUbicacionAutomatica(oyente: () => void) {
  oyentesUbicacion.add(oyente);
  return () => { oyentesUbicacion.delete(oyente); };
}
export const UBICACION_KEY = 'napa.ubicacion-automatica';
const KEY = UBICACION_KEY;
export type Preferencia = { usuarioId: string; emprendimientoId: string; segundoPlano?: boolean; activacionId?: string; iniciadaAt?: number };
export async function leerUbicacionAutomatica(): Promise<Preferencia | null> {
  const raw = await storage.getItemAsync(KEY);
  if (!raw) return null;
  try {
    const dato = JSON.parse(raw) as Partial<Preferencia> | null;
    return dato && typeof dato.usuarioId === 'string' && typeof dato.emprendimientoId === 'string'
      ? dato as Preferencia : null;
  } catch { return null; }
}


export function mismaPreferencia(a: Preferencia | null, b: Preferencia): boolean {
  return a?.usuarioId === b.usuarioId && a.emprendimientoId === b.emprendimientoId && a.activacionId === b.activacionId;
}

/** Revocar el permiso de segundo plano no puede reactivarse desde una reconciliación posterior. */
export async function deshabilitarSegundoPlano(preferencia: Preferencia): Promise<void> {
  if (!mismaPreferencia(await leerUbicacionAutomatica(), preferencia)) return;
  await storage.setItemAsync(UBICACION_KEY, JSON.stringify({ ...preferencia, segundoPlano: false }));
  oyentesUbicacion.forEach((oyente) => oyente());
}

// Registro desacoplado: auth puede detener tareas sin importar módulos nativos ni componentes React.
const paradas = new Set<() => Promise<void>>();
export function registrarParadaUbicacion(parar: () => Promise<void>) {
  paradas.add(parar);
  return () => { paradas.delete(parar); };
}
export async function detenerTareasUbicacion(): Promise<void> {
  await Promise.all([...paradas].map((parar) => parar()));
}
