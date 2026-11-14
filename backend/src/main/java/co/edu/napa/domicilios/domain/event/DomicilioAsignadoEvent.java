package co.edu.napa.domicilios.domain.event;

import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** E09: un domiciliario aceptó el domicilio (RF-19). */
public record DomicilioAsignadoEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID actorUserId,
        UUID compradorId,
        UUID vendedorId,
        UUID pedidoId
) implements DomainEvent {
    public static DomicilioAsignadoEvent de(Domicilio asignado) {
        return new DomicilioAsignadoEvent(UUID.randomUUID(), asignado.updatedAt(), asignado.id(),
                asignado.domiciliarioId(), asignado.compradorId(), asignado.vendedorId(), asignado.pedidoId());
    }

    @Override public String type() { return "domicilios.domicilio-asignado"; }
    @Override public String aggregateType() { return "Domicilio"; }

    @Override
    public Map<String, Object> payload() {
        return Map.of(
                "domicilioId", aggregateId,
                "domiciliarioId", actorUserId,
                "compradorId", compradorId,
                "vendedorId", vendedorId,
                "pedidoId", pedidoId
        );
    }
}
