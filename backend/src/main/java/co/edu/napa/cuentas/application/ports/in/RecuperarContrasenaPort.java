package co.edu.napa.cuentas.application.ports.in;

/** RF-09: recupera la contraseña con el código enviado al celular y revoca todas las sesiones. */
public interface RecuperarContrasenaPort {
    void recuperar(String celular, String codigo, String contrasenaNueva);
}