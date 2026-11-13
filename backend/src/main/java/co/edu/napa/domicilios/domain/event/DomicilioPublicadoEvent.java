package co.edu.napa.domicilios.domain.event;

import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** E08: se publicó un domicilio y está disponible para los domiciliarios (RF-14). */
public record DomicilioPublicadoEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID actorUserId,
        UUID pedidoId,
        long tarifa
) implements DomainEvent {
    public static DomicilioPublicadoEvent de(Domicilio domicilio) {
        return new DomicilioPublicadoEvent(UUID.randomUUID(), domicilio.createdAt(), domicilio.id(),
                domicilio.vendedorId(), domicilio.pedidoId(), domicilio.tarifa());
    }

    @Override public String type() { return "domicilios.domicilio-publicado"; }
    @Override public String aggregateType() { return "Domicilio"; }

    @Override
    public Map<String, Object> payload() {
        return Map.of(
                "domicilioId", aggregateId,
                "pedidoId", pedidoId,
                "vendedorId", actorUserId,
                "tarifa", tarifa
        );
    }
}
