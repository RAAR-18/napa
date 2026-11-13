package co.edu.napa.productos.application.ports.in;

import java.util.List;
import java.util.UUID;

public interface BloquearProductosParaPedidoPort {
    void bloquear(UUID emprendimientoId, List<UUID> productoIds);
}
