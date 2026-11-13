package co.edu.napa.productos.application.usecase;

import co.edu.napa.productos.application.ports.in.DescontarInventarioPort.LineaPedido;
import co.edu.napa.productos.application.ports.in.DevolverInventarioPort;
import co.edu.napa.productos.application.ports.out.ProductoRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class DevolverInventarioUseCase implements DevolverInventarioPort {
    private final ProductoRepositoryPort productos;
    private final Clock clock;

    public DevolverInventarioUseCase(ProductoRepositoryPort productos, Clock clock) {
        this.productos = Objects.requireNonNull(productos, "productos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public void devolver(List<LineaPedido> items) {
        if (items == null || items.stream().anyMatch(item -> item == null || item.productoId() == null
                || item.cantidad() == null || item.cantidad().signum() <= 0)) {
            throw new DomainException("VALIDATION_ERROR", "Indica los productos y cantidades que se deben devolver.");
        }
        Instant now = Instant.now(clock);
        // Mismo orden que al descontar para no provocar bloqueos cruzados.
        for (LineaPedido item : items.stream().sorted(Comparator.comparing(LineaPedido::productoId)).toList()) {
            productos.devolver(item.productoId(), item.cantidad(), now);
        }
    }
}
