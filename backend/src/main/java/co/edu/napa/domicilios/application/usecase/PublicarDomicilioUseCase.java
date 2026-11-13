package co.edu.napa.domicilios.application.usecase;

import co.edu.napa.domicilios.application.PlazosDomicilio;
import co.edu.napa.domicilios.application.ports.in.PublicarDomicilioPort;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import co.edu.napa.domicilios.domain.event.DomicilioPublicadoEvent;
import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.domicilios.domain.model.EventoTrazabilidad;
import co.edu.napa.emprendimientos.application.ports.in.ConsultarEmprendimientoParaPedidoPort;
import co.edu.napa.pagos.application.ports.in.ConsultarEstadoPagoPedidoPort;
import co.edu.napa.pedidos.application.ports.in.PedidoParaDomicilioPort;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.application.ports.out.UbicacionRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.Ubicacion;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class PublicarDomicilioUseCase implements PublicarDomicilioPort {
    private static final String NO_DISPONIBLE = "El pedido no está disponible.";
    private static final String NO_PUBLICABLE = "Solo se publican pedidos de domicilio aceptados y pagados.";

    private final PedidoParaDomicilioPort pedidos;
    private final ConsultarEmprendimientoParaPedidoPort emprendimientos;
    private final ConsultarEstadoPagoPedidoPort pagos;
    private final UbicacionRepositoryPort ubicaciones;
    private final DomicilioRepositoryPort domicilios;
    private final EventPublisherPort eventos;
    private final PlazosDomicilio plazos;
    private final Clock clock;

    public PublicarDomicilioUseCase(
            PedidoParaDomicilioPort pedidos,
            ConsultarEmprendimientoParaPedidoPort emprendimientos,
            ConsultarEstadoPagoPedidoPort pagos,
            UbicacionRepositoryPort ubicaciones,
            DomicilioRepositoryPort domicilios,
            EventPublisherPort eventos,
            PlazosDomicilio plazos,
            Clock clock
    ) {
        this.pedidos = Objects.requireNonNull(pedidos, "pedidos");
        this.emprendimientos = Objects.requireNonNull(emprendimientos, "emprendimientos");
        this.pagos = Objects.requireNonNull(pagos, "pagos");
        this.ubicaciones = Objects.requireNonNull(ubicaciones, "ubicaciones");
        this.domicilios = Objects.requireNonNull(domicilios, "domicilios");
        this.eventos = Objects.requireNonNull(eventos, "eventos");
        this.plazos = Objects.requireNonNull(plazos, "plazos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public DomicilioPublicado publicar(UUID pedidoId, UUID vendedorId) {
        if (pedidoId == null || vendedorId == null) {
            throw new DomainException("VALIDATION_ERROR", "Indica el pedido y el vendedor.");
        }
        var pedido = pedidos.bloquear(pedidoId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", NO_DISPONIBLE));
        var emprendimiento = emprendimientos.consultar(pedido.emprendimientoId())
                .orElseThrow(() -> new DomainException("NOT_FOUND", NO_DISPONIBLE));
        if (!emprendimiento.vendedorId().equals(vendedorId)) {
            throw new DomainException("NOT_FOUND", NO_DISPONIBLE);
        }
        if (!"DOMICILIO".equals(pedido.modalidad())) {
            throw new DomainException("BUSINESS_RULE", NO_PUBLICABLE);
        }
        if (!"ACEPTADO".equals(pedido.estado())) {
            throw new DomainException("INVALID_STATE", NO_PUBLICABLE, Map.of("estadoActual", pedido.estado()));
        }
        if (!"CONFIRMADO".equals(pagos.consultar(pedido.id()).orElse(null))) {
            throw new DomainException("PAYMENT_NOT_CONFIRMED", "Primero debes confirmar el pago del pedido.");
        }
        Instant now = Instant.now(clock);
        if (pedido.reserva() != null) {
            Instant disponibleDesde = pedido.reserva().minus(plazos.margenReserva());
            if (now.isBefore(disponibleDesde)) {
                throw new DomainException("RESERVATION_NOT_DUE",
                        "Podrás publicar el domicilio de esta reserva poco antes de la hora acordada.",
                        Map.of("disponibleDesde", disponibleDesde.toString()));
            }
        }
        if (domicilios.buscarActivoPorPedido(pedido.id()).isPresent()) {
            throw new DomainException("ALREADY_PUBLISHED", "Este pedido ya tiene un domicilio activo.");
        }

        Ubicacion puntoFijo = ubicacion(emprendimiento.ubicacionId());
        Ubicacion destino = ubicacion(pedido.ubicacionEntregaId());
        Ubicacion origen = ubicaciones.save(new Ubicacion(UUID.randomUUID(), puntoFijo.latitud(),
                puntoFijo.longitud(), puntoFijo.direccion(), puntoFijo.referencia(), now, now));

        Domicilio domicilio = Domicilio.publicar(pedido.id(), vendedorId, pedido.compradorId(),
                pedido.tarifaDomicilio(), origen.id(), destino.id(), now);
        domicilios.guardar(domicilio);
        domicilios.registrarEvento(EventoTrazabilidad.de(domicilio, vendedorId));
        eventos.publish(DomicilioPublicadoEvent.de(domicilio));

        return new DomicilioPublicado(domicilio.id(), domicilio.pedidoId(), domicilio.estado().name(),
                domicilio.tarifa(), domicilio.createdAt(), punto(origen), punto(destino));
    }

    private Ubicacion ubicacion(UUID id) {
        if (id == null) {
            throw new DomainException("INVALID_STATE", "El pedido no tiene una ubicación válida.");
        }
        return ubicaciones.findById(id)
                .orElseThrow(() -> new DomainException("INVALID_STATE", "El pedido no tiene una ubicación válida."));
    }

    private static Punto punto(Ubicacion ubicacion) {
        return new Punto(ubicacion.latitud(), ubicacion.longitud(), ubicacion.direccion(), ubicacion.referencia());
    }
}
