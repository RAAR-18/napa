package co.edu.napa.pedidos.application.ports.in;

import java.util.UUID;

/**
 * Pedidos de un emprendimiento que aún no se cierran (pendientes, aceptados, listos o en camino), reservas
 * incluidas. Lo usan la edición de la ubicación (RF-29) y la eliminación del emprendimiento (RF-30).
 */
public interface ConsultarPedidosSinCerrarPort {
    long contarDeEmprendimiento(UUID emprendimientoId);
}
