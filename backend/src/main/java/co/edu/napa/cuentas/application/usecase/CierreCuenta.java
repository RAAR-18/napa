package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.out.SesionRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.SessionVersionPort;
import co.edu.napa.cuentas.domain.model.Sesion;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Cierra todas las sesiones de un usuario y sube su {@code sv} para que los tokens emitidos dejen de servir. */
@Component
public class CierreCuenta {
    private final SesionRepositoryPort sesiones;
    private final SessionVersionPort sessionVersions;

    public CierreCuenta(SesionRepositoryPort sesiones, SessionVersionPort sessionVersions) {
        this.sesiones = Objects.requireNonNull(sesiones, "sesiones");
        this.sessionVersions = Objects.requireNonNull(sessionVersions, "sessionVersions");
    }

    public void cerrarSesiones(UUID usuarioId, Instant ahora) {
        for (Sesion sesion : sesiones.revokeAllSessions(usuarioId, ahora)) {
            Duration restante = Duration.between(ahora, sesion.expiresAt());
            sessionVersions.revoke(sesion.id(), sesion.sessionVersion(),
                    restante.isNegative() || restante.isZero() ? Duration.ofSeconds(1) : restante);
        }
    }
}
