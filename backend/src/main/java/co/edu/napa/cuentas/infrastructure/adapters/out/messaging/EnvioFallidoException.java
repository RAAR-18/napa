package co.edu.napa.cuentas.infrastructure.adapters.out.messaging;

/** El proveedor externo (SMSReactor o Brevo) no aceptó el envío del código. */
public class EnvioFallidoException extends RuntimeException {
    public EnvioFallidoException(String message) {
        super(message);
    }
}
