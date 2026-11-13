package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarPerfilPublicoUsuarioPort;
import co.edu.napa.domicilios.application.ports.in.ConsultarDomicilioDePedidoPort;
import co.edu.napa.pedidos.application.ports.in.ConsultarDomiciliarioPedidoPort;
import co.edu.napa.pedidos.application.ports.out.PedidoRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class ConsultarDomiciliarioPedidoUseCase implements ConsultarDomiciliarioPedidoPort {
    private static final String NO_DISPONIBLE = "El pedido no está disponible.";

    private final PedidoRepositoryPort pedidos;
    private final ConsultarDomicilioDePedidoPort domicilios;
    private final ConsultarPerfilPublicoUsuarioPort usuarios;

    public ConsultarDomiciliarioPedidoUseCase(
            PedidoRepositoryPort pedidos,
            ConsultarDomicilioDePedidoPort domicilios,
            ConsultarPerfilPublicoUsuarioPort usuarios
    ) {
        this.pedidos = Objects.requireNonNull(pedidos, "pedidos");
        this.domicilios = Objects.requireNonNull(domicilios, "domicilios");
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
    }

    @Override
    public DomiciliarioDePedido consultarComoCliente(UUID clienteId, UUID pedidoId) {
        Objects.requireNonNull(clienteId, "clienteId");
        Objects.requireNonNull(pedidoId, "pedidoId");
        pedidos.consultarDetalleCliente(clienteId, pedidoId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", NO_DISPONIBLE));
        return consultar(pedidoId);
    }

    @Override
    public DomiciliarioDePedido consultarComoVendedor(UUID vendedorId, UUID pedidoId) {
        Objects.requireNonNull(vendedorId, "vendedorId");
        Objects.requireNonNull(pedidoId, "pedidoId");
        pedidos.consultarDetalleVendedor(vendedorId, pedidoId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", NO_DISPONIBLE));
        return consultar(pedidoId);
    }

    private DomiciliarioDePedido consultar(UUID pedidoId) {
        return domicilios.porPedido(pedidoId)
                .map(domicilio -> new DomiciliarioDePedido(
                        pedidoId,
                        domicilio.id(),
                        domicilio.estado(),
                        domicilio.domiciliarioId() == null ? null : usuarios.consultarPerfil(domicilio.domiciliarioId())
                                .map(perfil -> new Domiciliario(perfil.nombre(), perfil.fotoUrl(), null))
                                .orElse(null)))
                .orElseGet(() -> new DomiciliarioDePedido(pedidoId, null, null, null));
    }
}
