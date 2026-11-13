package co.edu.napa.pedidos.domain.event;

import co.edu.napa.pedidos.domain.model.Pedido;
import co.edu.napa.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** E06: el pedido de recogida está listo para retirar (RF-64). */
public record PedidoListoParaRecogerEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID actorUserId,
        UUID compradorId
) implements DomainEvent {
    public static PedidoListoParaRecogerEvent de(Pedido listo, UUID vendedorId) {
        return new PedidoListoParaRecogerEvent(UUID.randomUUID(), listo.updatedAt(), listo.id(), vendedorId,
                listo.compradorId());
    }

    @Override public String type() { return "pedidos.pedido-listo-para-recoger"; }
    @Override public String aggregateType() { return "Pedido"; }

    @Override
    public Map<String, Object> payload() {
        return Map.of("pedidoId", aggregateId, "compradorId", compradorId);
    }
}
