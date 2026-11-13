package co.edu.napa.pedidos.application.ports.out;

import co.edu.napa.pedidos.domain.model.EstadoPedido;
import co.edu.napa.pedidos.domain.model.ModalidadEntrega;

import java.time.Instant;

public record FiltroPedidosVendedor(
        EstadoPedido estado,
        ModalidadEntrega modalidad,
        Boolean reserva,
        Instant desde,
        Instant hasta,
        boolean ascendente
) {
}
