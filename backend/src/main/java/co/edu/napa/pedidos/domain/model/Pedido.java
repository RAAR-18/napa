package co.edu.napa.pedidos.domain.model;

import co.edu.napa.shared.domain.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Map;

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
    /** Una reserva se puede iniciar o marcar lista desde este margen antes de la hora acordada (RF-36, RF-64). */
    public static final Duration MARGEN_RESERVA = Duration.ofMinutes(15);

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

    public Pedido aceptar(Instant now) {
        requirePending();
        return new Pedido(id, compradorId, emprendimientoId, modalidad, EstadoPedido.ACEPTADO, reserva,
                medioPago, tarifaDomicilio, total, ubicacionEntregaId, items, createdAt,
                Objects.requireNonNull(now, "now"));
    }

    public Pedido rechazar(Instant now) {
        requirePending();
        return new Pedido(id, compradorId, emprendimientoId, modalidad, EstadoPedido.RECHAZADO, reserva,
                medioPago, tarifaDomicilio, total, ubicacionEntregaId, items, createdAt,
                Objects.requireNonNull(now, "now"));
    }

    /** Cancela el pedido cuando se cancela su domicilio (RF-13, RF-14 FR-009). */
    public Pedido cancelarPorDomicilio(Instant now) {
        if (modalidad != ModalidadEntrega.DOMICILIO
                || (estado != EstadoPedido.ACEPTADO && estado != EstadoPedido.EN_CAMINO)) {
            throw new DomainException("INVALID_STATE", "El pedido no está en un estado que permita cancelarlo.",
                    Map.of("estadoActual", estado.name()));
        }
        return new Pedido(id, compradorId, emprendimientoId, modalidad, EstadoPedido.CANCELADO, reserva,
                medioPago, tarifaDomicilio, total, ubicacionEntregaId, items, createdAt,
                Objects.requireNonNull(now, "now"));
    }

    /** RF-23: el vendedor confirmó la salida del domiciliario. */
    public Pedido ponerEnCaminoPorDomicilio(Instant now) {
        return transicionDomicilio(EstadoPedido.ACEPTADO, EstadoPedido.EN_CAMINO, now);
    }

    /** RF-24: el domiciliario confirmó la entrega con el código del cliente. */
    public Pedido entregarPorDomicilio(Instant now) {
        return transicionDomicilio(EstadoPedido.EN_CAMINO, EstadoPedido.ENTREGADO, now);
    }

    /** RF-36: el vendedor ambulante sale a entregar el pedido. */
    public Pedido iniciarEntregaDirecta(Instant now) {
        return transicionPropia(ModalidadEntrega.ENTREGA_DIRECTA, EstadoPedido.ACEPTADO, EstadoPedido.EN_CAMINO,
                true, now);
    }

    /** RF-37: el vendedor ambulante entregó el pedido y cobró. */
    public Pedido confirmarEntregaDirecta(Instant now) {
        return transicionPropia(ModalidadEntrega.ENTREGA_DIRECTA, EstadoPedido.EN_CAMINO, EstadoPedido.ENTREGADO,
                false, now);
    }

    /** RF-64: el pedido de recogida ya se puede retirar en el punto fijo. */
    public Pedido marcarListoParaRecoger(Instant now) {
        return transicionPropia(ModalidadEntrega.RECOGIDA, EstadoPedido.ACEPTADO, EstadoPedido.LISTO_PARA_RECOGER,
                true, now);
    }

    /** RF-65: el cliente retiró y pagó el pedido. */
    public Pedido confirmarRetiro(Instant now) {
        return transicionPropia(ModalidadEntrega.RECOGIDA, EstadoPedido.LISTO_PARA_RECOGER, EstadoPedido.ENTREGADO,
                false, now);
    }

    /** Desde cuándo se puede iniciar o marcar lista una reserva; nulo en un pedido inmediato. */
    public Instant disponibleDesde() {
        return reserva == null ? null : reserva.minus(MARGEN_RESERVA);
    }

    private Pedido transicionPropia(
            ModalidadEntrega requerida,
            EstadoPedido desde,
            EstadoPedido hacia,
            boolean respetaReserva,
            Instant now
    ) {
        Objects.requireNonNull(now, "now");
        if (modalidad != requerida) {
            throw new DomainException("BUSINESS_RULE", requerida == ModalidadEntrega.RECOGIDA
                    ? "Este pedido no es de recogida."
                    : "Este pedido no es de entrega directa.");
        }
        if (estado != desde) {
            throw new DomainException("INVALID_STATE", desde == EstadoPedido.ACEPTADO
                    ? "El pedido debe estar aceptado."
                    : "El pedido no está en un estado que permita este cambio.",
                    Map.of("estadoActual", estado.name()));
        }
        Instant desdeReserva = disponibleDesde();
        if (respetaReserva && desdeReserva != null && now.isBefore(desdeReserva)) {
            throw new DomainException("RESERVATION_NOT_DUE",
                    "Esta reserva solo se puede atender desde 15 minutos antes de la hora acordada.",
                    Map.of("disponibleDesde", desdeReserva.toString()));
        }
        return new Pedido(id, compradorId, emprendimientoId, modalidad, hacia, reserva,
                medioPago, tarifaDomicilio, total, ubicacionEntregaId, items, createdAt, now);
    }

    private Pedido transicionDomicilio(EstadoPedido desde, EstadoPedido hacia, Instant now) {
        if (modalidad != ModalidadEntrega.DOMICILIO || estado != desde) {
            throw new DomainException("INVALID_STATE", "El pedido no está en un estado que permita este cambio.",
                    Map.of("estadoActual", estado.name()));
        }
        return new Pedido(id, compradorId, emprendimientoId, modalidad, hacia, reserva,
                medioPago, tarifaDomicilio, total, ubicacionEntregaId, items, createdAt,
                Objects.requireNonNull(now, "now"));
    }

    private void requirePending() {
        if (estado != EstadoPedido.PENDIENTE) {
            throw new DomainException("INVALID_STATE", "El pedido ya no está pendiente.",
                    Map.of("estadoActual", estado.name()));
        }
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
