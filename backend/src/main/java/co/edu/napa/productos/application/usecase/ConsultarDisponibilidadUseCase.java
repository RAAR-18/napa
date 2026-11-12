package co.edu.napa.productos.application.usecase;

import co.edu.napa.emprendimientos.application.ports.in.ConsultarEmprendimientoPort;
import co.edu.napa.productos.application.ports.in.ConsultarDisponibilidadPort;
import co.edu.napa.productos.application.ports.out.ProductoRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class ConsultarDisponibilidadUseCase implements ConsultarDisponibilidadPort {
    private final ProductoRepositoryPort productos;
    private final ConsultarEmprendimientoPort emprendimientos;

    public ConsultarDisponibilidadUseCase(
            ProductoRepositoryPort productos,
            ConsultarEmprendimientoPort emprendimientos
    ) {
        this.productos = Objects.requireNonNull(productos, "productos");
        this.emprendimientos = Objects.requireNonNull(emprendimientos, "emprendimientos");
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoDisponible> consultar(UUID emprendimientoId, List<UUID> productoIds) {
        if (emprendimientoId == null || productoIds == null || productoIds.stream().anyMatch(Objects::isNull)) {
            throw new DomainException("VALIDATION_ERROR", "Indica un emprendimiento y productos válidos.");
        }
        emprendimientos.activoPorId(emprendimientoId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "El emprendimiento no está disponible."));
        if (productoIds.isEmpty()) return List.of();

        return productos.consultarDisponibilidad(emprendimientoId, productoIds.stream().distinct().toList())
                .stream()
                .map(producto -> new ProductoDisponible(
                        producto.id(),
                        producto.nombre(),
                        producto.unidad().name(),
                        producto.precio(),
                        producto.cantidadDisponible(),
                        producto.estado().name()))
                .toList();
    }
}
