package co.edu.napa.cuentas.application.ports.in;

import co.edu.napa.cuentas.domain.model.CanalVerificacion;

/** RF-09: pide el código de recuperación por SMS o por correo. Responde igual exista o no la cuenta. */
public interface SolicitarRecuperacionPort {
    default void solicitar(String celular, String ipAddress) {
        solicitar(celular, CanalVerificacion.WHATSAPP, ipAddress);
    }

    void solicitar(String celular, CanalVerificacion canal, String ipAddress);
}