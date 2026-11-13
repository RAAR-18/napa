package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.emprendimientos.application.ports.in.ConsultarEmprendimientoParaPedidoPort;
import co.edu.napa.productos.application.ports.in.BloquearProductosParaPedidoPort;
import co.edu.napa.pedidos.application.ports.in.CotizarPedidoPort;
import co.edu.napa.pedidos.application.ports.in.RealizarPedidoPort;
import co.edu.napa.pedidos.application.ports.out.PedidoRepositoryPort;
import co.edu.napa.pedidos.domain.event.PedidoCreadoEvent;
import co.edu.napa.pedidos.domain.model.EstadoPedido;
import co.edu.napa.pedidos.domain.model.ItemPedido;
import co.edu.napa.pedidos.domain.model.MedioPagoPedido;
import co.edu.napa.pedidos.domain.model.Pedido;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.application.ports.out.UbicacionRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.Ubicacion;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class RealizarPedidoUseCase implements RealizarPedidoPort {
    private final CotizarPedidoPort cotizaciones;
    private final ConsultarEmprendimientoParaPedidoPort emprendimientos;
    private final PedidoRepositoryPort pedidos;
    private final BloquearProductosParaPedidoPort bloqueoProductos;
    private final UbicacionRepositoryPort ubicaciones;
    private final EventPublisherPort eventos;
    private final Clock clock;

    public RealizarPedidoUseCase(
            CotizarPedidoPort cotizaciones,
            ConsultarEmprendimientoParaPedidoPort emprendimientos,
            PedidoRepositoryPort pedidos,
            BloquearProductosParaPedidoPort bloqueoProductos,
            UbicacionRepositoryPort ubicaciones,
            EventPublisherPort eventos,
            Clock clock
    ) {
        this.cotizaciones = Objects.requireNonNull(cotizaciones, "cotizaciones");
        this.emprendimientos = Objects.requireNonNull(emprendimientos, "emprendimientos");
        this.pedidos = Objects.requireNonNull(pedidos, "pedidos");
        this.bloqueoProductos = Objects.requireNonNull(bloqueoProductos, "bloqueoProductos");
        this.ubicaciones = Objects.requireNonNull(ubicaciones, "ubicaciones");
        this.eventos = Objects.requireNonNull(eventos, "eventos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public PedidoCreado realizar(UUID compradorId, SolicitudPedido solicitud) {
        if (compradorId == null || solicitud == null || solicitud.cotizacion() == null) {
            throw invalid("pedido", "Completa los datos obligatorios del pedido.");
        }
        var pedidoSolicitado = solicitud.cotizacion();
        if (pedidoSolicitado.emprendimientoId() == null || pedidoSolicitado.items() == null
                || pedidoSolicitado.items().isEmpty()
                || pedidoSolicitado.items().stream().anyMatch(item -> item == null || item.productoId() == null)) {
            throw invalid("items", "Indica un emprendimiento y los productos que quieres pedir.");
        }
        var emprendimiento = emprendimientos.consultar(pedidoSolicitado.emprendimientoId())
                .orElseThrow(() -> new DomainException("NOT_FOUND", "El emprendimiento no está disponible."));
        bloqueoProductos.bloquear(pedidoSolicitado.emprendimientoId(),
                pedidoSolicitado.items().stream().map(item -> item.productoId()).toList());
        var cotizacion = cotizaciones.cotizar(pedidoSolicitado);
        MedioPagoPedido medioPago = medioPago(solicitud.medioPago());
        if (!cotizacion.mediosPagoDisponibles().contains(medioPago.name())) {
            throw new DomainException("BUSINESS_RULE", "El vendedor no tiene habilitado ese medio de pago.",
                    Map.of("motivo", "MEDIO_PAGO_NO_DISPONIBLE"));
        }
        if (solicitud.totalEsperado() != null && solicitud.totalEsperado() != cotizacion.total()) {
            throw new DomainException("CONFLICT", "El total cambió desde que revisaste la cotización.",
                    Map.of("totalActual", cotizacion.total()));
        }
        if (pedidoSolicitado.reserva() != null && !pedidoSolicitado.reserva().isAfter(clock.instant())) {
            throw invalid("reserva", "La fecha de reserva debe ser futura.");
        }

        Instant now = clock.instant();
        var ubicacionSolicitada = pedidoSolicitado.ubicacionEntrega();
        Ubicacion ubicacion = ubicacionSolicitada == null ? null : ubicaciones.save(new Ubicacion(
                UUID.randomUUID(),
                ubicacionSolicitada.latitud(),
                ubicacionSolicitada.longitud(),
                ubicacionSolicitada.direccion(),
                ubicacionSolicitada.referencia(),
                now,
                now
        ));
        var items = cotizacion.items().stream()
                .map(item -> new ItemPedido(UUID.randomUUID(), item.productoId(), item.cantidad(),
                        item.precioUnitario()))
                .toList();
        var pedido = Pedido.nuevo(
                compradorId,
                pedidoSolicitado.emprendimientoId(),
                pedidoSolicitado.modalidad(),
                pedidoSolicitado.reserva(),
                medioPago,
                cotizacion.tarifaDomicilio(),
                cotizacion.total(),
                ubicacion == null ? null : ubicacion.id(),
                items,
                now
        );
        pedidos.guardar(pedido);
        eventos.publish(PedidoCreadoEvent.de(pedido, emprendimiento.vendedorId(), pedido.createdAt()));

        return new PedidoCreado(pedido.id(), EstadoPedido.PENDIENTE.name(), pedido.modalidad(),
                pedido.reserva(), pedido.medioPago().name(), cotizacion.items(), cotizacion.subtotal(),
                cotizacion.tarifaDomicilio(), cotizacion.total(), ubicacionSolicitada,
                cotizacion.pagoDomicilio(), pedido.createdAt());
    }

    private MedioPagoPedido medioPago(String value) {
        try {
            return MedioPagoPedido.valueOf(Objects.requireNonNull(value, "medioPago"));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new DomainException("BUSINESS_RULE", "Selecciona un medio de pago habilitado.",
                    Map.of("motivo", "MEDIO_PAGO_NO_DISPONIBLE"));
        }
    }

    private DomainException invalid(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of(field, message)));
    }
}
