package co.edu.napa.domicilios.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Cambio de estado de un domicilio (RF-26). Inmutable; {@code usuarioId} es nulo si lo hizo el sistema. */
public record EventoTrazabilidad(
        UUID id,
        UUID domicilioId,
        EstadoDomicilio estado,
        UUID usuarioId,
        Instant ocurridoAt
) {
    public EventoTrazabilidad {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(domicilioId, "domicilioId");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(ocurridoAt, "ocurridoAt");
    }

    public static EventoTrazabilidad de(Domicilio domicilio, UUID usuarioId) {
        return new EventoTrazabilidad(UUID.randomUUID(), domicilio.id(), domicilio.estado(), usuarioId,
                domicilio.updatedAt());
    }
}
