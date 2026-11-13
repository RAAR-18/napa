package co.edu.napa.cuentas.application.ports.in;

/** RF-09: confirma el código, fija la contraseña nueva, cierra todas las sesiones e inicia una nueva. */
public interface ConfirmarRecuperacionPort {
    AuthTokens confirmar(String celular, String codigo, String passwordNueva, String deviceId);
}