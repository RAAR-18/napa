package co.edu.napa.notificaciones.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Notificacion(
        UUID id,
        UUID usuarioId,
        String tipo,
        String titulo,
        String cuerpo,
        Instant leidaAt,
        Instant createdAt
) {
    public Notificacion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(titulo, "titulo");
        Objects.requireNonNull(cuerpo, "cuerpo");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
