package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.domicilios.application.ports.in.ConsultarDomicilioDePedidoPort;
import co.edu.napa.domicilios.application.ports.in.ConsultarDomicilioDePedidoPort.DomicilioDePedido;
import co.edu.napa.pagos.application.ports.in.ConsultarEstadoPagoPedidoPort;
import co.edu.napa.pedidos.application.ports.in.ConsultarPedidoClientePort;
import co.edu.napa.pedidos.application.ports.in.ListarPedidosClientePort;
import co.edu.napa.pedidos.application.ports.in.PedidoVendedorDetalle;
import co.edu.napa.pedidos.application.ports.out.PedidoDetalleProjection;
import co.edu.napa.pedidos.application.ports.out.PedidoRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class ConsultarPedidoClienteUseCase implements ConsultarPedidoClientePort {
    private final PedidoRepositoryPort pedidos;
    private final ConsultarEstadoPagoPedidoPort pagos;
    private final ConsultarDomicilioDePedidoPort domicilios;

    public ConsultarPedidoClienteUseCase(
            PedidoRepositoryPort pedidos,
            ConsultarEstadoPagoPedidoPort pagos,
            ConsultarDomicilioDePedidoPort domicilios
    ) {
        this.pedidos = Objects.requireNonNull(pedidos, "pedidos");
        this.pagos = Objects.requireNonNull(pagos, "pagos");
        this.domicilios = Objects.requireNonNull(domicilios, "domicilios");
    }

    @Override
    @Transactional(readOnly = true)
    public PedidoClienteDetalle consultar(UUID clienteId, UUID pedidoId) {
        if (clienteId == null || pedidoId == null) {
            throw new DomainException("VALIDATION_ERROR", "Indica el cliente y el pedido.");
        }
        PedidoDetalleProjection pedido = pedidos.consultarDetalleCliente(clienteId, pedidoId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "El pedido no está disponible."));
        String paymentState = pagos.consultar(pedidoId).orElse(null);
        DomicilioDePedido delivery = domicilios.porPedido(pedidoId).orElse(null);
        List<PedidoVendedorDetalle.Item> items = pedido.items().stream()
                .map(item -> new PedidoVendedorDetalle.Item(
                        item.productoId(),
                        item.nombre(),
                        item.unidad(),
                        item.cantidad(),
                        item.precioUnitario(),
                        item.cantidad().multiply(BigDecimal.valueOf(item.precioUnitario()))
                                .setScale(0, RoundingMode.HALF_UP).longValueExact(),
                        item.productoEliminado()))
                .toList();
        long subtotal = items.stream().mapToLong(PedidoVendedorDetalle.Item::subtotal).reduce(0, Math::addExact);
        Instant updatedAt = delivery != null && delivery.updatedAt().isAfter(pedido.updatedAt())
                ? delivery.updatedAt()
                : pedido.updatedAt();
        // La consulta oculta la ubicación de entrega cuando el pedido ya terminó.
        PedidoVendedorDetalle.UbicacionEntrega location = pedido.ubicacionEntrega() == null ? null
                : new PedidoVendedorDetalle.UbicacionEntrega(
                        pedido.ubicacionEntrega().latitud(),
                        pedido.ubicacionEntrega().longitud(),
                        pedido.ubicacionEntrega().direccion(),
                        pedido.ubicacionEntrega().referencia(),
                        pedido.ubicacionEntrega().barrio());
        return new PedidoClienteDetalle(
                pedido.id(),
                pedido.estado(),
                pedido.modalidad(),
                pedido.reserva(),
                pedido.createdAt(),
                updatedAt,
                new ListarPedidosClientePort.Emprendimiento(pedido.emprendimientoId(), pedido.emprendimientoNombre()),
                items,
                subtotal,
                pedido.tarifaDomicilio(),
                pedido.total(),
                pedido.medioPago(),
                paymentState == null ? null : new PedidoVendedorDetalle.Pago(paymentState),
                location,
                delivery == null ? null
                        : new PedidoVendedorDetalle.Domicilio(delivery.id(), delivery.estado(),
                                delivery.motivoCancelacion())
        );
    }
}
