package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarNombresUsuarioPort;
import co.edu.napa.pagos.application.ports.in.ConsultarEstadoPagoPedidoPort;
import co.edu.napa.pedidos.application.ports.in.ConsultarPedidoVendedorPort;
import co.edu.napa.pedidos.application.ports.in.PedidoVendedorDetalle;
import co.edu.napa.pedidos.application.ports.out.PedidoDetalleProjection;
import co.edu.napa.pedidos.application.ports.out.PedidoRepositoryPort;
import co.edu.napa.pedidos.domain.model.EstadoPedido;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class ConsultarPedidoVendedorUseCase implements ConsultarPedidoVendedorPort {
    private final PedidoRepositoryPort pedidos;
    private final ConsultarNombresUsuarioPort usuarios;
    private final ConsultarEstadoPagoPedidoPort pagos;

    public ConsultarPedidoVendedorUseCase(
            PedidoRepositoryPort pedidos,
            ConsultarNombresUsuarioPort usuarios,
            ConsultarEstadoPagoPedidoPort pagos
    ) {
        this.pedidos = Objects.requireNonNull(pedidos, "pedidos");
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.pagos = Objects.requireNonNull(pagos, "pagos");
    }

    @Override
    public PedidoVendedorDetalle consultar(UUID vendedorId, UUID pedidoId) {
        if (vendedorId == null || pedidoId == null) {
            throw new DomainException("VALIDATION_ERROR", "Indica el vendedor y el pedido.");
        }
        PedidoDetalleProjection pedido = pedidos.consultarDetalleVendedor(vendedorId, pedidoId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "El pedido no está disponible."));
        String customerName = usuarios.consultar(Set.of(pedido.clienteId())).get(pedido.clienteId());
        if (customerName == null) {
            throw new DomainException("NOT_FOUND", "La información del cliente no está disponible.");
        }
        String paymentState = pagos.consultar(pedidoId).orElse(null);
        List<PedidoVendedorDetalle.Item> items = pedido.items().stream()
                .map(item -> new PedidoVendedorDetalle.Item(
                        item.productoId(),
                        item.nombre(),
                        item.unidad(),
                        item.cantidad(),
                        item.precioUnitario(),
                        item.cantidad().multiply(java.math.BigDecimal.valueOf(item.precioUnitario()))
                                .setScale(0, RoundingMode.HALF_UP).longValueExact(),
                        item.productoEliminado()))
                .toList();
        long subtotal = items.stream().mapToLong(PedidoVendedorDetalle.Item::subtotal).reduce(0, Math::addExact);
        boolean hideLocation = List.of(
                EstadoPedido.RECHAZADO, EstadoPedido.ENTREGADO, EstadoPedido.CANCELADO
        ).contains(pedido.estado());
        PedidoVendedorDetalle.UbicacionEntrega location = pedido.ubicacionEntrega() == null || hideLocation
                ? null
                : new PedidoVendedorDetalle.UbicacionEntrega(
                        pedido.ubicacionEntrega().latitud(),
                        pedido.ubicacionEntrega().longitud(),
                        pedido.ubicacionEntrega().direccion(),
                        pedido.ubicacionEntrega().referencia());
        return new PedidoVendedorDetalle(
                pedido.id(),
                pedido.estado(),
                pedido.modalidad(),
                pedido.reserva(),
                pedido.createdAt(),
                pedido.updatedAt(),
                new PedidoVendedorDetalle.Cliente(customerName),
                items,
                subtotal,
                pedido.tarifaDomicilio(),
                pedido.total(),
                pedido.medioPago(),
                paymentState == null ? null : new PedidoVendedorDetalle.Pago(paymentState),
                location
        );
    }
}
