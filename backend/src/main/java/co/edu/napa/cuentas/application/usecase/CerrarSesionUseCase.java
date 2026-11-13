package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.CerrarSesionPort;
import co.edu.napa.cuentas.application.ports.out.SesionRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.SessionVersionPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Component
public class CerrarSesionUseCase implements CerrarSesionPort {
    private final SesionRepositoryPort sesiones;
    private final SessionVersionPort sessionVersions;
    private final Clock clock;

    public CerrarSesionUseCase(
            SesionRepositoryPort sesiones,
            SessionVersionPort sessionVersions,
            Clock clock
    ) {
        this.sesiones = Objects.requireNonNull(sesiones, "sesiones");
        this.sessionVersions = Objects.requireNonNull(sessionVersions, "sessionVersions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public void cerrar(UUID sessionId, long sessionVersion) {
        Instant now = Instant.now(clock);
        sesiones.revoke(sessionId, now).ifPresent(revocada ->
                sessionVersions.revoke(
                        sessionId,
                        Math.max(sessionVersion + 1, revocada.sessionVersion()),
                        Duration.between(now, revocada.expiresAt())
                )
        );
    }
}