package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.emprendimientos.application.ports.in.ConsultarEmprendimientoParaPedidoPort;
import co.edu.napa.pagos.application.ports.in.ConfirmarPagoPedidoPort;
import co.edu.napa.pedidos.application.ports.in.EntregaPedidoPort;
import co.edu.napa.pedidos.application.ports.out.PedidoRepositoryPort;
import co.edu.napa.pedidos.domain.event.EntregaDirectaIniciadaEvent;
import co.edu.napa.pedidos.domain.event.PedidoEntregadoEvent;
import co.edu.napa.pedidos.domain.event.PedidoListoParaRecogerEvent;
import co.edu.napa.pedidos.domain.model.Pedido;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;

@Component
public class EntregaPedidoUseCase implements EntregaPedidoPort {
    static final String COBRO_REQUERIDO = "Confirma que recibiste el pago.";

    private final PedidoRepositoryPort pedidos;
    private final ConsultarEmprendimientoParaPedidoPort emprendimientos;
    private final ConfirmarPagoPedidoPort pagos;
    private final EventPublisherPort eventos;
    private final Clock clock;

    public EntregaPedidoUseCase(
            PedidoRepositoryPort pedidos,
            ConsultarEmprendimientoParaPedidoPort emprendimientos,
            ConfirmarPagoPedidoPort pagos,
            EventPublisherPort eventos,
            Clock clock
    ) {
        this.pedidos = Objects.requireNonNull(pedidos, "pedidos");
        this.emprendimientos = Objects.requireNonNull(emprendimientos, "emprendimientos");
        this.pagos = Objects.requireNonNull(pagos, "pagos");
        this.eventos = Objects.requireNonNull(eventos, "eventos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public PedidoTransicionado iniciarEntregaDirecta(UUID pedidoId, UUID vendedorId) {
        Pedido enCamino = bloquearPropio(pedidoId, vendedorId).iniciarEntregaDirecta(Instant.now(clock));
        pedidos.actualizar(enCamino);
        eventos.publish(EntregaDirectaIniciadaEvent.de(enCamino, vendedorId));
        return transicionado(enCamino);
    }

    @Override
    @Transactional
    public PedidoTransicionado marcarListoParaRecoger(UUID pedidoId, UUID vendedorId) {
        Pedido listo = bloquearPropio(pedidoId, vendedorId).marcarListoParaRecoger(Instant.now(clock));
        pedidos.actualizar(listo);
        eventos.publish(PedidoListoParaRecogerEvent.de(listo, vendedorId));
        return transicionado(listo);
    }

    @Override
    @Transactional
    public PedidoEntregado confirmarEntregaDirecta(UUID pedidoId, UUID vendedorId, boolean cobroConfirmado) {
        return entregar(pedidoId, vendedorId, cobroConfirmado, Pedido::confirmarEntregaDirecta);
    }

    @Override
    @Transactional
    public PedidoEntregado confirmarRetiro(UUID pedidoId, UUID vendedorId, boolean cobroConfirmado) {
        return entregar(pedidoId, vendedorId, cobroConfirmado, Pedido::confirmarRetiro);
    }

    /** El pedido pasa a entregado y su pago a confirmado en la misma transacción (RF-37 y RF-65, FR-002). */
    private PedidoEntregado entregar(
            UUID pedidoId,
            UUID vendedorId,
            boolean cobroConfirmado,
            BiFunction<Pedido, Instant, Pedido> transicion
    ) {
        if (!cobroConfirmado) {
            throw new DomainException("VALIDATION_ERROR", COBRO_REQUERIDO,
                    Map.of("fields", Map.of("cobroConfirmado", COBRO_REQUERIDO)));
        }
        Pedido pedido = bloquearPropio(pedidoId, vendedorId);
        Pedido entregado = transicion.apply(pedido, Instant.now(clock));
        var pago = pagos.confirmar(pedido.id(), pedido.compradorId(), vendedorId, pedido.modalidad().name());
        pedidos.actualizar(entregado);
        eventos.publish(PedidoEntregadoEvent.porVendedor(entregado, vendedorId));
        return new PedidoEntregado(entregado.id(), entregado.estado().name(), entregado.modalidad(),
                entregado.updatedAt(),
                new Pago(pago.id(), pago.estado(), pago.medio(), pago.monto(), pago.confirmadoAt()));
    }

    private Pedido bloquearPropio(UUID pedidoId, UUID vendedorId) {
        if (pedidoId == null || vendedorId == null) {
            throw new DomainException("VALIDATION_ERROR", "Indica el pedido y el vendedor.");
        }
        Pedido pedido = pedidos.bloquearPorId(pedidoId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "El pedido no está disponible."));
        boolean propio = emprendimientos.consultar(pedido.emprendimientoId())
                .map(emprendimiento -> emprendimiento.vendedorId().equals(vendedorId))
                .orElse(false);
        if (!propio) {
            throw new DomainException("NOT_FOUND", "El pedido no está disponible.");
        }
        return pedido;
    }

    private static PedidoTransicionado transicionado(Pedido pedido) {
        return new PedidoTransicionado(pedido.id(), pedido.estado().name(), pedido.modalidad(), pedido.reserva(),
                pedido.updatedAt());
    }
}
