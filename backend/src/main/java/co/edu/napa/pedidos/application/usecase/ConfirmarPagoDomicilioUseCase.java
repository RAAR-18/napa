package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.emprendimientos.application.ports.in.ConsultarEmprendimientoParaPedidoPort;
import co.edu.napa.pagos.application.ports.in.ConfirmarPagoPedidoPort;
import co.edu.napa.pedidos.application.ports.in.ConfirmarPagoDomicilioPort;
import co.edu.napa.pedidos.application.ports.in.ConfirmarPagoDomicilioPort.PagoDomicilioConfirmado;
import co.edu.napa.pedidos.application.ports.in.ConfirmarPagoDomicilioPort.Pago;
import co.edu.napa.pedidos.application.ports.out.PedidoRepositoryPort;
import co.edu.napa.pedidos.domain.model.EstadoPedido;
import co.edu.napa.pedidos.domain.model.ModalidadEntrega;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class ConfirmarPagoDomicilioUseCase implements ConfirmarPagoDomicilioPort {
    private final PedidoRepositoryPort pedidos;
    private final ConsultarEmprendimientoParaPedidoPort emprendimientos;
    private final ConfirmarPagoPedidoPort pagos;

    public ConfirmarPagoDomicilioUseCase(
            PedidoRepositoryPort pedidos,
            ConsultarEmprendimientoParaPedidoPort emprendimientos,
            ConfirmarPagoPedidoPort pagos
    ) {
        this.pedidos = Objects.requireNonNull(pedidos, "pedidos");
        this.emprendimientos = Objects.requireNonNull(emprendimientos, "emprendimientos");
        this.pagos = Objects.requireNonNull(pagos, "pagos");
    }

    @Override
    @Transactional
    public PagoDomicilioConfirmado confirmar(UUID pedidoId, UUID vendedorId) {
        if (pedidoId == null || vendedorId == null) {
            throw new DomainException("VALIDATION_ERROR", "Indica el pedido y el vendedor.");
        }
        var pedido = pedidos.bloquearPorId(pedidoId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "El pedido no está disponible."));
        var emprendimiento = emprendimientos.consultar(pedido.emprendimientoId())
                .orElseThrow(() -> new DomainException("NOT_FOUND", "El pedido no está disponible."));
        if (!emprendimiento.vendedorId().equals(vendedorId)) {
            throw new DomainException("NOT_FOUND", "El pedido no está disponible.");
        }
        if (pedido.modalidad() != ModalidadEntrega.DOMICILIO) {
            throw new DomainException("BUSINESS_RULE",
                    "En este pedido el cobro se confirma al registrar la entrega o el retiro.");
        }
        if (pedido.estado() != EstadoPedido.ACEPTADO) {
            String message = pedido.estado() == EstadoPedido.PENDIENTE
                    ? "Primero debes aceptar el pedido."
                    : "El pedido no está en un estado que permita confirmar el pago.";
            throw new DomainException("INVALID_STATE", message,
                    Map.of("estadoActual", pedido.estado().name()));
        }

        var confirmado = pagos.confirmar(pedido.id(), pedido.compradorId(), vendedorId,
                pedido.modalidad().name());
        return new PagoDomicilioConfirmado(pedido.id(), new Pago(confirmado.id(), confirmado.estado(),
                confirmado.medio(), confirmado.monto(), confirmado.confirmadoAt()));
    }
}
