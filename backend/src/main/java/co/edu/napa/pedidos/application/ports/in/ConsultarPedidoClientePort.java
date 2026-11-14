package co.edu.napa.pedidos.application.ports.in;

import co.edu.napa.pedidos.domain.model.EstadoPedido;
import co.edu.napa.pedidos.domain.model.MedioPagoPedido;
import co.edu.napa.pedidos.domain.model.ModalidadEntrega;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** RF-48: el cliente consulta el estado de uno de sus pedidos. */
public interface ConsultarPedidoClientePort {
    PedidoClienteDetalle consultar(UUID clienteId, UUID pedidoId);

    /**
     * {@code updatedAt} es la última actualización del pedido o de su domicilio. {@code pago} es nulo mientras
     * no exista; {@code domicilio}, mientras no se publique (RF-14).
     */
    record PedidoClienteDetalle(
            UUID id,
            EstadoPedido estado,
            ModalidadEntrega modalidad,
            Instant reserva,
            Instant createdAt,
            Instant updatedAt,
            ListarPedidosClientePort.Emprendimiento emprendimiento,
            List<PedidoVendedorDetalle.Item> items,
            long subtotal,
            Long tarifaDomicilio,
            long total,
            MedioPagoPedido medioPago,
            PedidoVendedorDetalle.Pago pago,
            PedidoVendedorDetalle.UbicacionEntrega ubicacionEntrega,
            PedidoVendedorDetalle.Domicilio domicilio
    ) {
        public PedidoClienteDetalle {
            items = List.copyOf(items);
        }
    }
}
