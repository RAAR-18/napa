package co.edu.napa.cuentas.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DispositivoToken(
        UUID id,
        UUID usuarioId,
        String token,
        PlataformaDispositivo plataforma,
        Instant ultimaConexionAt,
        Instant createdAt,
        Instant updatedAt
) {
    public DispositivoToken {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(usuarioId, "usuarioId");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        Objects.requireNonNull(plataforma, "plataforma");
        Objects.requireNonNull(ultimaConexionAt, "ultimaConexionAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    @Override
    public String toString() {
        return "DispositivoToken[id=%s, usuarioId=%s, token=[REDACTED], plataforma=%s, ultimaConexionAt=%s, createdAt=%s, updatedAt=%s]"
                .formatted(id, usuarioId, plataforma, ultimaConexionAt, createdAt, updatedAt);
    }
}
