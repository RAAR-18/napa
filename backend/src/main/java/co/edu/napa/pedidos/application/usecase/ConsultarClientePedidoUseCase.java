package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarContactoUsuarioPort;
import co.edu.napa.pedidos.application.ports.in.ConsultarClientePedidoPort;
import co.edu.napa.pedidos.application.ports.out.PedidoDetalleProjection;
import co.edu.napa.pedidos.application.ports.out.PedidoRepositoryPort;
import co.edu.napa.pedidos.domain.model.EstadoPedido;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** RF-52: nombre siempre; celular y dirección solo mientras el pedido está en curso. */
@Component
public class ConsultarClientePedidoUseCase implements ConsultarClientePedidoPort {
    private static final Set<EstadoPedido> CERRADOS =
            Set.of(EstadoPedido.ENTREGADO, EstadoPedido.RECHAZADO, EstadoPedido.CANCELADO);
    static final String CLIENTE_ELIMINADO = "Cliente eliminado";

    private final PedidoRepositoryPort pedidos;
    private final ConsultarContactoUsuarioPort contactos;

    public ConsultarClientePedidoUseCase(PedidoRepositoryPort pedidos, ConsultarContactoUsuarioPort contactos) {
        this.pedidos = Objects.requireNonNull(pedidos, "pedidos");
        this.contactos = Objects.requireNonNull(contactos, "contactos");
    }

    @Override
    @Transactional(readOnly = true)
    public ClientePedido consultar(UUID vendedorId, UUID pedidoId) {
        Objects.requireNonNull(vendedorId, "vendedorId");
        Objects.requireNonNull(pedidoId, "pedidoId");
        PedidoDetalleProjection pedido = pedidos.consultarDetalleVendedor(vendedorId, pedidoId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "El pedido no está disponible."));
        ConsultarContactoUsuarioPort.Contacto contacto = contactos.consultar(pedido.clienteId()).orElse(null);
        if (contacto == null || contacto.eliminada()) {
            return new ClientePedido(CLIENTE_ELIMINADO, null, null);
        }
        if (CERRADOS.contains(pedido.estado())) {
            return new ClientePedido(contacto.nombre(), null, null);
        }
        PedidoDetalleProjection.Ubicacion entrega = pedido.ubicacionEntrega();
        DireccionEntrega direccion = entrega == null ? null : new DireccionEntrega(
                entrega.latitud(), entrega.longitud(), entrega.direccion(), entrega.referencia());
        return new ClientePedido(contacto.nombre(), contacto.celular(), direccion);
    }
}
