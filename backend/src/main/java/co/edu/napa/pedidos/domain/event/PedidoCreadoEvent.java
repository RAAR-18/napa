package co.edu.napa.pedidos.domain.event;

import co.edu.napa.pedidos.domain.model.Pedido;
import co.edu.napa.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record PedidoCreadoEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID compradorId,
        UUID vendedorId,
        String modalidad,
        Instant reserva
) implements DomainEvent {
    public static PedidoCreadoEvent de(Pedido pedido, UUID vendedorId, Instant occurredAt) {
        return new PedidoCreadoEvent(UUID.randomUUID(), occurredAt, pedido.id(), pedido.compradorId(),
                vendedorId, pedido.modalidad().name(), pedido.reserva());
    }

    @Override public String type() { return "pedidos.pedido-creado"; }
    @Override public String aggregateType() { return "Pedido"; }
    @Override public UUID actorUserId() { return compradorId; }

    @Override
    public Map<String, Object> payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pedidoId", aggregateId);
        payload.put("compradorId", compradorId);
        payload.put("vendedorId", vendedorId);
        payload.put("modalidad", modalidad);
        if (reserva != null) payload.put("reserva", reserva);
        return Map.copyOf(payload);
    }
}
