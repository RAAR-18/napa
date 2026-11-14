package co.edu.napa.pedidos.domain.event;

import co.edu.napa.pedidos.domain.model.Pedido;
import co.edu.napa.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** E05: el vendedor ambulante inició la entrega directa (RF-36). Conserva el tipo del catálogo de eventos. */
public record EntregaDirectaIniciadaEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID actorUserId,
        UUID compradorId
) implements DomainEvent {
    public static EntregaDirectaIniciadaEvent de(Pedido enCamino, UUID vendedorId) {
        return new EntregaDirectaIniciadaEvent(UUID.randomUUID(), enCamino.updatedAt(), enCamino.id(), vendedorId,
                enCamino.compradorId());
    }

    @Override public String type() { return "entregas.entrega-directa-iniciada"; }
    @Override public String aggregateType() { return "Pedido"; }

    @Override
    public Map<String, Object> payload() {
        return Map.of("pedidoId", aggregateId, "compradorId", compradorId);
    }
}
