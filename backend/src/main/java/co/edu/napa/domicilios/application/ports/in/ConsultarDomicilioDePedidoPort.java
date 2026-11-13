package co.edu.napa.domicilios.application.ports.in;

import java.util.Optional;
import java.util.UUID;

/** El domicilio más reciente de un pedido, para el detalle del pedido del vendedor (RF-51). */
public interface ConsultarDomicilioDePedidoPort {
    Optional<DomicilioDePedido> porPedido(UUID pedidoId);

    /** {@code domiciliarioId} es nulo mientras nadie lo acepta (RF-27). */
    record DomicilioDePedido(
            UUID id,
            String estado,
            String motivoCancelacion,
            java.time.Instant updatedAt,
            UUID domiciliarioId
    ) {
        public DomicilioDePedido(UUID id, String estado, String motivoCancelacion, java.time.Instant updatedAt) {
            this(id, estado, motivoCancelacion, updatedAt, null);
        }
    }
}
