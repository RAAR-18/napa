import { useEffect, useState } from 'react';
import * as Location from 'expo-location';

import { ApiError } from '../lib/api';
import { INTERVALO_UBICACION_MS, enviarUbicacionDomiciliario } from '../lib/domicilios';

export type EstadoCompartir = 'inactivo' | 'compartiendo' | 'sin-permiso' | 'error';

/**
 * RF-26: mientras `activo` es verdadero envía la posición cada 15 s. Las lecturas que fallan se descartan (no se
 * reenvían en lote); la siguiente vuelve a intentarlo.
 */
export function useCompartirUbicacion(domicilioId: string | undefined, activo: boolean): EstadoCompartir {
  const [estado, setEstado] = useState<EstadoCompartir>('inactivo');

  useEffect(() => {
    if (!domicilioId || !activo) return undefined;
    let vigente = true;
    let timer: ReturnType<typeof setInterval> | null = null;

    async function enviar() {
      try {
        const posicion = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
        if (!vigente || !domicilioId) return;
        await enviarUbicacionDomiciliario(domicilioId, posicion.coords.latitude, posicion.coords.longitude);
        if (vigente) setEstado('compartiendo');
      } catch (cause) {
        // 429: el envío anterior fue hace menos de 5 s; no es un error para el domiciliario.
        if (cause instanceof ApiError && cause.code === 'RATE_LIMITED') return;
        // 409: el domicilio ya no está en camino; dejamos de enviar.
        if (cause instanceof ApiError && cause.status === 409 && timer) {
          clearInterval(timer);
          timer = null;
          if (vigente) setEstado('inactivo');
          return;
        }
        if (vigente) setEstado('error');
      }
    }

    void (async () => {
      const permiso = await Location.requestForegroundPermissionsAsync().catch(() => null);
      if (!vigente) return;
      if (permiso?.status !== 'granted') {
        setEstado('sin-permiso');
        return;
      }
      await enviar();
      if (vigente) timer = setInterval(() => void enviar(), INTERVALO_UBICACION_MS);
    })();

    return () => {
      vigente = false;
      if (timer) clearInterval(timer);
    };
  }, [domicilioId, activo]);

  return domicilioId && activo ? estado : 'inactivo';
}
