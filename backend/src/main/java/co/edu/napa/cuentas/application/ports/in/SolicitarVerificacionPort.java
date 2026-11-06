package co.edu.napa.cuentas.application.ports.in;

import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;

import java.util.UUID;

/** RF-70: envía (o reenvía) un código de verificación. Responde igual exista o no la cuenta (FR-006). */
public interface SolicitarVerificacionPort {
    EnvioSolicitado solicitar(
            String celular,
            PropositoVerificacion proposito,
            CanalVerificacion canal,
            String ipAddress
    );

    record EnvioSolicitado(UUID mensajeId, CanalVerificacion canal, long reenvioDisponibleEn) {
    }
}