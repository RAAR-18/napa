package co.edu.napa.pagos.domain.event;

import co.edu.napa.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PagoPedidoConfirmadoEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID actorUserId,
        UUID pedidoId,
        UUID compradorId,
        String modalidad,
        String medio,
        long monto
) implements DomainEvent {
    public static PagoPedidoConfirmadoEvent de(
            UUID pagoId,
            UUID pedidoId,
            UUID compradorId,
            UUID vendedorId,
            String modalidad,
            String medio,
            long monto,
            Instant now
    ) {
        return new PagoPedidoConfirmadoEvent(UUID.randomUUID(), now, pagoId, vendedorId,
                pedidoId, compradorId, modalidad, medio, monto);
    }

    @Override
    public String type() {
        return "pagos.pago-pedido-confirmado";
    }

    @Override
    public String aggregateType() {
        return "Pago";
    }

    @Override
    public Map<String, Object> payload() {
        return Map.of(
                "pagoId", aggregateId,
                "pedidoId", pedidoId,
                "compradorId", compradorId,
                "modalidad", modalidad,
                "medio", medio,
                "monto", monto
        );
    }
}
