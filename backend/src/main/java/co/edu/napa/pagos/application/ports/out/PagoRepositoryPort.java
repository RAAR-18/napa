package co.edu.napa.pagos.application.ports.out;

import co.edu.napa.pagos.domain.model.Pago;
import co.edu.napa.pagos.domain.model.EstadoPago;

import java.util.Optional;
import java.util.UUID;

public interface PagoRepositoryPort {
    Pago guardar(Pago pago);
    Optional<EstadoPago> consultarEstadoPorPedido(UUID pedidoId);
}
