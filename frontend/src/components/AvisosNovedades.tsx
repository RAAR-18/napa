import { useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { getSessionGeneration, readSession } from '../lib/auth';
import { listarNotificaciones } from '../lib/notificaciones';
import { escucharNovedades } from '../lib/push';
import { Toast } from './Toast';

export const INTERVALO_AVISOS_MS = 15_000;
/** Avisos globales en primer plano: la bandeja conserva su estado de lectura. */
/** Se monta una vez en el layout: navegar no dispara consultas extra; el sondeo, volver a primer plano y el push sí. */
export function AvisosNovedades() {
  const [aviso, setAviso] = useState<{ id: string; mensaje: string; generacion: number } | null>(null);
  const conocidas = useRef<{ cuenta: string; generacion: number; ids: Set<string> } | null>(null);
  const consultando = useRef(false);
  useEffect(() => {
    let activo = true;
    async function consultar() {
      if (!activo || consultando.current || AppState.currentState !== 'active') return;
      consultando.current = true;
      const generacion = getSessionGeneration();
      try {
        const sesion = await readSession();
        if (!activo || generacion !== getSessionGeneration()) return;
        if (!sesion?.usuarioId) { conocidas.current = null; setAviso(null); return; }
        const cuenta = sesion.usuarioId;
        const respuesta = await listarNotificaciones({ page: 0, size: 50 });
        const actual = await readSession();
        if (!activo || generacion !== getSessionGeneration() || actual?.usuarioId !== cuenta) return;
        const anteriores = conocidas.current;
        const mismaCuenta = anteriores?.cuenta === cuenta && anteriores.generacion === generacion;
        const ids = new Set(respuesta.content.map((item) => item.id));
        if (mismaCuenta) {
          const nuevas = respuesta.content.filter((item) => !anteriores.ids.has(item.id) && !item.leida);
          if (nuevas.length) {
            const ultima = nuevas[0];
            setAviso({ id: ultima.id, generacion,
              mensaje: nuevas.length > 1 ? `${ultima.titulo}: ${ultima.cuerpo} (${nuevas.length} novedades)`
                : `${ultima.titulo}: ${ultima.cuerpo}` });
          }
          // Mantiene una ventana acotada; no se conserva contenido de las notificaciones.
          for (const id of anteriores.ids) { if (ids.size < 200) ids.add(id); }
        } else setAviso(null);
        conocidas.current = { cuenta, generacion, ids };
      } catch { /* Los avisos no bloquean la pantalla si la API no está disponible. */ }
      finally { consultando.current = false; }
    }
    void consultar();
    const timer = setInterval(() => { void consultar(); }, INTERVALO_AVISOS_MS);
    const listener = AppState.addEventListener('change', (estado) => { if (estado === 'active') void consultar(); });
    const dejarPush = escucharNovedades(() => { void consultar(); });
    return () => { activo = false; clearInterval(timer); listener.remove(); dejarPush(); };
  }, []);
  if (!aviso || aviso.generacion !== getSessionGeneration()) return null;
  return <Toast key={aviso.id} message={aviso.mensaje} tone="info" />;
}
