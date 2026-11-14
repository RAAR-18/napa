package co.edu.napa.domicilios.domain.event;

import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** E20: el domicilio cumplió el plazo de aviso sin domiciliario (RF-14 FR-009). Lo emite el sistema. */
public record DomicilioSinAsignarEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID pedidoId,
        UUID compradorId,
        UUID vendedorId
) implements DomainEvent {
    public static DomicilioSinAsignarEvent de(Domicilio domicilio, Instant now) {
        return new DomicilioSinAsignarEvent(UUID.randomUUID(), now, domicilio.id(), domicilio.pedidoId(),
                domicilio.compradorId(), domicilio.vendedorId());
    }

    @Override public String type() { return "domicilios.domicilio-sin-asignar"; }
    @Override public String aggregateType() { return "Domicilio"; }
    /** Lo emite una tarea programada: el actor es el sistema (UUID nulo, el outbox exige un actor). */
    @Override public UUID actorUserId() { return Actores.SISTEMA; }

    @Override
    public Map<String, Object> payload() {
        return Map.of(
                "domicilioId", aggregateId,
                "pedidoId", pedidoId,
                "compradorId", compradorId,
                "vendedorId", vendedorId
        );
    }
}
