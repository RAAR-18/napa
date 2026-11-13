package co.edu.napa.productos.application.usecase;

import co.edu.napa.productos.application.ports.in.DescontarInventarioPort;
import co.edu.napa.productos.application.ports.in.DescontarInventarioPort.LineaPedido;
import co.edu.napa.productos.application.ports.out.ProductoRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Component
public class DescontarInventarioUseCase implements DescontarInventarioPort {
    private final ProductoRepositoryPort productos;
    private final Clock clock;

    public DescontarInventarioUseCase(ProductoRepositoryPort productos, Clock clock) {
        this.productos = Objects.requireNonNull(productos, "productos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public void descontar(List<LineaPedido> items) {
        if (items == null || items.isEmpty()
                || items.stream().anyMatch(item -> item == null || item.productoId() == null
                || item.cantidad() == null || item.cantidad().signum() <= 0)
                || new HashSet<>(items.stream().map(LineaPedido::productoId).toList()).size() != items.size()) {
            throw new DomainException("VALIDATION_ERROR", "Indica los productos y cantidades que se deben descontar.");
        }
        Instant now = Instant.now(clock);
        for (LineaPedido item : items.stream()
                .sorted(Comparator.comparing(LineaPedido::productoId)).toList()) {
            if (!productos.descontar(item.productoId(), item.cantidad(), now)) {
                throw new DomainException("OUT_OF_STOCK",
                        "Ajusta tu inventario o rechaza el pedido.",
                        java.util.Map.of("productoId", item.productoId()));
            }
        }
    }
}
