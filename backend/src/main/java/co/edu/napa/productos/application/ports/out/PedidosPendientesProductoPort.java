package co.edu.napa.productos.application.ports.out;

import java.util.UUID;

/** Debe contar todos los pedidos no finalizados, incluidas reservas. Un fallo de consulta no equivale a cero. */
public interface PedidosPendientesProductoPort {
    long contar(UUID productoId);
}
