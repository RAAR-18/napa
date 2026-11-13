package co.edu.napa.pagos.application.ports.in;

import java.time.Instant;
import java.util.UUID;

public interface ConfirmarPagoPedidoPort {
    PagoConfirmado confirmar(UUID pedidoId, UUID compradorId, UUID vendedorId, String modalidad);

    record PagoConfirmado(
            UUID id,
            String estado,
            String medio,
            long monto,
            Instant confirmadoAt
    ) {
    }
}
