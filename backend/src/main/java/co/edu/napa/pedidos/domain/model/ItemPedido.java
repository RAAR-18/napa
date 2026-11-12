package co.edu.napa.pedidos.domain.model;

import co.edu.napa.shared.domain.DomainException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record ItemPedido(UUID id, UUID productoId, BigDecimal cantidad, long precioUnitario) {
    public ItemPedido {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(productoId, "productoId");
        if (cantidad == null || cantidad.signum() <= 0 || cantidad.compareTo(BigDecimal.valueOf(20)) > 0
                || cantidad.stripTrailingZeros().scale() > 2) {
            throw new DomainException("VALIDATION_ERROR", "La cantidad debe estar entre 0 y 20, con hasta dos decimales.");
        }
        if (precioUnitario <= 0) {
            throw new DomainException("VALIDATION_ERROR", "El precio unitario debe ser positivo.");
        }
    }
}
