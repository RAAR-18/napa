import { HelperText, TextButton } from './AuthUI';
import type { AccionPendiente } from '../database/outbox/types';
import type { PasoDomicilio } from '../lib/confirmacion-domicilio';

type PasoConfirmacion = PasoDomicilio | 'confirmar-pago' | 'confirmar-entrega-directa';

const mensajes: Record<PasoConfirmacion, { pendiente: string; confirmada: string }> = {
  'confirmar-recogida': {
    pendiente: 'Recogida pendiente de envío',
    confirmada: 'Recogida confirmada',
  },
  'confirmar-salida': {
    pendiente: 'Salida pendiente de envío',
    confirmada: 'Salida confirmada',
  },
  'confirmar-pago': {
    pendiente: 'Confirmación de pago pendiente de envío',
    confirmada: 'Pago confirmado',
  },
  'confirmar-entrega-directa': {
    pendiente: 'Entrega y cobro pendientes de envío',
    confirmada: 'Entrega y cobro confirmados',
  },
};

export function EstadoConfirmacionDomicilio({
  pendiente,
  confirmada,
  paso,
  cerrarRevision,
}: {
  pendiente?: AccionPendiente;
  confirmada?: { updatedAt: string } | null;
  paso: PasoConfirmacion;
  cerrarRevision?: () => void;
}) {
  const mensaje = mensajes[paso];
  const formato = (fecha: number | string) =>
    new Date(fecha).toLocaleString('es-CO', {
      dateStyle: 'medium',
      timeStyle: 'short',
    });

  if (pendiente) {
    const fallida = pendiente.status === 'FALLIDA';

    return (
      <>
        <HelperText>{fallida ? 'Requiere revisión' : mensaje.pendiente}</HelperText>
        <HelperText>Guardada en el dispositivo el {formato(pendiente.createdAt)}.</HelperText>
        {fallida ? (
          <>
            <HelperText>{pendiente.lastError ?? 'El servidor rechazó la confirmación.'}</HelperText>
            {cerrarRevision ? (
              <TextButton title="Cerrar revisión" onPress={cerrarRevision} />
            ) : null}
          </>
        ) : null}
      </>
    );
  }

  return confirmada ? (
    <HelperText>{mensaje.confirmada} por el servidor el {formato(confirmada.updatedAt)}.</HelperText>
  ) : null;
}