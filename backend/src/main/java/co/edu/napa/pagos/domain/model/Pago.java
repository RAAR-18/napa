package co.edu.napa.pagos.domain.model;

import co.edu.napa.shared.domain.DomainException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Pago(
        UUID id,
        TipoPago tipo,
        long monto,
        MedioPago medio,
        EstadoPago estado,
        Instant confirmadoAt,
        UUID pedidoId,
        UUID domicilioId,
        UUID compradorId,
        UUID vendedorId,
        UUID domiciliarioId,
        Instant createdAt,
        Instant updatedAt
) {
    public Pago {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(medio, "medio");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(compradorId, "compradorId");
        Objects.requireNonNull(vendedorId, "vendedorId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (monto <= 0) throw new DomainException("VALIDATION_ERROR", "El monto del pago debe ser positivo.");
        boolean pagoPedido = pedidoId != null && domicilioId == null;
        boolean pagoDomicilio = pedidoId == null && domicilioId != null;
        if (tipo == TipoPago.PEDIDO && !pagoPedido
                || tipo == TipoPago.TARIFA_DOMICILIO
                && (!pagoDomicilio || medio != MedioPago.CUENTA_BANCARIA)) {
            throw new DomainException("VALIDATION_ERROR", "El pago debe corresponder a un pedido o domicilio.");
        }
    }

    public static Pago pedidoPendiente(
            UUID pedidoId,
            UUID compradorId,
            UUID vendedorId,
            long monto,
            MedioPago medio,
            Instant now
    ) {
        return new Pago(UUID.randomUUID(), TipoPago.PEDIDO, monto, medio,
                EstadoPago.PENDIENTE_CONFIRMACION, null, pedidoId, null, compradorId, vendedorId, null, now, now);
    }
}
