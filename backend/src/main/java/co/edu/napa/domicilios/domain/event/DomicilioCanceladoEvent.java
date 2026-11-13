package co.edu.napa.domicilios.domain.event;

import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** E15: el domicilio y su pedido quedaron cancelados (RF-13, o RF-14 FR-009 si nadie lo tomó). */
public record DomicilioCanceladoEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID actorUserId,
        UUID pedidoId,
        UUID compradorId,
        UUID vendedorId,
        UUID domiciliarioId,
        String motivo
) implements DomainEvent {
    public static DomicilioCanceladoEvent de(Domicilio cancelado, UUID domiciliarioPrevio, UUID actorUserId) {
        return new DomicilioCanceladoEvent(UUID.randomUUID(), cancelado.updatedAt(), cancelado.id(), actorUserId,
                cancelado.pedidoId(), cancelado.compradorId(), cancelado.vendedorId(), domiciliarioPrevio,
                cancelado.motivoCancelacion().name());
    }

    @Override public String type() { return "domicilios.domicilio-cancelado"; }
    @Override public String aggregateType() { return "Domicilio"; }

    @Override
    public Map<String, Object> payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("domicilioId", aggregateId);
        payload.put("pedidoId", pedidoId);
        payload.put("compradorId", compradorId);
        payload.put("vendedorId", vendedorId);
        if (domiciliarioId != null) {
            payload.put("domiciliarioId", domiciliarioId);
        }
        payload.put("motivo", motivo);
        return payload;
    }
}
