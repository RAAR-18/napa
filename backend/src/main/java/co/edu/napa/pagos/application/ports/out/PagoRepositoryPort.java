package co.edu.napa.pagos.application.ports.out;

import co.edu.napa.pagos.domain.model.Pago;
import co.edu.napa.pagos.domain.model.EstadoPago;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PagoRepositoryPort {
    Pago guardar(Pago pago);
    Optional<EstadoPago> consultarEstadoPorPedido(UUID pedidoId);
    Optional<Pago> bloquearPorPedido(UUID pedidoId);
    /** Pasa un pago pendiente o registrado a confirmado. */
    void actualizar(Pago pago);

    Optional<Pago> bloquearTarifaPorDomicilio(UUID domicilioId);

    Optional<Pago> buscarTarifaPorDomicilio(UUID domicilioId);

    List<FilaHistorial> listarHistorial(HistorialPagosQuery query);

    long contarHistorial(HistorialPagosQuery query);

    /** {@code modalidad} es la del pedido en los pagos de pedido. */
    record FilaHistorial(Pago pago, String modalidad) {}
}
