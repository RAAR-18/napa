package co.edu.napa.cuentas.application.ports.out;

import co.edu.napa.cuentas.domain.model.CanalVerificacion;

/**
 * Espera creciente entre envíos de código a un mismo celular por un mismo medio: 2 minutos
 * después del primero, 4 después del segundo, 8 después del tercero, y así hasta un tope.
 * Cada medio lleva su propia espera: si el SMS no llega se puede pedir enseguida por correo.
 */
public interface EsperaReenvioPort {
    /** Lanza RATE_LIMITED con retryAfter (segundos) si todavía no se puede enviar otro código. */
    void exigirDisponible(String celular, CanalVerificacion canal);

    /** Registra un envío y devuelve los segundos que hay que esperar para el siguiente. */
    long registrarEnvio(String celular, CanalVerificacion canal);
}
