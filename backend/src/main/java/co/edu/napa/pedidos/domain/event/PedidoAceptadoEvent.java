package co.edu.napa.pedidos.domain.event;

import co.edu.napa.pedidos.domain.model.Pedido;
import co.edu.napa.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PedidoAceptadoEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID actorUserId,
        UUID compradorId,
        String modalidad,
        boolean requierePagoDigital,
        long total
) implements DomainEvent {
    public static PedidoAceptadoEvent de(Pedido pedido, UUID vendedorId, Instant now) {
        return new PedidoAceptadoEvent(UUID.randomUUID(), now, pedido.id(), vendedorId,
                pedido.compradorId(), pedido.modalidad().name(),
                pedido.modalidad().name().equals("DOMICILIO"), pedido.total());
    }

    @Override public String type() { return "pedidos.pedido-aceptado"; }
    @Override public String aggregateType() { return "Pedido"; }

    @Override
    public Map<String, Object> payload() {
        return Map.of(
                "pedidoId", aggregateId,
                "compradorId", compradorId,
                "modalidad", modalidad,
                "requierePagoDigital", requierePagoDigital,
                "total", total
        );
    }
}
