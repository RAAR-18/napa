package co.edu.napa.cuentas.application.ports.out;

import co.edu.napa.cuentas.domain.model.Sesion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SesionRepositoryPort {
    Sesion save(Sesion sesion);

    Optional<Sesion> findByRefreshTokenHash(String tokenHash);

    boolean rotateRefreshToken(
            UUID sessionId,
            String currentTokenHash,
            String nextTokenHash,
            Instant now
    );

    List<Sesion> revokeOtherSessions(UUID usuarioId, UUID currentSessionId, Instant revokedAt);

    List<Sesion> revokeAllSessions(UUID usuarioId, Instant revokedAt);

    Optional<Sesion> revoke(UUID sessionId, Instant revokedAt);
}
