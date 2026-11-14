package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.pedidos.application.ports.in.ConsultarPedidosSinCerrarPort;
import co.edu.napa.pedidos.application.ports.out.PedidoRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class ConsultarPedidosSinCerrarUseCase implements ConsultarPedidosSinCerrarPort {
    private final PedidoRepositoryPort pedidos;

    public ConsultarPedidosSinCerrarUseCase(PedidoRepositoryPort pedidos) {
        this.pedidos = Objects.requireNonNull(pedidos, "pedidos");
    }

    @Override
    public long contarDeEmprendimiento(UUID emprendimientoId) {
        return pedidos.contarSinCerrarDeEmprendimiento(Objects.requireNonNull(emprendimientoId, "emprendimientoId"));
    }
}
