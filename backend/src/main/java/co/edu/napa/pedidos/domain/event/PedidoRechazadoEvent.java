package co.edu.napa.pedidos.domain.event;

import co.edu.napa.pedidos.domain.model.Pedido;
import co.edu.napa.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PedidoRechazadoEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID actorUserId,
        UUID compradorId
) implements DomainEvent {
    public static PedidoRechazadoEvent de(Pedido pedido, UUID vendedorId, Instant now) {
        return new PedidoRechazadoEvent(UUID.randomUUID(), now, pedido.id(), vendedorId, pedido.compradorId());
    }

    @Override public String type() { return "pedidos.pedido-rechazado"; }
    @Override public String aggregateType() { return "Pedido"; }

    @Override
    public Map<String, Object> payload() {
        return Map.of("pedidoId", aggregateId, "compradorId", compradorId);
    }
}
