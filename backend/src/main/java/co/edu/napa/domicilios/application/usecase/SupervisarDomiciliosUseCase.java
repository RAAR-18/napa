package co.edu.napa.domicilios.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarNombresUsuarioPort;
import co.edu.napa.domicilios.application.ports.in.SupervisarDomiciliosPort;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import co.edu.napa.domicilios.application.ports.out.DomicilioSupervisionProjection;
import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.domicilios.domain.model.EstadoDomicilio;
import co.edu.napa.domicilios.domain.model.EventoTrazabilidad;
import co.edu.napa.pagos.application.ports.in.PagoTarifaDomicilioPort;
import co.edu.napa.pedidos.application.ports.in.PedidoParaDomicilioPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.Ubicacion;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * RF-12. Sin filtros implícitos de rol ni de estado (SC-002). Los nombres se piden en un solo lote por página y los
 * puntos salen en la misma consulta del domicilio, así no hay una consulta por fila.
 */
public class SupervisarDomiciliosUseCase implements SupervisarDomiciliosPort {
    private static final int MAX_SIZE = 100;

    private final DomicilioRepositoryPort domicilios;
    private final PedidoParaDomicilioPort pedidos;
    private final PagoTarifaDomicilioPort pagos;
    private final ConsultarNombresUsuarioPort usuarios;
    private final Clock clock;

    public SupervisarDomiciliosUseCase(
            DomicilioRepositoryPort domicilios,
            PedidoParaDomicilioPort pedidos,
            PagoTarifaDomicilioPort pagos,
            ConsultarNombresUsuarioPort usuarios,
            Clock clock
    ) {
        this.domicilios = Objects.requireNonNull(domicilios, "domicilios");
        this.pedidos = Objects.requireNonNull(pedidos, "pedidos");
        this.pagos = Objects.requireNonNull(pagos, "pagos");
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina listar(Filtro filtro, int page, int size) {
        if (page < 0) {
            throw validation("page", "La página no puede ser negativa.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw validation("size", "El tamaño de página debe estar entre 1 y 100.");
        }
        Filtro efectivo = filtro == null ? Filtro.SIN_FILTROS : filtro;
        if (efectivo.desde() != null && efectivo.hasta() != null && !efectivo.desde().isBefore(efectivo.hasta())) {
            throw validation("desde", "La fecha inicial no puede ser posterior a la fecha final.");
        }
        List<DomicilioSupervisionProjection> filas = domicilios.listarGeneral(efectivo, (long) page * size, size);
        long total = domicilios.contarGeneral(efectivo);
        Set<UUID> ids = new HashSet<>();
        filas.forEach(fila -> agregarPersonas(ids, fila.domicilio()));
        Map<UUID, String> nombres = ids.isEmpty() ? Map.of() : usuarios.consultar(ids);
        Instant now = Instant.now(clock);
        List<Fila> content = filas.stream().map(fila -> {
            Domicilio domicilio = fila.domicilio();
            return new Fila(domicilio.id(), domicilio.pedidoId(), domicilio.estado(), domicilio.tarifa(),
                    minutosSinTomar(domicilio, now), punto(fila.origen()), punto(fila.destino()),
                    persona(domicilio.vendedorId(), nombres), persona(domicilio.compradorId(), nombres),
                    persona(domicilio.domiciliarioId(), nombres), motivo(domicilio), domicilio.createdAt(),
                    domicilio.updatedAt());
        }).toList();
        return new Pagina(content, page, size, total);
    }

    @Override
    @Transactional(readOnly = true)
    public Detalle consultar(UUID domicilioId) {
        Objects.requireNonNull(domicilioId, "domicilioId");
        DomicilioSupervisionProjection fila = domicilios.buscarConPuntos(domicilioId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "El domicilio no existe."));
        Domicilio domicilio = fila.domicilio();
        List<EventoTrazabilidad> eventos = domicilios.listarEventos(domicilioId);
        Set<UUID> ids = new HashSet<>();
        agregarPersonas(ids, domicilio);
        eventos.stream().map(EventoTrazabilidad::usuarioId).filter(Objects::nonNull).forEach(ids::add);
        Map<UUID, String> nombres = usuarios.consultar(ids);
        Pedido pedido = pedidos.resumen(domicilio.pedidoId(), domicilio.compradorId())
                .map(resumen -> new Pedido(resumen.id(), resumen.modalidad(), resumen.estado(), resumen.total(),
                        resumen.cantidadItems()))
                .orElse(null);
        PagoTarifa pagoTarifa = pagos.consultar(domicilioId)
                .map(pago -> new PagoTarifa(pago.estado(), pago.monto()))
                .orElse(null);
        List<Evento> trazabilidad = eventos.stream()
                .map(evento -> new Evento(evento.estado(), evento.ocurridoAt(), persona(evento.usuarioId(), nombres)))
                .toList();
        return new Detalle(domicilio.id(), domicilio.pedidoId(), domicilio.estado(), domicilio.tarifa(),
                minutosSinTomar(domicilio, Instant.now(clock)), punto(fila.origen()), punto(fila.destino()),
                persona(domicilio.vendedorId(), nombres), persona(domicilio.compradorId(), nombres),
                persona(domicilio.domiciliarioId(), nombres), domicilio.createdAt(), domicilio.updatedAt(), pedido,
                motivo(domicilio), domicilio.detalleCancelacion(), pagoTarifa, trazabilidad);
    }

    private static void agregarPersonas(Set<UUID> ids, Domicilio domicilio) {
        ids.add(domicilio.vendedorId());
        ids.add(domicilio.compradorId());
        if (domicilio.domiciliarioId() != null) {
            ids.add(domicilio.domiciliarioId());
        }
    }

    private static Long minutosSinTomar(Domicilio domicilio, Instant now) {
        if (domicilio.estado() != EstadoDomicilio.DISPONIBLE) {
            return null;
        }
        return Math.max(0, Duration.between(domicilio.createdAt(), now).toMinutes());
    }

    private static Persona persona(UUID id, Map<UUID, String> nombres) {
        return id == null ? null : new Persona(id, nombres.get(id));
    }

    private static Punto punto(Ubicacion ubicacion) {
        return new Punto(ubicacion.latitud(), ubicacion.longitud(), ubicacion.direccion(), ubicacion.barrio());
    }

    private static String motivo(Domicilio domicilio) {
        return domicilio.motivoCancelacion() == null ? null : domicilio.motivoCancelacion().name();
    }

    private static DomainException validation(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of(field, message)));
    }
}
