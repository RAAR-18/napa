package co.edu.napa.domicilios.domain.event;

import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** E12 (RF-24): el domiciliario entregó el pedido; el vendedor debe pagarle la tarifa. */
public record DomicilioEntregadoEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID actorUserId,
        UUID pedidoId,
        UUID compradorId,
        UUID vendedorId,
        long tarifa
) implements DomainEvent {
    public static DomicilioEntregadoEvent de(Domicilio entregado) {
        return new DomicilioEntregadoEvent(UUID.randomUUID(), entregado.updatedAt(), entregado.id(),
                entregado.domiciliarioId(), entregado.pedidoId(), entregado.compradorId(), entregado.vendedorId(),
                entregado.tarifa());
    }

    @Override public String type() { return "domicilios.domicilio-entregado"; }
    @Override public String aggregateType() { return "Domicilio"; }

    @Override
    public Map<String, Object> payload() {
        return Map.of(
                "domicilioId", aggregateId,
                "pedidoId", pedidoId,
                "compradorId", compradorId,
                "vendedorId", vendedorId,
                "domiciliarioId", actorUserId,
                "tarifa", tarifa
        );
    }
}
