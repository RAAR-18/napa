import { HelperText, TextButton } from './AuthUI';
import type { AccionPendiente } from '../database/outbox/types';
import type { PasoDomicilio } from '../lib/confirmacion-domicilio';

export function EstadoConfirmacionDomicilio({ pendiente, confirmada, paso, cerrarRevision }: {
  pendiente?: AccionPendiente;
  confirmada?: { updatedAt: string } | null;
  paso: PasoDomicilio | 'confirmar-pago';
  cerrarRevision?: () => void;
}) {
  const pendienteTexto = paso === 'confirmar-pago' ? 'Confirmación de pago pendiente de envío'
    : paso === 'confirmar-recogida' ? 'Recogida pendiente de envío' : 'Salida pendiente de envío';
  const confirmadaTexto = paso === 'confirmar-pago' ? 'Pago confirmado'
    : paso === 'confirmar-recogida' ? 'Recogida confirmada' : 'Salida confirmada';
  const formato = (fecha: number | string) =>
    new Date(fecha).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
  if (pendiente) {
    return (
      <>
        <HelperText>{pendiente.status === 'FALLIDA' ? 'Requiere revisión'
          : pendienteTexto}</HelperText>
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
    <HelperText>{confirmadaTexto} por el servidor el {formato(confirmada.updatedAt)}.</HelperText>
  ) : null;
}
