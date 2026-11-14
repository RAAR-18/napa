package co.edu.napa.domicilios.application.usecase;

import co.edu.napa.domicilios.application.ports.in.ConsultarDomicilioDePedidoPort;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class ConsultarDomicilioDePedidoUseCase implements ConsultarDomicilioDePedidoPort {
    private final DomicilioRepositoryPort domicilios;

    public ConsultarDomicilioDePedidoUseCase(DomicilioRepositoryPort domicilios) {
        this.domicilios = Objects.requireNonNull(domicilios, "domicilios");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DomicilioDePedido> porPedido(UUID pedidoId) {
        Objects.requireNonNull(pedidoId, "pedidoId");
        return domicilios.buscarUltimoPorPedido(pedidoId).map(domicilio -> new DomicilioDePedido(
                domicilio.id(), domicilio.estado().name(),
                domicilio.motivoCancelacion() == null ? null : domicilio.motivoCancelacion().name(),
                domicilio.updatedAt(), domicilio.domiciliarioId()));
    }
}
