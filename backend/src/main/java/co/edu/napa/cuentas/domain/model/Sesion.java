package co.edu.napa.cuentas.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Sesion(
        UUID id,
        UUID usuarioId,
        String refreshTokenHash,
        String deviceId,
        long sessionVersion,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt
) {
    public Sesion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(refreshTokenHash, "refreshTokenHash");
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public Sesion rotarRefreshToken(String tokenHash) {
        return new Sesion(
                id, usuarioId, Objects.requireNonNull(tokenHash, "tokenHash"),
                deviceId, sessionVersion, createdAt, expiresAt, revokedAt
        );
    }

    public boolean estaActiva(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
