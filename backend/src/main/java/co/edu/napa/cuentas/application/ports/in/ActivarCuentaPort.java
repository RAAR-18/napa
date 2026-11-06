package co.edu.napa.cuentas.application.ports.in;

/** RF-07: activa una cuenta pendiente ya verificada e inicia su primera sesión. */
public interface ActivarCuentaPort {
    AuthTokens activar(String celular, String deviceId);
}