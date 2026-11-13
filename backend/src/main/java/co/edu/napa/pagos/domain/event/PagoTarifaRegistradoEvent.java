package co.edu.napa.pagos.domain.event;

import co.edu.napa.pagos.domain.model.Pago;
import co.edu.napa.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** E14: avisa al domiciliario de que el vendedor registró el pago de su tarifa (RF-43). */
public record PagoTarifaRegistradoEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID actorUserId,
        UUID domicilioId,
        UUID domiciliarioId,
        long monto
) implements DomainEvent {
    public static PagoTarifaRegistradoEvent de(Pago pago) {
        return new PagoTarifaRegistradoEvent(UUID.randomUUID(), pago.createdAt(), pago.id(), pago.vendedorId(),
                pago.domicilioId(), pago.domiciliarioId(), pago.monto());
    }

    @Override
    public String type() {
        return "pagos.pago-tarifa-registrado";
    }

    @Override
    public String aggregateType() {
        return "Pago";
    }

    @Override
    public Map<String, Object> payload() {
        return Map.of(
                "pagoId", aggregateId,
                "domicilioId", domicilioId,
                "domiciliarioId", domiciliarioId,
                "monto", monto
        );
    }
}
