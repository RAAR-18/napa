package co.edu.napa.cuentas.application.ports.in;

/** RF-09: pide el código de recuperación. Responde igual exista o no la cuenta. */
public interface SolicitarRecuperacionPort {
    void solicitar(String celular, String ipAddress);
}