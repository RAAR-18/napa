package co.edu.napa.domicilios.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarContactoUsuarioPort;
import co.edu.napa.domicilios.application.ports.in.AceptarDomicilioPort;
import co.edu.napa.domicilios.application.ports.in.ConsultarDomicilioDomiciliarioPort;
import co.edu.napa.domicilios.application.ports.in.ListarDomiciliosDisponiblesPort;
import co.edu.napa.domicilios.application.ports.in.ListarDomiciliosEnCursoPort;
import co.edu.napa.domicilios.application.ports.in.PuntoDomicilio;
import co.edu.napa.domicilios.application.ports.in.RechazarDomicilioPort;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort.OrdenDisponibles;
import co.edu.napa.domicilios.domain.event.DomicilioAsignadoEvent;
import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.domicilios.domain.model.EstadoDomicilio;
import co.edu.napa.domicilios.domain.model.EventoTrazabilidad;
import co.edu.napa.pedidos.application.ports.in.PedidoParaDomicilioPort;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.application.ports.out.UbicacionRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.GeoPoint;
import co.edu.napa.shared.domain.geo.Ubicacion;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Acciones del domiciliario sobre los domicilios: RF-16, RF-17, RF-18 y RF-19. */
public class DomiciliarioUseCase implements ListarDomiciliosDisponiblesPort, ConsultarDomicilioDomiciliarioPort,
        RechazarDomicilioPort, AceptarDomicilioPort, ListarDomiciliosEnCursoPort {
    private static final String NO_EXISTE = "El domicilio no está disponible.";
    private static final String YA_NO_DISPONIBLE = "El domicilio ya no está disponible.";
    private static final int MAX_SIZE = 50;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Radio de búsqueda por defecto: el origen (A) a 5 km o menos del domiciliario. */
    public static final long RADIO_POR_DEFECTO_METROS = 5_000;

    private final DomicilioRepositoryPort domicilios;
    private final UbicacionRepositoryPort ubicaciones;
    private final PedidoParaDomicilioPort pedidos;
    private final ConsultarContactoUsuarioPort usuarios;
    private final EventPublisherPort eventos;
    private final Clock clock;
    private final Supplier<String> codigos;
    private final long radioMetros;

    public DomiciliarioUseCase(
            DomicilioRepositoryPort domicilios,
            UbicacionRepositoryPort ubicaciones,
            PedidoParaDomicilioPort pedidos,
            ConsultarContactoUsuarioPort usuarios,
            EventPublisherPort eventos,
            Clock clock,
            long radioMetros
    ) {
        this(domicilios, ubicaciones, pedidos, usuarios, eventos, clock,
                () -> "%06d".formatted(RANDOM.nextInt(1_000_000)), radioMetros);
    }

    DomiciliarioUseCase(
            DomicilioRepositoryPort domicilios,
            UbicacionRepositoryPort ubicaciones,
            PedidoParaDomicilioPort pedidos,
            ConsultarContactoUsuarioPort usuarios,
            EventPublisherPort eventos,
            Clock clock,
            Supplier<String> codigos,
            long radioMetros
    ) {
        if (radioMetros <= 0) {
            throw new IllegalArgumentException("radioMetros must be positive");
        }
        this.radioMetros = radioMetros;
        this.domicilios = Objects.requireNonNull(domicilios, "domicilios");
        this.ubicaciones = Objects.requireNonNull(ubicaciones, "ubicaciones");
        this.pedidos = Objects.requireNonNull(pedidos, "pedidos");
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.eventos = Objects.requireNonNull(eventos, "eventos");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.codigos = Objects.requireNonNull(codigos, "codigos");
    }

    /** RF-16. */
    @Override
    @Transactional(readOnly = true)
    public ListarDomiciliosDisponiblesPort.Pagina listar(
            UUID domiciliarioId, Orden orden, GeoPoint posicion, int page, int size) {
        Objects.requireNonNull(domiciliarioId, "domiciliarioId");
        Orden efectivo = orden == null ? Orden.RECIENTES : orden;
        if (posicion == null) {
            throw validation("lat", "Indica tu ubicación para ver los domicilios cercanos.");
        }
        if (page < 0) {
            throw validation("page", "La página no puede ser negativa.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw validation("size", "El tamaño de página debe estar entre 1 y 50.");
        }
        var filas = domicilios.listarDisponibles(domiciliarioId, OrdenDisponibles.valueOf(efectivo.name()), posicion,
                radioMetros, (long) page * size, size);
        var items = filas.stream().map(fila -> new ListarDomiciliosDisponiblesPort.Item(
                fila.id(), fila.tarifa(), fila.distanciaMetros(), fila.distanciaAlOrigenMetros(), fila.createdAt(),
                PuntoDomicilio.exacto(fila.origen()), PuntoDomicilio.aproximado(fila.destino()))).toList();
        return new ListarDomiciliosDisponiblesPort.Pagina(items, page, size,
                domicilios.contarDisponibles(domiciliarioId, posicion, radioMetros), radioMetros);
    }

    /** RF-18. */
    @Override
    @Transactional(readOnly = true)
    public Detalle consultar(UUID domiciliarioId, UUID domicilioId) {
        Objects.requireNonNull(domiciliarioId, "domiciliarioId");
        Domicilio domicilio = domicilios.buscar(Objects.requireNonNull(domicilioId, "domicilioId"))
                .orElseThrow(() -> new DomainException("NOT_FOUND", NO_EXISTE));
        boolean mio = domicilio.asignadoA(domiciliarioId);
        boolean disponibleParaMi = domicilio.estado() == EstadoDomicilio.DISPONIBLE
                && !domicilios.fueRechazadoPor(domiciliarioId, domicilio.id());
        if (!mio && !disponibleParaMi) {
            throw new DomainException("NOT_AVAILABLE", YA_NO_DISPONIBLE,
                    Map.of("estadoActual", domicilio.estado().name()));
        }
        Ubicacion origen = ubicacion(domicilio.ubicacionOrigenId());
        Ubicacion destino = ubicacion(domicilio.ubicacionDestinoId());
        var contacto = usuarios.consultar(domicilio.compradorId()).orElse(null);
        String nombre = contacto == null ? null : contacto.nombre();
        String celular = mio && contacto != null ? contacto.celular() : null;
        return new Detalle(
                domicilio.id(),
                domicilio.estado().name(),
                domicilio.tarifa(),
                mio,
                mio && Domicilio.EN_CURSO.contains(domicilio.estado()),
                domicilios.distanciaMetros(domicilio.ubicacionOrigenId(), domicilio.ubicacionDestinoId()),
                PuntoDomicilio.exacto(origen),
                mio ? PuntoDomicilio.exacto(destino) : PuntoDomicilio.aproximado(destino),
                new Receptor(nombre, celular),
                new ResumenItems(pedidos.cantidadItems(domicilio.pedidoId())),
                domicilio.asignadoAt());
    }

    /** RF-17. */
    @Override
    @Transactional
    public void rechazar(UUID domiciliarioId, UUID domicilioId) {
        Objects.requireNonNull(domiciliarioId, "domiciliarioId");
        Domicilio domicilio = domicilios.buscar(Objects.requireNonNull(domicilioId, "domicilioId"))
                .orElseThrow(() -> new DomainException("NOT_FOUND", NO_EXISTE));
        if (domicilio.estado() != EstadoDomicilio.DISPONIBLE) {
            throw new DomainException("NOT_AVAILABLE", "Este domicilio ya no está disponible.",
                    Map.of("estadoActual", domicilio.estado().name()));
        }
        domicilios.registrarRechazo(domiciliarioId, domicilio.id(), Instant.now(clock));
    }

    /** RF-19. */
    @Override
    @Transactional
    public DomicilioAceptado aceptar(UUID domiciliarioId, UUID domicilioId) {
        Objects.requireNonNull(domiciliarioId, "domiciliarioId");
        Objects.requireNonNull(domicilioId, "domicilioId");
        // Primero el domiciliario y luego el domicilio: dos aceptaciones suyas no pueden pasar del máximo a la vez.
        domicilios.bloquearDomiciliario(domiciliarioId);
        Domicilio domicilio = domicilios.bloquear(domicilioId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", NO_EXISTE));
        if (domicilio.estado() != EstadoDomicilio.DISPONIBLE
                || domicilios.fueRechazadoPor(domiciliarioId, domicilio.id())) {
            throw new DomainException("NOT_AVAILABLE", YA_NO_DISPONIBLE,
                    Map.of("estadoActual", domicilio.estado().name()));
        }
        int enCurso = domicilios.listarEnCurso(domiciliarioId).size();
        if (enCurso >= Domicilio.MAXIMO_EN_CURSO) {
            throw new DomainException("MAX_ACTIVE_DELIVERIES",
                    "Ya tienes 3 domicilios en curso. Entrega uno antes de aceptar otro.",
                    Map.of("enCurso", enCurso, "maximo", Domicilio.MAXIMO_EN_CURSO));
        }
        Domicilio asignado = domicilio.asignar(domiciliarioId, codigos.get(), Instant.now(clock));
        domicilios.actualizar(asignado);
        domicilios.registrarEvento(EventoTrazabilidad.de(asignado, domiciliarioId));
        eventos.publish(DomicilioAsignadoEvent.de(asignado));
        return new DomicilioAceptado(asignado.id(), asignado.estado().name(), asignado.tarifa(), true,
                asignado.updatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ListarDomiciliosEnCursoPort.Item> listar(UUID domiciliarioId) {
        Objects.requireNonNull(domiciliarioId, "domiciliarioId");
        return domicilios.listarEnCurso(domiciliarioId).stream().map(domicilio ->
                new ListarDomiciliosEnCursoPort.Item(domicilio.id(), domicilio.estado().name(), domicilio.tarifa(),
                        domicilio.asignadoAt(), PuntoDomicilio.exacto(ubicacion(domicilio.ubicacionOrigenId())),
                        PuntoDomicilio.exacto(ubicacion(domicilio.ubicacionDestinoId())))).toList();
    }

    private Ubicacion ubicacion(UUID id) {
        return ubicaciones.findById(id)
                .orElseThrow(() -> new DomainException("INVALID_STATE", "El domicilio no tiene una ubicación válida."));
    }

    private static DomainException validation(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of(field, message)));
    }
}
