import { HelperText, TextButton } from './AuthUI';
import type { AccionPendiente } from '../database/outbox/types';
import type { TransicionDomicilio } from '../lib/domicilios';
import type { PasoDomicilio } from '../lib/confirmacion-domicilio';

export function EstadoConfirmacionDomicilio({ pendiente, confirmada, paso, cerrarRevision }: {
  pendiente?: AccionPendiente;
  confirmada?: TransicionDomicilio | null;
  paso: PasoDomicilio | 'confirmar-entrega-directa';
  cerrarRevision?: () => void;
}) {
  const etiqueta = paso === 'confirmar-recogida' ? 'Recogida'
    : paso === 'confirmar-salida' ? 'Salida' : 'Entrega y cobro';
  const entregaDirecta = paso === 'confirmar-entrega-directa';
  const formato = (fecha: number | string) =>
    new Date(fecha).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
  if (pendiente) {
    return (
      <>
        <HelperText>{pendiente.status === 'FALLIDA' ? 'Requiere revisión'
          : `${etiqueta} ${entregaDirecta ? 'pendientes' : 'pendiente'} de envío`}</HelperText>
        <HelperText>Guardada en el dispositivo el {formato(pendiente.createdAt)}.</HelperText>
        {pendiente.status === 'FALLIDA' ? (
          <>
            <HelperText>{pendiente.lastError ?? 'El servidor rechazó la confirmación.'}</HelperText>
            {cerrarRevision ? <TextButton title="Cerrar revisión" onPress={cerrarRevision} /> : null}
          </>
        ) : null}
      </>
    );
  }
  return confirmada ? (
    <HelperText>{etiqueta} {entregaDirecta ? 'confirmados' : 'confirmada'} por el servidor el {formato(confirmada.updatedAt)}.</HelperText>
  ) : null;
}
