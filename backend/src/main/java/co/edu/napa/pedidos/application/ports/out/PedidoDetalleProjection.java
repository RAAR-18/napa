package co.edu.napa.pedidos.application.ports.out;

import co.edu.napa.pedidos.domain.model.EstadoPedido;
import co.edu.napa.pedidos.domain.model.MedioPagoPedido;
import co.edu.napa.pedidos.domain.model.ModalidadEntrega;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PedidoDetalleProjection(
        UUID id,
        UUID clienteId,
        EstadoPedido estado,
        ModalidadEntrega modalidad,
        Instant reserva,
        Instant createdAt,
        Instant updatedAt,
        MedioPagoPedido medioPago,
        Long tarifaDomicilio,
        long total,
        Ubicacion ubicacionEntrega,
        List<Item> items,
        UUID emprendimientoId,
        String emprendimientoNombre
) {
    public PedidoDetalleProjection {
        items = List.copyOf(items);
    }

    public record Ubicacion(double latitud, double longitud, String direccion, String referencia, String barrio) {
    }

    public record Item(
            UUID productoId,
            String nombre,
            String unidad,
            BigDecimal cantidad,
            long precioUnitario,
            boolean productoEliminado
    ) {
    }
}
