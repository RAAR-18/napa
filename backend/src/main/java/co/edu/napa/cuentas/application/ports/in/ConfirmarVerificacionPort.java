package co.edu.napa.cuentas.application.ports.in;

import co.edu.napa.cuentas.domain.model.PropositoVerificacion;

/** RF-70: comprueba un código. Lanza OTP_INVALID, OTP_EXPIRED u OTP_ATTEMPTS_EXHAUSTED si no es válido. */
public interface ConfirmarVerificacionPort {
    void confirmar(String celular, PropositoVerificacion proposito, String codigo);
}