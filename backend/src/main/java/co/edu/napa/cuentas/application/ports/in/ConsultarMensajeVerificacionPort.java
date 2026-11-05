package co.edu.napa.cuentas.application.ports.in;

import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.EstadoMensaje;

import java.util.UUID;

/** RF-70: estado de un envío, para saber si se entregó o si conviene ofrecer SMS. */
public interface ConsultarMensajeVerificacionPort {
    MensajeVista consultar(UUID mensajeId);

    record MensajeVista(UUID mensajeId, CanalVerificacion canal, EstadoMensaje estado, boolean smsDisponible) {
    }
}