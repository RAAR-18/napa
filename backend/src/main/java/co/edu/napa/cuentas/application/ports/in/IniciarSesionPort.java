package co.edu.napa.cuentas.application.ports.in;

/** RF-10: inicio de sesión con celular o correo y contraseña. */
public interface IniciarSesionPort {
    AuthTokens iniciarSesion(String identificador, String password, String deviceId, String ipAddress);
}