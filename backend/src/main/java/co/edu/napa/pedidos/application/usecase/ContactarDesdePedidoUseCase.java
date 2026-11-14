package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarContactoUsuarioPort;
import co.edu.napa.domicilios.application.ports.in.ConsultarDomicilioDePedidoPort;
import co.edu.napa.domicilios.application.ports.in.ConsultarDomicilioDePedidoPort.DomicilioDePedido;
import co.edu.napa.pedidos.application.ports.in.ContactarDesdePedidoPort;
import co.edu.napa.pedidos.application.ports.out.CanalContactoRepositoryPort;
import co.edu.napa.pedidos.application.ports.out.PedidoDetalleProjection;
import co.edu.napa.pedidos.application.ports.out.PedidoRepositoryPort;
import co.edu.napa.pedidos.domain.model.EstadoPedido;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * RF-49. Solo con el pedido abierto; al domiciliario, solo mientras tiene el domicilio en curso. Se guarda el canal
 * (tipo, destinatario y fecha), nunca la conversación, que ocurre en las apps del teléfono.
 */
@Component
public class ContactarDesdePedidoUseCase implements ContactarDesdePedidoPort {
    private static final Set<EstadoPedido> CERRADOS =
            Set.of(EstadoPedido.ENTREGADO, EstadoPedido.RECHAZADO, EstadoPedido.CANCELADO);
    private static final Set<String> DOMICILIO_EN_CURSO = Set.of("ASIGNADO", "RECOGIDO", "EN_CAMINO");

    private final PedidoRepositoryPort pedidos;
    private final CanalContactoRepositoryPort canales;
    private final ConsultarDomicilioDePedidoPort domicilios;
    private final ConsultarContactoUsuarioPort contactos;
    private final Clock clock;

    public ContactarDesdePedidoUseCase(
            PedidoRepositoryPort pedidos,
            CanalContactoRepositoryPort canales,
            ConsultarDomicilioDePedidoPort domicilios,
            ConsultarContactoUsuarioPort contactos,
            Clock clock
    ) {
        this.pedidos = Objects.requireNonNull(pedidos, "pedidos");
        this.canales = Objects.requireNonNull(canales, "canales");
        this.domicilios = Objects.requireNonNull(domicilios, "domicilios");
        this.contactos = Objects.requireNonNull(contactos, "contactos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public CanalContacto contactar(UUID clienteId, UUID pedidoId, Destinatario destinatario, Tipo tipo) {
        Objects.requireNonNull(clienteId, "clienteId");
        Objects.requireNonNull(pedidoId, "pedidoId");
        Objects.requireNonNull(destinatario, "destinatario");
        Objects.requireNonNull(tipo, "tipo");
        PedidoDetalleProjection pedido = pedidos.consultarDetalleCliente(clienteId, pedidoId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "El pedido no está disponible."));
        if (CERRADOS.contains(pedido.estado())) {
            throw new DomainException("INVALID_STATE", "Este pedido ya no admite contacto.",
                    Map.of("estadoActual", pedido.estado().name()));
        }
        UUID destinatarioId = destinatario == Destinatario.VENDEDOR
                ? canales.vendedorDelPedido(pedidoId)
                        .orElseThrow(() -> new DomainException("NOT_FOUND", "El pedido no está disponible."))
                : domiciliarioEnCurso(pedidoId);
        String celular = contactos.consultar(destinatarioId)
                .filter(contacto -> !contacto.eliminada())
                .map(ConsultarContactoUsuarioPort.Contacto::celular)
                .filter(valor -> valor != null && !valor.isBlank())
                .orElseThrow(() -> new DomainException("INVALID_STATE", "No es posible contactar a esta persona.",
                        Map.of("estadoActual", pedido.estado().name())));
        UUID id = UUID.randomUUID();
        canales.registrar(id, pedidoId, clienteId, destinatarioId, destinatario.name(), tipo.name(), clock.instant());
        return new CanalContacto(id, destinatario, tipo, enlace(tipo, celular));
    }

    private UUID domiciliarioEnCurso(UUID pedidoId) {
        DomicilioDePedido domicilio = domicilios.porPedido(pedidoId)
                .orElseThrow(() -> new DomainException("INVALID_STATE", "El pedido no tiene domiciliario.",
                        Map.of("estadoActual", "SIN_DOMICILIO")));
        if (domicilio.domiciliarioId() == null || !DOMICILIO_EN_CURSO.contains(domicilio.estado())) {
            throw new DomainException("INVALID_STATE", "El pedido no tiene un domiciliario en camino.",
                    Map.of("estadoActual", domicilio.estado()));
        }
        return domicilio.domiciliarioId();
    }

    static String enlace(Tipo tipo, String celular) {
        return tipo == Tipo.LLAMADA ? "tel:" + celular : "https://wa.me/" + celular.replace("+", "");
    }
}
