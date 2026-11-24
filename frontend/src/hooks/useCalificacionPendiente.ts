import { useCallback, useState } from 'react';
import { useFocusEffect } from 'expo-router';

import { obtenerOpcionesCalificacion } from '../lib/calificaciones';

export function useCalificacionPendiente(pedidoId: string | undefined, habilitado: boolean) {
  const [resultado, setResultado] = useState<{ pedidoId: string; pendiente: boolean } | null>(null);
  useFocusEffect(useCallback(() => {
    let vigente = true;
    if (pedidoId && habilitado) {
      void obtenerOpcionesCalificacion({ pedidoId }).then((respuesta) => {
        if (vigente) setResultado({ pedidoId, pendiente: respuesta.opciones.some((opcion) => opcion.calificacionId === null) });
      }).catch(() => { if (vigente) setResultado(null); });
    }
    return () => { vigente = false; };
  }, [pedidoId, habilitado]));
  return Boolean(habilitado && resultado?.pedidoId === pedidoId && resultado?.pendiente);
}
