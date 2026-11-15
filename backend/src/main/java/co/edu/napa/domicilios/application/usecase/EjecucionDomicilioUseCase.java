package co.edu.napa.domicilios.application.usecase;

import co.edu.napa.domicilios.application.ports.in.ConsultarCodigoConfirmacionPort;
import co.edu.napa.domicilios.application.ports.in.ConsultarTrazabilidadPort;
import co.edu.napa.domicilios.application.ports.in.EjecutarDomicilioPort;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import co.edu.napa.domicilios.application.ports.out.IntentosCodigoEntregaPort;
import co.edu.napa.domicilios.application.ports.out.UbicacionDomiciliarioPort;
import co.edu.napa.domicilios.application.ports.in.ActualizarUbicacionDomiciliarioPort;
import co.edu.napa.domicilios.domain.event.DomicilioEnCaminoEvent;
import co.edu.napa.domicilios.domain.event.DomicilioEntregadoEvent;
import co.edu.napa.domicilios.domain.event.DomicilioRecogidoEvent;
import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.domicilios.domain.model.EstadoDomicilio;
import co.edu.napa.domicilios.domain.model.EventoTrazabilidad;
import co.edu.napa.pedidos.application.ports.in.PedidoParaDomicilioPort;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Ejecución y seguimiento del domicilio: RF-23, RF-24, RF-25 y RF-26. */
public class EjecucionDomicilioUseCase
        implements EjecutarDomicilioPort, ConsultarCodigoConfirmacionPort, ConsultarTrazabilidadPort,
        ActualizarUbicacionDomiciliarioPort {
    private static final String NO_DISPONIBLE = "El domicilio no está disponible.";

    private final DomicilioRepositoryPort domicilios;
    private final PedidoParaDomicilioPort pedidos;
    private final IntentosCodigoEntregaPort intentos;
    private final EventPublisherPort eventos;
    private final UbicacionDomiciliarioPort ubicaciones;
    private final Clock clock;

    public EjecucionDomicilioUseCase(
            DomicilioRepositoryPort domicilios,
            PedidoParaDomicilioPort pedidos,
            IntentosCodigoEntregaPort intentos,
            EventPublisherPort eventos,
            Clock clock
    ) {
        this(domicilios, pedidos, intentos, eventos, UbicacionDomiciliarioPort.NINGUNA, clock);
    }

    public EjecucionDomicilioUseCase(
            DomicilioRepositoryPort domicilios,
            PedidoParaDomicilioPort pedidos,
            IntentosCodigoEntregaPort intentos,
            EventPublisherPort eventos,
            UbicacionDomiciliarioPort ubicaciones,
            Clock clock
    ) {
        this.ubicaciones = Objects.requireNonNull(ubicaciones, "ubicaciones");
        this.domicilios = Objects.requireNonNull(domicilios, "domicilios");
        this.pedidos = Objects.requireNonNull(pedidos, "pedidos");
        this.intentos = Objects.requireNonNull(intentos, "intentos");
        this.eventos = Objects.requireNonNull(eventos, "eventos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** RF-23: ASIGNADO → RECOGIDO, solo el domiciliario asignado. */
    @Override
    @Transactional
    public Transicion confirmarRecogida(UUID domiciliarioId, UUID domicilioId) {
        Domicilio recogido = bloquearDelDomiciliario(domiciliarioId, domicilioId)
                .confirmarRecogida(Instant.now(clock));
        domicilios.actualizar(recogido);
        domicilios.registrarEvento(EventoTrazabilidad.de(recogido, domiciliarioId));
        eventos.publish(DomicilioRecogidoEvent.de(recogido, domiciliarioId));
        return transicion(recogido);
    }

    /** RF-23: RECOGIDO → EN_CAMINO, solo el vendedor dueño; el pedido también pasa a EN_CAMINO. */
    @Override
    @Transactional
    public Transicion confirmarSalida(UUID vendedorId, UUID domicilioId) {
        Objects.requireNonNull(vendedorId, "vendedorId");
        Domicilio enCamino = domicilios.bloquear(Objects.requireNonNull(domicilioId, "domicilioId"))
                .filter(value -> value.vendedorId().equals(vendedorId))
                .orElseThrow(() -> new DomainException("NOT_FOUND", NO_DISPONIBLE))
                .confirmarSalida(Instant.now(clock));
        pedidos.ponerEnCaminoPorDomicilio(enCamino.pedidoId());
        domicilios.actualizar(enCamino);
        domicilios.registrarEvento(EventoTrazabilidad.de(enCamino, vendedorId));
        eventos.publish(DomicilioEnCaminoEvent.de(enCamino, vendedorId));
        return transicion(enCamino);
    }

    /**
     * RF-24. Los fallos se cuentan fuera de la base, así que lanzar el error no deshace el conteo. Un código mal
     * formado no cuenta como intento, y durante el bloqueo ni siquiera el código correcto entra.
     */
    @Override
    @Transactional
    public Transicion confirmarEntrega(UUID domiciliarioId, UUID domicilioId, String codigo) {
        if (codigo == null || !codigo.matches("\\d{6}")) {
            String message = "El código debe tener 6 dígitos.";
            throw new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of("codigo", message)));
        }
        Domicilio domicilio = bloquearDelDomiciliario(domiciliarioId, domicilioId);
        Domicilio entregado = domicilio.entregar(Instant.now(clock));
        long bloqueado = intentos.segundosBloqueado(domicilio.id());
        if (bloqueado > 0) {
            throw bloqueo(bloqueado);
        }
        if (!domicilio.codigoCoincide(codigo)) {
            int restantes = intentos.registrarFallo(domicilio.id());
            if (restantes <= 0) {
                throw bloqueo(IntentosCodigoEntregaPort.BLOQUEO.toSeconds());
            }
            throw new DomainException("CODE_INVALID", "El código no es válido.",
                    Map.of("intentosRestantes", restantes));
        }
        pedidos.entregarPorDomicilio(entregado.pedidoId(), entregado.id(), domiciliarioId);
        domicilios.actualizar(entregado);
        domicilios.registrarEvento(EventoTrazabilidad.de(entregado, domiciliarioId));
        eventos.publish(DomicilioEntregadoEvent.de(entregado));
        intentos.limpiar(entregado.id());
        ubicaciones.borrar(entregado.id());
        return transicion(entregado);
    }

    /** RF-25: solo el cliente del pedido, mientras el domicilio está en manos del domiciliario. */
    @Override
    @Transactional(readOnly = true)
    public CodigoConfirmacion consultarCodigo(UUID compradorId, UUID domicilioId) {
        Objects.requireNonNull(compradorId, "compradorId");
        Domicilio domicilio = domicilios.buscar(Objects.requireNonNull(domicilioId, "domicilioId"))
                .filter(value -> value.compradorId().equals(compradorId))
                .orElseThrow(() -> new DomainException("NOT_FOUND", NO_DISPONIBLE));
        if (!domicilio.codigoVisible()) {
            String message = switch (domicilio.estado()) {
                case DISPONIBLE -> "Tu código estará disponible cuando un domiciliario acepte el domicilio.";
                case ENTREGADO -> "El domicilio ya fue entregado.";
                default -> "El domicilio fue cancelado.";
            };
            throw new DomainException("INVALID_STATE", message, Map.of("estadoActual", domicilio.estado().name()));
        }
        return new CodigoConfirmacion(domicilio.id(), domicilio.estado().name(), domicilio.codigoConfirmacion());
    }

    /** RF-26: el cliente o el vendedor del pedido; a cualquier otro se le responde que no existe. */
    @Override
    @Transactional(readOnly = true)
    public Trazabilidad consultarTrazabilidad(UUID usuarioId, UUID domicilioId) {
        Objects.requireNonNull(usuarioId, "usuarioId");
        Domicilio domicilio = domicilios.buscar(Objects.requireNonNull(domicilioId, "domicilioId"))
                .filter(value -> value.compradorId().equals(usuarioId) || value.vendedorId().equals(usuarioId))
                .orElseThrow(() -> new DomainException("NOT_FOUND", NO_DISPONIBLE));
        return new Trazabilidad(
                domicilio.id(),
                domicilio.estado().name(),
                domicilio.motivoCancelacion() == null ? null : domicilio.motivoCancelacion().name(),
                domicilio.updatedAt(),
                domicilios.listarEventos(domicilio.id()).stream()
                        .map(evento -> new Evento(evento.estado().name(), evento.ocurridoAt()))
                        .toList(),
                ubicacionParaCliente(domicilio, usuarioId)
        );
    }

    /** RF-26 FR-003: solo el cliente, solo EN_CAMINO y redondeada a 3 decimales (≈ 110 m). */
    private UbicacionDomiciliario ubicacionParaCliente(Domicilio domicilio, UUID usuarioId) {
        if (!domicilio.compradorId().equals(usuarioId) || domicilio.estado() != EstadoDomicilio.EN_CAMINO) {
            return null;
        }
        return ubicaciones.consultar(domicilio.id())
                .map(p -> new UbicacionDomiciliario(redondear(p.latitud()), redondear(p.longitud()), p.actualizadaAt()))
                .orElse(null);
    }

    private static double redondear(double valor) {
        return Math.round(valor * 1000d) / 1000d;
    }

    /** RF-26: solo el domiciliario asignado y con el domicilio EN_CAMINO; como mucho un envío cada 5 s. */
    @Override
    public void actualizarUbicacion(UUID domiciliarioId, UUID domicilioId, double latitud, double longitud) {
        Objects.requireNonNull(domiciliarioId, "domiciliarioId");
        if (Double.isNaN(latitud) || latitud < -90 || latitud > 90) {
            throw coordenada("latitud", "La latitud debe estar entre -90 y 90.");
        }
        if (Double.isNaN(longitud) || longitud < -180 || longitud > 180) {
            throw coordenada("longitud", "La longitud debe estar entre -180 y 180.");
        }
        Domicilio domicilio = domicilios.buscar(Objects.requireNonNull(domicilioId, "domicilioId"))
                .orElseThrow(() -> new DomainException("NOT_FOUND", NO_DISPONIBLE));
        if (!domicilio.asignadoA(domiciliarioId)) {
            throw new DomainException("FORBIDDEN", "Este domicilio está asignado a otro domiciliario.");
        }
        if (domicilio.estado() != EstadoDomicilio.EN_CAMINO) {
            throw new DomainException("INVALID_STATE", "Solo se comparte la ubicación con el domicilio en camino.",
                    Map.of("estadoActual", domicilio.estado().name()));
        }
        if (!ubicaciones.guardar(domicilio.id(), latitud, longitud, Instant.now(clock))) {
            throw new DomainException("RATE_LIMITED", "Envía la ubicación como mucho cada 5 segundos.",
                    Map.of("retryAfter", 5));
        }
    }

    private static DomainException coordenada(String campo, String mensaje) {
        return new DomainException("VALIDATION_ERROR", mensaje, Map.of("fields", Map.of(campo, mensaje)));
    }

    private Domicilio bloquearDelDomiciliario(UUID domiciliarioId, UUID domicilioId) {
        Objects.requireNonNull(domiciliarioId, "domiciliarioId");
        return domicilios.bloquear(Objects.requireNonNull(domicilioId, "domicilioId"))
                .filter(value -> value.asignadoA(domiciliarioId))
                .orElseThrow(() -> new DomainException("NOT_FOUND", NO_DISPONIBLE));
    }

    private static DomainException bloqueo(long segundos) {
        return new DomainException("CODE_ATTEMPTS_LOCKED",
                "Superaste los intentos para confirmar la entrega. Inténtalo más tarde o crea un reporte.",
                Map.of("retryAfter", segundos));
    }

    private static Transicion transicion(Domicilio domicilio) {
        return new Transicion(domicilio.id(), domicilio.estado().name(), domicilio.updatedAt());
    }
}
