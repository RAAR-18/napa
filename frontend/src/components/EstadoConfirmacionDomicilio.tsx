import { HelperText, TextButton } from './AuthUI';
import type { AccionPendiente } from '../database/outbox/types';
import type { TransicionDomicilio } from '../lib/domicilios';
import type { PasoDomicilio } from '../lib/confirmacion-domicilio';

export function EstadoConfirmacionDomicilio({ pendiente, confirmada, paso, cerrarRevision }: {
  pendiente?: AccionPendiente;
  confirmada?: TransicionDomicilio | null;
  paso: PasoDomicilio;
  cerrarRevision?: () => void;
}) {
  const recogida = paso === 'confirmar-recogida';
  const formato = (fecha: number | string) =>
    new Date(fecha).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
  if (pendiente) {
    return (
      <>
        <HelperText>{pendiente.status === 'FALLIDA' ? 'Requiere revisión'
          : recogida ? 'Recogida pendiente de envío' : 'Salida pendiente de envío'}</HelperText>
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
    <HelperText>{recogida ? 'Recogida' : 'Salida'} confirmada por el servidor el {formato(confirmada.updatedAt)}.</HelperText>
  ) : null;
}
