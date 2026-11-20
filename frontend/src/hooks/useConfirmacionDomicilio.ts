import { useEffect, useRef, useState } from 'react';

import { outbox } from '../database/outbox';
import { guardarConsultaPedidos, leerConsultaPedidos } from '../lib/pedidos-cliente-cache';
import { rutaConfirmacionDomicilio, type PasoDomicilio } from '../lib/confirmacion-domicilio';
import type { TransicionDomicilio } from '../lib/domicilios';
import { useAccionesPendientes } from './useOutbox';

export function useConfirmacionDomicilio(
  cuenta: string | null, id: string | undefined, paso: PasoDomicilio, actualizar: () => void,
) {
  const acciones = useAccionesPendientes();
  const ruta = id ? rutaConfirmacionDomicilio(id, paso) : null;
  const pendiente = acciones.find((a) => cuenta && a.usuarioId === cuenta && a.method === 'POST' && a.path === ruta);
  const marca = pendiente ? `${pendiente.id}:${pendiente.status}` : null;
  const anterior = useRef<{ cuenta: string | null; ruta: string | null; marca: string | null } | null>(null);
  const refrescar = useRef(actualizar);
  const [confirmada, setConfirmada] = useState<{ cuenta: string; ruta: string; data: TransicionDomicilio } | null>(null);
  useEffect(() => { refrescar.current = actualizar; }, [actualizar]);

  useEffect(() => {
    const previo = anterior.current;
    anterior.current = { cuenta, ruta, marca };
    if (previo && previo.cuenta === cuenta && previo.ruta === ruta && previo.marca && previo.marca !== marca) {
      refrescar.current();
    }
    if (!cuenta || !ruta) return;
    let active = true;
    void leerConsultaPedidos<TransicionDomicilio>(cuenta, 'confirmacion-domicilio', ruta)
      .then((resultado) => {
        if (active) setConfirmada(resultado ? { cuenta, ruta, data: resultado.data } : null);
      }).catch(() => undefined);
    return () => { active = false; };
  }, [cuenta, ruta, marca]);

  async function encolar(clave: string, intentoPrevio: boolean) {
    if (!cuenta || !ruta || cuenta !== await outbox.propietario()) {
      throw new Error('Vuelve a abrir el domicilio con la cuenta que lo consultó.');
    }
    await outbox.encolar({
      usuarioId: cuenta, method: 'POST', path: ruta, idempotencyKey: clave, intentoPrevio,
      descripcion: paso === 'confirmar-recogida' ? 'Recogida de domicilio' : 'Salida de domicilio',
    });
  }

  async function registrar(resultado: TransicionDomicilio) {
    if (cuenta && ruta) {
      await guardarConsultaPedidos(cuenta, 'confirmacion-domicilio', ruta, resultado, () => true);
    }
  }

  async function yaEncolada() {
    if (!cuenta || !ruta) return false;
    if (cuenta !== await outbox.propietario()) {
      throw new Error('Vuelve a abrir el domicilio con la cuenta que lo consultó.');
    }
    return (await outbox.listar()).some((accion) => accion.method === 'POST' && accion.path === ruta);
  }

  return {
    pendiente,
    registrar,
    yaEncolada,
    confirmada: confirmada?.cuenta === cuenta && confirmada?.ruta === ruta ? confirmada.data : null,
    encolar,
  };
}
