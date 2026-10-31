package co.edu.napa.emprendimientos.application.ports.in;

import co.edu.napa.emprendimientos.domain.model.Producto;

import java.math.BigDecimal;
import java.util.UUID;

public interface CrearProductoPort {
    Producto crear(
            UUID usuarioId,
            String nombre,
            BigDecimal precio,
            String unidad,
            String descripcion,
            int cantidadDisponible,
            String imagenUrl
    );
}
