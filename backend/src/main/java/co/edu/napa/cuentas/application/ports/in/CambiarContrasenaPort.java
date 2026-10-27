package co.edu.napa.cuentas.application.ports.in;

import java.util.UUID;

/** RF-09: cambia la contraseña con la actual y revoca las demás sesiones. */
public interface CambiarContrasenaPort {
    void cambiar(UUID usuarioId, UUID sesionActualId, String contrasenaActual, String contrasenaNueva);
}