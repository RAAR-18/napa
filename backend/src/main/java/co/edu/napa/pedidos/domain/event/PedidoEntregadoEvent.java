package co.edu.napa.pedidos.domain.event;

import co.edu.napa.pedidos.domain.model.Pedido;
import co.edu.napa.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** E07: el pedido fue entregado. Con {@code domicilioId} cuando lo cerró un domicilio (RF-24). */
public record PedidoEntregadoEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID actorUserId,
        UUID compradorId,
        UUID emprendimientoId,
        String modalidad,
        UUID domicilioId
) implements DomainEvent {
    public static PedidoEntregadoEvent porDomicilio(Pedido entregado, UUID domicilioId, UUID actor) {
        return new PedidoEntregadoEvent(UUID.randomUUID(), entregado.updatedAt(), entregado.id(), actor,
                entregado.compradorId(), entregado.emprendimientoId(), entregado.modalidad().name(), domicilioId);
    }

    /** RF-37 y RF-65: el vendedor confirmó la entrega directa o el retiro, con el cobro. */
    public static PedidoEntregadoEvent porVendedor(Pedido entregado, UUID vendedorId) {
        return new PedidoEntregadoEvent(UUID.randomUUID(), entregado.updatedAt(), entregado.id(), vendedorId,
                entregado.compradorId(), entregado.emprendimientoId(), entregado.modalidad().name(), null);
    }

    @Override public String type() { return "pedidos.pedido-entregado"; }
    @Override public String aggregateType() { return "Pedido"; }

    @Override
    public Map<String, Object> payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pedidoId", aggregateId);
        payload.put("compradorId", compradorId);
        payload.put("emprendimientoId", emprendimientoId);
        payload.put("modalidad", modalidad);
        if (domicilioId != null) {
            payload.put("domicilioId", domicilioId);
        }
        return Map.copyOf(payload);
    }
}
