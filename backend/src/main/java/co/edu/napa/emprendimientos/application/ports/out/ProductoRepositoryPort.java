package co.edu.napa.emprendimientos.application.ports.out;

import co.edu.napa.emprendimientos.domain.model.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProductoRepositoryPort {
    Producto save(Producto producto);

    Page<ProductoListRow> findDisponibles(Pageable pageable);

    Page<ProductoListRow> findDisponiblesByUsuario(UUID usuarioId, Pageable pageable);

    interface ProductoListRow {
        UUID getId();
        UUID getEmprendimientoId();
        String getEmprendimientoNombre();
        String getNombre();
        java.math.BigDecimal getPrecio();
        String getUnidad();
        String getDescripcion();
        int getCantidadDisponible();
        String getImagenUrl();
    }
}
