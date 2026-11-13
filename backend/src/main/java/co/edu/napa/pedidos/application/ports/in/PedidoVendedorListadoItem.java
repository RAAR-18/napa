package co.edu.napa.pedidos.application.ports.in;

import co.edu.napa.pedidos.domain.model.EstadoPedido;
import co.edu.napa.pedidos.domain.model.ModalidadEntrega;

import java.time.Instant;
import java.util.UUID;

public record PedidoVendedorListadoItem(
        UUID id,
        Cliente cliente,
        Instant createdAt,
        EstadoPedido estado,
        ModalidadEntrega modalidad,
        Instant reserva,
        long total
) {
    public record Cliente(String nombre) {
    }
}
