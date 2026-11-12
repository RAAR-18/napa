package co.edu.napa.productos.application.usecase;

import co.edu.napa.productos.application.ports.in.BloquearProductosParaPedidoPort;
import co.edu.napa.productos.application.ports.out.ProductoRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class BloquearProductosParaPedidoUseCase implements BloquearProductosParaPedidoPort {
    private final ProductoRepositoryPort productos;

    public BloquearProductosParaPedidoUseCase(ProductoRepositoryPort productos) {
        this.productos = Objects.requireNonNull(productos, "productos");
    }

    @Override
    @Transactional
    public void bloquear(UUID emprendimientoId, List<UUID> productoIds) {
        if (emprendimientoId == null || productoIds == null || productoIds.isEmpty()
                || productoIds.size() > 20 || productoIds.stream().anyMatch(Objects::isNull)) {
            throw new DomainException("VALIDATION_ERROR", "Indica entre 1 y 20 productos válidos.");
        }
        List<UUID> ids = productoIds.stream().distinct().sorted().toList();
        if (ids.size() != productoIds.size()) {
            throw new DomainException("VALIDATION_ERROR", "No repitas un producto en el pedido.");
        }
        if (productos.bloquearDisponibles(emprendimientoId, ids).size() != ids.size()) {
            throw new DomainException("NOT_FOUND", "Uno de los productos no está disponible.");
        }
    }
}
