package co.edu.napa.pedidos.application.ports.in;

import co.edu.napa.pedidos.domain.model.ModalidadEntrega;

import java.time.Instant;
import java.util.UUID;

public interface ResponderPedidoPort {
    PedidoAceptado aceptar(UUID pedidoId, UUID vendedorId);
    PedidoRechazado rechazar(UUID pedidoId, UUID vendedorId);

    record PagoPendiente(UUID id, String estado, String medio, long monto) {}

    record PedidoAceptado(
            UUID id,
            String estado,
            ModalidadEntrega modalidad,
            Instant reserva,
            long total,
            PagoPendiente pago
    ) {}

    record PedidoRechazado(UUID id, String estado) {}
}
