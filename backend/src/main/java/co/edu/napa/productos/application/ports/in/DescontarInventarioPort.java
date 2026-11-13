package co.edu.napa.productos.application.ports.in;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface DescontarInventarioPort {
    void descontar(List<LineaPedido> items);

    record LineaPedido(UUID productoId, BigDecimal cantidad) {}
}
