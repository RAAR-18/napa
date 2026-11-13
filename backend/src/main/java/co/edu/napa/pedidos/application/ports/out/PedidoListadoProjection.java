package co.edu.napa.pedidos.application.ports.out;

import co.edu.napa.pedidos.domain.model.EstadoPedido;
import co.edu.napa.pedidos.domain.model.ModalidadEntrega;

import java.time.Instant;
import java.util.UUID;

public record PedidoListadoProjection(
        UUID id,
        UUID clienteId,
        Instant createdAt,
        EstadoPedido estado,
        ModalidadEntrega modalidad,
        Instant reserva,
        long total
) {
}
