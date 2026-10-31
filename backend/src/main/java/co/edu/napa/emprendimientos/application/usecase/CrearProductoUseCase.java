package co.edu.napa.emprendimientos.application.usecase;

import co.edu.napa.emprendimientos.application.ports.in.CrearProductoPort;
import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoRepositoryPort;
import co.edu.napa.emprendimientos.application.ports.out.ProductoRepositoryPort;
import co.edu.napa.emprendimientos.domain.model.Producto;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Component
public class CrearProductoUseCase implements CrearProductoPort {
    private final EmprendimientoRepositoryPort emprendimientos;
    private final ProductoRepositoryPort productos;
    private final Clock clock;

    public CrearProductoUseCase(
            EmprendimientoRepositoryPort emprendimientos,
            ProductoRepositoryPort productos,
            Clock clock
    ) {
        this.emprendimientos = Objects.requireNonNull(emprendimientos, "emprendimientos");
        this.productos = Objects.requireNonNull(productos, "productos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public Producto crear(
            UUID usuarioId,
            String nombre,
            BigDecimal precio,
            String unidad,
            String descripcion,
            int cantidadDisponible,
            String imagenUrl
    ) {
        var emprendimiento = emprendimientos.findActivoByUsuarioId(usuarioId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "Debes crear un emprendimiento antes de publicar productos."));
        return productos.save(Producto.crear(
                emprendimiento.id(), nombre, precio, unidad, descripcion,
                cantidadDisponible, imagenUrl, Instant.now(clock)
        ));
    }
}
