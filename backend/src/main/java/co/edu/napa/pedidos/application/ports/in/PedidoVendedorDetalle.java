package co.edu.napa.pedidos.application.ports.in;

import co.edu.napa.pedidos.domain.model.EstadoPedido;
import co.edu.napa.pedidos.domain.model.MedioPagoPedido;
import co.edu.napa.pedidos.domain.model.ModalidadEntrega;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PedidoVendedorDetalle(
        UUID id,
        EstadoPedido estado,
        ModalidadEntrega modalidad,
        Instant reserva,
        Instant createdAt,
        Instant updatedAt,
        Cliente cliente,
        List<Item> items,
        long subtotal,
        Long tarifaDomicilio,
        long total,
        MedioPagoPedido medioPago,
        Pago pago,
        UbicacionEntrega ubicacionEntrega,
        Domicilio domicilio
) {
    public PedidoVendedorDetalle {
        items = List.copyOf(items);
    }

    public record Cliente(String nombre) {
    }

    public record Item(
            UUID productoId,
            String nombre,
            String unidad,
            BigDecimal cantidad,
            long precioUnitario,
            long subtotal,
            boolean productoEliminado
    ) {
    }

    public record Pago(String estado) {
    }

    /** Domicilio publicado para el pedido (RF-14), o nulo si aún no se publica. */
    public record Domicilio(UUID id, String estado, String motivoCancelacion) {
    }

    public record UbicacionEntrega(
            double latitud,
            double longitud,
            String direccion,
            String referencia,
            String barrio
    ) {
    }
}
