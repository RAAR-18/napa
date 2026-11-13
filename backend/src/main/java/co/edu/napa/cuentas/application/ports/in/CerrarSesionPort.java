package co.edu.napa.cuentas.application.ports.in;

import java.util.UUID;

/** RF-10: cierra la sesión actual. */
public interface CerrarSesionPort {
    void cerrar(UUID sessionId, long sessionVersion);
}