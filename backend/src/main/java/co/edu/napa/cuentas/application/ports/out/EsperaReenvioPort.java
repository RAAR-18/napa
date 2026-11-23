package co.edu.napa.cuentas.application.ports.out;

/**
 * Espera creciente entre envíos de código a un mismo celular, sin importar el canal: 2 minutos
 * después del primero, 4 después del segundo, 8 después del tercero, y así hasta un tope.
 */
public interface EsperaReenvioPort {
    /** Lanza RATE_LIMITED con retryAfter (segundos) si todavía no se puede enviar otro código. */
    void exigirDisponible(String celular);

    /** Registra un envío y devuelve los segundos que hay que esperar para el siguiente. */
    long registrarEnvio(String celular);
}
