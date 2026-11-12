package co.edu.napa.pedidos.application.ports.in;

import co.edu.napa.pedidos.application.ports.in.CotizarPedidoPort.Item;
import co.edu.napa.pedidos.application.ports.in.CotizarPedidoPort.PagoDomicilio;
import co.edu.napa.pedidos.application.ports.in.CotizarPedidoPort.Solicitud;
import co.edu.napa.pedidos.application.ports.in.CotizarPedidoPort.UbicacionEntrega;
import co.edu.napa.pedidos.domain.model.ModalidadEntrega;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RealizarPedidoPort {
    PedidoCreado realizar(UUID compradorId, SolicitudPedido solicitud);

    record SolicitudPedido(Solicitud cotizacion, String medioPago, Long totalEsperado) {}

    record PedidoCreado(
            UUID id,
            String estado,
            ModalidadEntrega modalidad,
            Instant reserva,
            String medioPago,
            List<Item> items,
            long subtotal,
            Long tarifaDomicilio,
            long total,
            UbicacionEntrega ubicacionEntrega,
            PagoDomicilio pagoDomicilio,
            Instant createdAt
    ) {
        public PedidoCreado {
            items = List.copyOf(items);
        }
    }
}
