package co.edu.napa.cuentas.application.ports.in;

/** RF-70 / RF-07: confirma el código, activa la cuenta e inicia la primera sesión. */
public interface ConfirmarRegistroPort {
    AuthTokens confirmar(String celular, String codigo, String deviceId);
}