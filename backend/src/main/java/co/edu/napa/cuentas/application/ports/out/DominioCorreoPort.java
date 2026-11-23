package co.edu.napa.cuentas.application.ports.out;

/** Comprueba que el dominio de un correo exista y pueda recibir mensajes (registros MX o A). */
public interface DominioCorreoPort {
    /** false solo si el dominio seguro no recibe correo; ante una falla de DNS responde true. */
    boolean recibeCorreo(String dominio);
}
