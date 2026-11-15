import { useEffect, useState } from 'react';

import { obtenerResumenes, type ResumenCalificaciones } from '../lib/calificaciones';

/**
 * RF-02 en listados: promedio de los productos (o emprendimientos) visibles en una sola petición. Si falla, la
 * lista se muestra igual, sin estrellas.
 */
export function useResumenesCalificacion(
  tipo: 'PRODUCTO' | 'EMPRENDIMIENTO',
  ids: string[],
): Record<string, ResumenCalificaciones> {
  const [resumenes, setResumenes] = useState<Record<string, ResumenCalificaciones>>({});
  const clave = ids.join(',');

  useEffect(() => {
    let vigente = true;
    async function cargar() {
      if (!clave) return;
      try {
        const respuesta = await obtenerResumenes(tipo, clave.split(','));
        if (vigente) setResumenes(respuesta);
      } catch {
        // El catálogo sigue sirviendo sin las calificaciones.
      }
    }
    void cargar();
    return () => {
      vigente = false;
    };
  }, [tipo, clave]);

  return resumenes;
}
