package co.edu.napa.pedidos.domain.model;

import co.edu.napa.shared.domain.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Pedido(
        UUID id,
        UUID compradorId,
        UUID emprendimientoId,
        ModalidadEntrega modalidad,
        EstadoPedido estado,
        Instant reserva,
        MedioPagoPedido medioPago,
        Long tarifaDomicilio,
        long total,
        UUID ubicacionEntregaId,
        List<ItemPedido> items,
        Instant createdAt,
        Instant updatedAt
) {
    public Pedido {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(compradorId, "compradorId");
        Objects.requireNonNull(emprendimientoId, "emprendimientoId");
        Objects.requireNonNull(modalidad, "modalidad");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(medioPago, "medioPago");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (items.isEmpty() || items.size() > 20
                || new HashSet<>(items.stream().map(ItemPedido::productoId).toList()).size() != items.size()) {
            throw new DomainException("VALIDATION_ERROR", "El pedido debe tener entre 1 y 20 productos distintos.");
        }
        if (total < 0) throw new DomainException("VALIDATION_ERROR", "El total no puede ser negativo.");
        boolean hasLocation = ubicacionEntregaId != null;
        if (modalidad == ModalidadEntrega.RECOGIDA && (hasLocation || tarifaDomicilio != null)
                || modalidad == ModalidadEntrega.ENTREGA_DIRECTA && (!hasLocation || tarifaDomicilio != null)
                || modalidad == ModalidadEntrega.DOMICILIO
                && (!hasLocation || tarifaDomicilio == null || tarifaDomicilio <= 0
                || medioPago != MedioPagoPedido.CUENTA_BANCARIA)) {
            throw new DomainException("VALIDATION_ERROR", "Los datos del pedido no corresponden a su modalidad.");
        }
        if (calcularTotal(items, tarifaDomicilio) != total) {
            throw new DomainException("VALIDATION_ERROR", "El total no coincide con los productos y la tarifa.");
        }
    }

    public static Pedido nuevo(
            UUID compradorId,
            UUID emprendimientoId,
            ModalidadEntrega modalidad,
            Instant reserva,
            MedioPagoPedido medioPago,
            Long tarifaDomicilio,
            long total,
            UUID ubicacionEntregaId,
            List<ItemPedido> items,
            Instant now
    ) {
        return new Pedido(UUID.randomUUID(), compradorId, emprendimientoId, modalidad, EstadoPedido.PENDIENTE,
                reserva, medioPago, tarifaDomicilio, total, ubicacionEntregaId, items, now, now);
    }

    private static long calcularTotal(List<ItemPedido> items, Long tarifaDomicilio) {
        try {
            long subtotal = 0;
            for (ItemPedido item : items) {
                long linea = BigDecimal.valueOf(item.precioUnitario()).multiply(item.cantidad())
                        .setScale(0, RoundingMode.HALF_UP).longValueExact();
                subtotal = Math.addExact(subtotal, linea);
            }
            return Math.addExact(subtotal, tarifaDomicilio == null ? 0 : tarifaDomicilio);
        } catch (ArithmeticException exception) {
            throw new DomainException("VALIDATION_ERROR", "El total del pedido excede el valor permitido.");
        }
    }
}
