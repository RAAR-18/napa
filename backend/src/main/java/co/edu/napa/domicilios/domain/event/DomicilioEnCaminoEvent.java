package co.edu.napa.domicilios.domain.event;

import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** E11 (RF-23: el vendedor confirmó la salida del domiciliario). */
public record DomicilioEnCaminoEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID actorUserId,
        UUID pedidoId,
        UUID compradorId,
        UUID vendedorId,
        UUID domiciliarioId
) implements DomainEvent {
    public static DomicilioEnCaminoEvent de(Domicilio domicilio, UUID actor) {
        return new DomicilioEnCaminoEvent(UUID.randomUUID(), domicilio.updatedAt(), domicilio.id(), actor,
                domicilio.pedidoId(), domicilio.compradorId(), domicilio.vendedorId(), domicilio.domiciliarioId());
    }

    @Override public String type() { return "domicilios.domicilio-en-camino"; }
    @Override public String aggregateType() { return "Domicilio"; }

    @Override
    public Map<String, Object> payload() {
        return Map.of(
                "domicilioId", aggregateId,
                "pedidoId", pedidoId,
                "compradorId", compradorId,
                "vendedorId", vendedorId,
                "domiciliarioId", domiciliarioId
        );
    }
}
