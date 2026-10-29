package co.edu.napa.cuentas.domain.model;

import co.edu.napa.shared.domain.DomainException;

import java.time.Duration;
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
    private static final int LONGITUD_MAXIMA_DEVICE_ID = 128;

    public Sesion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(refreshTokenHash, "refreshTokenHash");
        validarDeviceId(deviceId);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /** Sesión recién iniciada: versión 1 y vigente hasta now + ttl. */
    public static Sesion nueva(
            UUID usuarioId,
            String refreshTokenHash,
            String deviceId,
            Instant now,
            Duration refreshTokenTtl
    ) {
        return new Sesion(
                UUID.randomUUID(),
                usuarioId,
                refreshTokenHash,
                deviceId,
                1,
                now,
                now.plus(refreshTokenTtl),
                null
        );
    }

    public static void validarDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank() || deviceId.length() > LONGITUD_MAXIMA_DEVICE_ID) {
            throw new DomainException("VALIDATION_ERROR", "La cabecera X-Device-Id es obligatoria.");
        }
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