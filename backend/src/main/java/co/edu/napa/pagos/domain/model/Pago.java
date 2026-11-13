package co.edu.napa.pagos.domain.model;

import co.edu.napa.shared.domain.DomainException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.Map;

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

    public Pago confirmar(Instant now) {
        Objects.requireNonNull(now, "now");
        if (tipo != TipoPago.PEDIDO) {
            throw new DomainException("BUSINESS_RULE", "Este pago no corresponde a un pedido.");
        }
        if (estado == EstadoPago.CONFIRMADO) {
            throw new DomainException("ALREADY_CONFIRMED", "El pago ya está confirmado.");
        }
        if (estado != EstadoPago.PENDIENTE_CONFIRMACION) {
            throw new DomainException("INVALID_STATE", "El pago no está pendiente de confirmación.",
                    Map.of("estadoActual", estado.name()));
        }
        return new Pago(id, tipo, monto, medio, EstadoPago.CONFIRMADO, now, pedidoId, domicilioId,
                compradorId, vendedorId, domiciliarioId, createdAt, now);
    }

    /** RF-43: el vendedor deja constancia de que transfirió la tarifa; siempre por cuenta bancaria. */
    public static Pago tarifaRegistrada(
            UUID domicilioId,
            UUID compradorId,
            UUID vendedorId,
            UUID domiciliarioId,
            long monto,
            Instant now
    ) {
        Objects.requireNonNull(domiciliarioId, "domiciliarioId");
        return new Pago(UUID.randomUUID(), TipoPago.TARIFA_DOMICILIO, monto, MedioPago.CUENTA_BANCARIA,
                EstadoPago.REGISTRADO, null, null, domicilioId, compradorId, vendedorId, domiciliarioId, now, now);
    }

    /** RF-43: el domiciliario confirma que la transferencia le llegó. */
    public Pago confirmarTarifa(Instant now) {
        Objects.requireNonNull(now, "now");
        if (tipo != TipoPago.TARIFA_DOMICILIO) {
            throw new DomainException("BUSINESS_RULE", "Este pago no corresponde a una tarifa de domicilio.");
        }
        if (estado == EstadoPago.CONFIRMADO) {
            throw new DomainException("ALREADY_CONFIRMED", "Ya confirmaste este pago.");
        }
        return new Pago(id, tipo, monto, medio, EstadoPago.CONFIRMADO, now, pedidoId, domicilioId,
                compradorId, vendedorId, domiciliarioId, createdAt, now);
    }
}
