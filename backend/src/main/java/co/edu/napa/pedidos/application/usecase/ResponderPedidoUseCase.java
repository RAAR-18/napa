package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.emprendimientos.application.ports.in.ConsultarEmprendimientoParaPedidoPort;
import co.edu.napa.pagos.application.ports.in.ConsultarMediosPagoPort;
import co.edu.napa.pagos.application.ports.in.RegistrarPagoPedidoPendientePort;
import co.edu.napa.pedidos.application.ports.in.ResponderPedidoPort;
import co.edu.napa.pedidos.application.ports.in.ResponderPedidoPort.PedidoAceptado;
import co.edu.napa.pedidos.application.ports.in.ResponderPedidoPort.PagoPendiente;
import co.edu.napa.pedidos.application.ports.in.ResponderPedidoPort.PedidoRechazado;
import co.edu.napa.pedidos.application.ports.out.PedidoRepositoryPort;
import co.edu.napa.pedidos.domain.event.PedidoAceptadoEvent;
import co.edu.napa.pedidos.domain.event.PedidoRechazadoEvent;
import co.edu.napa.pedidos.domain.model.ModalidadEntrega;
import co.edu.napa.pedidos.domain.model.Pedido;
import co.edu.napa.productos.application.ports.in.DescontarInventarioPort;
import co.edu.napa.productos.application.ports.in.DescontarInventarioPort.LineaPedido;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Component
public class ResponderPedidoUseCase implements ResponderPedidoPort {
    private final PedidoRepositoryPort pedidos;
    private final ConsultarEmprendimientoParaPedidoPort emprendimientos;
    private final DescontarInventarioPort inventario;
    private final ConsultarMediosPagoPort mediosPago;
    private final RegistrarPagoPedidoPendientePort registrarPago;
    private final EventPublisherPort eventos;
    private final Clock clock;

    public ResponderPedidoUseCase(
            PedidoRepositoryPort pedidos,
            ConsultarEmprendimientoParaPedidoPort emprendimientos,
            DescontarInventarioPort inventario,
            ConsultarMediosPagoPort mediosPago,
            RegistrarPagoPedidoPendientePort registrarPago,
            EventPublisherPort eventos,
            Clock clock
    ) {
        this.pedidos = Objects.requireNonNull(pedidos, "pedidos");
        this.emprendimientos = Objects.requireNonNull(emprendimientos, "emprendimientos");
        this.inventario = Objects.requireNonNull(inventario, "inventario");
        this.mediosPago = Objects.requireNonNull(mediosPago, "mediosPago");
        this.registrarPago = Objects.requireNonNull(registrarPago, "registrarPago");
        this.eventos = Objects.requireNonNull(eventos, "eventos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public PedidoAceptado aceptar(UUID pedidoId, UUID vendedorId) {
        Pedido pedido = bloquearPedido(pedidoId, vendedorId);
        Instant now = Instant.now(clock);
        Pedido aceptado = pedido.aceptar(now);

        if (pedido.modalidad() == ModalidadEntrega.DOMICILIO) {
            var opciones = mediosPago.consultar(vendedorId);
            if (!opciones.domicilioDisponible()) {
                throw new DomainException("BANK_ACCOUNT_REQUIRED", "Registra una cuenta bancaria primero.");
            }
        }
        if (pedido.reserva() == null) {
            inventario.descontar(pedido.items().stream()
                    .map(item -> new LineaPedido(item.productoId(), item.cantidad())).toList());
        }

        var pago = registrarPago.registrar(pedido.id(), pedido.compradorId(), vendedorId,
                pedido.total(), pedido.medioPago().name());
        pedidos.actualizar(aceptado);
        eventos.publish(PedidoAceptadoEvent.de(aceptado, vendedorId, now));
        return new PedidoAceptado(aceptado.id(), aceptado.estado().name(), aceptado.modalidad(),
                aceptado.reserva(), aceptado.total(),
                new PagoPendiente(pago.id(), pago.estado(), pago.medio(), pago.monto()));
    }

    @Override
    @Transactional
    public PedidoRechazado rechazar(UUID pedidoId, UUID vendedorId) {
        Pedido pedido = bloquearPedido(pedidoId, vendedorId);
        Instant now = Instant.now(clock);
        Pedido rechazado = pedido.rechazar(now);
        pedidos.actualizar(rechazado);
        eventos.publish(PedidoRechazadoEvent.de(rechazado, vendedorId, now));
        return new PedidoRechazado(rechazado.id(), rechazado.estado().name());
    }

    private Pedido bloquearPedido(UUID pedidoId, UUID vendedorId) {
        if (pedidoId == null || vendedorId == null) {
            throw new DomainException("VALIDATION_ERROR", "Indica el pedido y el vendedor.");
        }
        Pedido pedido = pedidos.bloquearPorId(pedidoId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "El pedido no está disponible."));
        var emprendimiento = emprendimientos.consultar(pedido.emprendimientoId())
                .orElseThrow(() -> new DomainException("NOT_FOUND", "El pedido no está disponible."));
        if (!emprendimiento.vendedorId().equals(vendedorId)) {
            throw new DomainException("NOT_FOUND", "El pedido no está disponible.");
        }
        return pedido;
    }
}
