import { useEffect, useRef } from 'react';
import { AppState, Platform } from 'react-native';
import { usePathname } from 'expo-router';

import { getSessionGeneration, readSession } from '../lib/auth';
import { registrarPush } from '../lib/push';

/**
 * RF-38: registra este celular para recibir push en cuanto hay una sesión, en cualquier pantalla.
 * Antes solo ocurría al abrir "Mi cuenta", y quien nunca entraba ahí no recibía avisos.
 *
 * - Se intenta una vez por sesión (al abrir la app o tras iniciar sesión, que cambia de pantalla).
 * - Al volver a primer plano se reintenta sin pedir permiso, por si la persona lo activó en los ajustes.
 */
export function useRegistroPush() {
  const pathname = usePathname();
  const intentada = useRef<number | null>(null);
  const enCurso = useRef(false);

  useEffect(() => {
    if (Platform.OS === 'web') return undefined;

    async function intentar(pedirPermiso: boolean) {
      const generacion = getSessionGeneration();
      if (enCurso.current || (pedirPermiso && intentada.current === generacion)) return;
      enCurso.current = true;
      try {
        const sesion = await readSession().catch(() => null);
        if (!sesion || generacion !== getSessionGeneration()) return;
        intentada.current = generacion;
        await registrarPush(pedirPermiso);
      } finally {
        enCurso.current = false;
      }
    }

    void intentar(true);
    const subscription = AppState.addEventListener('change', (estado) => {
      if (estado === 'active') void intentar(false);
    });
    return () => subscription.remove();
  }, [pathname]);
}
