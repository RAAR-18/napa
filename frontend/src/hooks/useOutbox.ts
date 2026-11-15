import { useEffect, useState } from 'react';
import { AppState } from 'react-native';

import { outbox, type AccionPendiente } from '../database/outbox';

export const INTERVALO_OUTBOX_MS = 60_000;

/** Acciones en la cola, actualizadas cada vez que cambia. */
export function useAccionesPendientes(): AccionPendiente[] {
  const [acciones, setAcciones] = useState<AccionPendiente[]>([]);

  useEffect(() => {
    let vigente = true;
    async function leer() {
      try {
        const lista = await outbox.listar();
        if (vigente) setAcciones(lista);
      } catch {
        // Sin base local no hay nada que mostrar.
      }
    }
    void leer();
    const desuscribir = outbox.suscribir(() => void leer());
    return () => {
      vigente = false;
      desuscribir();
    };
  }, []);

  return acciones;
}

/** F041: intenta enviar la cola al abrir la app, al volver a primer plano y cada minuto. */
export function useProcesarOutbox() {
  useEffect(() => {
    const procesar = () => {
      outbox.procesar().catch(() => undefined);
    };
    procesar();
    const timer = setInterval(procesar, INTERVALO_OUTBOX_MS);
    const subscription = AppState.addEventListener('change', (estado) => {
      if (estado === 'active') procesar();
    });
    return () => {
      clearInterval(timer);
      subscription.remove();
    };
  }, []);
}
