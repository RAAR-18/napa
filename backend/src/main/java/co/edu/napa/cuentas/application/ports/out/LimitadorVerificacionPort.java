package co.edu.napa.cuentas.application.ports.out;

import co.edu.napa.cuentas.domain.model.PropositoVerificacion;

/** Límites de envío y de intentos de código (RF-70 FR-003 y FR-004). */
public interface LimitadorVerificacionPort {
    int INTENTOS_MAXIMOS = 5;

    /** Cuenta un envío por celular y por IP; lanza RATE_LIMITED con retryAfter si se superó algún límite. */
    void registrarEnvio(String celular, String ipAddress);

    int intentosRestantes(String celular, PropositoVerificacion proposito);

    /** Suma un intento fallido y devuelve cuántos quedan. */
    int registrarIntentoFallido(String celular, PropositoVerificacion proposito);

    /** Pone el contador en cero (código aprobado o código nuevo enviado). */
    void reiniciarIntentos(String celular, PropositoVerificacion proposito);
}