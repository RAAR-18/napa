package co.edu.napa.pagos.application.ports.in;

import java.util.Optional;
import java.util.UUID;

public interface ConsultarEstadoPagoPedidoPort {
    Optional<String> consultar(UUID pedidoId);
}
