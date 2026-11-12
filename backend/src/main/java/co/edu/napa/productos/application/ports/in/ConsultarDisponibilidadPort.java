package co.edu.napa.productos.application.ports.in;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ConsultarDisponibilidadPort {
    List<ProductoDisponible> consultar(UUID emprendimientoId, List<UUID> productoIds);

    record ProductoDisponible(
            UUID productoId,
            String nombre,
            String unidad,
            long precio,
            BigDecimal cantidadDisponible,
            String estadoProducto
    ) {}
}
