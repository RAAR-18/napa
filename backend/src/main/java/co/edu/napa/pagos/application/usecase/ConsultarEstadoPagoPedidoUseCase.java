package co.edu.napa.pagos.application.usecase;

import co.edu.napa.pagos.application.ports.in.ConsultarEstadoPagoPedidoPort;
import co.edu.napa.pagos.application.ports.out.PagoRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class ConsultarEstadoPagoPedidoUseCase implements ConsultarEstadoPagoPedidoPort {
    private final PagoRepositoryPort pagos;

    public ConsultarEstadoPagoPedidoUseCase(PagoRepositoryPort pagos) {
        this.pagos = Objects.requireNonNull(pagos, "pagos");
    }

    @Override
    public Optional<String> consultar(UUID pedidoId) {
        return pagos.consultarEstadoPorPedido(Objects.requireNonNull(pedidoId, "pedidoId"))
                .map(Enum::name);
    }
}
