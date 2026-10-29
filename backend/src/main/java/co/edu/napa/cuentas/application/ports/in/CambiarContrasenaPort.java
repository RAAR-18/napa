package co.edu.napa.cuentas.application.ports.in;

import java.util.UUID;

/** RF-09: cambia la contraseña con la actual, cierra las demás sesiones y rota los tokens de la sesión en curso. */
public interface CambiarContrasenaPort {
    AuthTokens cambiar(UUID usuarioId, UUID sesionActualId, String passwordActual, String passwordNueva);
}