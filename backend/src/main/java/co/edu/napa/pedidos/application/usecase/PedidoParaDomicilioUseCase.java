package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.pedidos.application.ports.in.PedidoParaDomicilioPort;
import co.edu.napa.pedidos.application.ports.out.PedidoRepositoryPort;
import co.edu.napa.pedidos.domain.event.PedidoEntregadoEvent;
import co.edu.napa.pedidos.domain.model.Pedido;
import co.edu.napa.productos.application.ports.in.DescontarInventarioPort.LineaPedido;
import co.edu.napa.productos.application.ports.in.DevolverInventarioPort;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class PedidoParaDomicilioUseCase implements PedidoParaDomicilioPort {
    private final PedidoRepositoryPort pedidos;
    private final DevolverInventarioPort inventario;
    private final EventPublisherPort eventos;
    private final Clock clock;

    public PedidoParaDomicilioUseCase(
            PedidoRepositoryPort pedidos,
            DevolverInventarioPort inventario,
            EventPublisherPort eventos,
            Clock clock
    ) {
        this.pedidos = Objects.requireNonNull(pedidos, "pedidos");
        this.inventario = Objects.requireNonNull(inventario, "inventario");
        this.eventos = Objects.requireNonNull(eventos, "eventos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<PedidoParaDomicilio> bloquear(UUID pedidoId) {
        Objects.requireNonNull(pedidoId, "pedidoId");
        return pedidos.bloquearPorId(pedidoId).map(pedido -> new PedidoParaDomicilio(
                pedido.id(), pedido.compradorId(), pedido.emprendimientoId(), pedido.modalidad().name(),
                pedido.estado().name(), pedido.tarifaDomicilio(), pedido.ubicacionEntregaId(), pedido.reserva()));
    }

    @Override
    public int cantidadItems(UUID pedidoId) {
        Objects.requireNonNull(pedidoId, "pedidoId");
        return pedidos.contarItems(pedidoId);
    }

    @Override
    public Optional<ResumenPedido> resumen(UUID pedidoId, UUID compradorId) {
        Objects.requireNonNull(pedidoId, "pedidoId");
        Objects.requireNonNull(compradorId, "compradorId");
        return pedidos.consultarDetalleCliente(compradorId, pedidoId).map(pedido -> new ResumenPedido(
                pedido.id(), pedido.modalidad().name(), pedido.estado().name(), pedido.total(),
                pedido.items().size()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelarPorDomicilio(UUID pedidoId) {
        Objects.requireNonNull(pedidoId, "pedidoId");
        Pedido pedido = requirePedido(pedidoId);
        pedidos.actualizar(pedido.cancelarPorDomicilio(Instant.now(clock)));
        // Al aceptar solo se descuenta el inventario de los pedidos inmediatos (las reservas no lo descuentan).
        if (pedido.reserva() == null) {
            inventario.devolver(pedido.items().stream()
                    .map(item -> new LineaPedido(item.productoId(), item.cantidad())).toList());
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void ponerEnCaminoPorDomicilio(UUID pedidoId) {
        pedidos.actualizar(requirePedido(pedidoId).ponerEnCaminoPorDomicilio(Instant.now(clock)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void entregarPorDomicilio(UUID pedidoId, UUID domicilioId, UUID domiciliarioId) {
        Objects.requireNonNull(domicilioId, "domicilioId");
        Objects.requireNonNull(domiciliarioId, "domiciliarioId");
        Pedido entregado = requirePedido(pedidoId).entregarPorDomicilio(Instant.now(clock));
        pedidos.actualizar(entregado);
        eventos.publish(PedidoEntregadoEvent.porDomicilio(entregado, domicilioId, domiciliarioId));
    }

    private Pedido requirePedido(UUID pedidoId) {
        Objects.requireNonNull(pedidoId, "pedidoId");
        return pedidos.bloquearPorId(pedidoId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "El pedido no está disponible."));
    }
}
