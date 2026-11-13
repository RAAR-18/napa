package co.edu.napa.pagos.application.usecase;

import co.edu.napa.pagos.application.ports.in.ConfirmarPagoPedidoPort;
import co.edu.napa.pagos.application.ports.in.ConfirmarPagoPedidoPort.PagoConfirmado;
import co.edu.napa.pagos.application.ports.out.PagoRepositoryPort;
import co.edu.napa.pagos.domain.event.PagoPedidoConfirmadoEvent;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class ConfirmarPagoPedidoUseCase implements ConfirmarPagoPedidoPort {
    private final PagoRepositoryPort pagos;
    private final EventPublisherPort eventos;
    private final Clock clock;

    public ConfirmarPagoPedidoUseCase(PagoRepositoryPort pagos, EventPublisherPort eventos, Clock clock) {
        this.pagos = Objects.requireNonNull(pagos, "pagos");
        this.eventos = Objects.requireNonNull(eventos, "eventos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public PagoConfirmado confirmar(UUID pedidoId, UUID compradorId, UUID vendedorId, String modalidad) {
        Objects.requireNonNull(pedidoId, "pedidoId");
        Objects.requireNonNull(compradorId, "compradorId");
        Objects.requireNonNull(vendedorId, "vendedorId");
        Objects.requireNonNull(modalidad, "modalidad");

        var pago = pagos.bloquearPorPedido(pedidoId)
                .orElseThrow(() -> new IllegalStateException("El pedido aceptado no tiene un pago registrado."));
        if (!pago.compradorId().equals(compradorId) || !pago.vendedorId().equals(vendedorId)) {
            throw new IllegalStateException("El pago no corresponde al comprador y vendedor del pedido.");
        }

        Instant now = Instant.now(clock);
        var confirmado = pago.confirmar(now);
        pagos.actualizar(confirmado);
        eventos.publish(PagoPedidoConfirmadoEvent.de(
                confirmado.id(), pedidoId, compradorId, vendedorId, modalidad,
                confirmado.medio().name(), confirmado.monto(), now));
        return new PagoConfirmado(confirmado.id(), confirmado.estado().name(),
                confirmado.medio().name(), confirmado.monto(), confirmado.confirmadoAt());
    }
}
