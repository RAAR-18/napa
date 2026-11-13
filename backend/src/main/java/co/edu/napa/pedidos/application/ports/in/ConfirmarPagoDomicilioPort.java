package co.edu.napa.pedidos.application.ports.in;

import java.time.Instant;
import java.util.UUID;

public interface ConfirmarPagoDomicilioPort {
    PagoDomicilioConfirmado confirmar(UUID pedidoId, UUID vendedorId);

    record PagoDomicilioConfirmado(UUID pedidoId, Pago pago) {
    }

    record Pago(UUID id, String estado, String medio, long monto, Instant confirmadoAt) {
    }
}
