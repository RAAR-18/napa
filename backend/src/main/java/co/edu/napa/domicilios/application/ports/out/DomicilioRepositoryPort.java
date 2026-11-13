package co.edu.napa.domicilios.application.ports.out;

import co.edu.napa.domicilios.application.ports.in.ListarDomiciliosVendedorPort.Filtro;
import co.edu.napa.domicilios.application.ports.in.SupervisarDomiciliosPort;
import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.domicilios.domain.model.EventoTrazabilidad;
import co.edu.napa.shared.domain.geo.GeoPoint;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DomicilioRepositoryPort {
    /** Inserta el domicilio; si el pedido ya tiene uno activo lanza {@code ALREADY_PUBLISHED}. */
    void guardar(Domicilio domicilio);

    void actualizar(Domicilio domicilio);

    void registrarEvento(EventoTrazabilidad evento);

    /** Eventos de trazabilidad del domicilio en orden cronológico (RF-26). */
    List<EventoTrazabilidad> listarEventos(UUID domicilioId);

    Optional<Domicilio> buscar(UUID domicilioId);

    /** Bloquea la fila del domicilio hasta el fin de la transacción. */
    Optional<Domicilio> bloquear(UUID domicilioId);

    Optional<Domicilio> buscarActivoPorPedido(UUID pedidoId);

    Optional<Domicilio> buscarUltimoPorPedido(UUID pedidoId);

    List<Domicilio> listarVendedor(UUID vendedorId, Filtro filtro, long offset, int limit);

    long contarVendedor(UUID vendedorId, Filtro filtro);

    /** Todos los domicilios que cumplen el filtro, con sus puntos (RF-12). */
    List<DomicilioSupervisionProjection> listarGeneral(SupervisarDomiciliosPort.Filtro filtro, long offset, int limit);

    long contarGeneral(SupervisarDomiciliosPort.Filtro filtro);

    Optional<DomicilioSupervisionProjection> buscarConPuntos(UUID domicilioId);

    /** Bloquea (saltando los ya bloqueados) disponibles publicados antes de {@code limite} y aún sin aviso. */
    List<Domicilio> bloquearSinAvisoPublicadosAntesDe(Instant limite, int maximo);

    /** Bloquea (saltando los ya bloqueados) disponibles publicados antes de {@code limite}. */
    List<Domicilio> bloquearDisponiblesPublicadosAntesDe(Instant limite, int maximo);

    /** Serializa las aceptaciones de un mismo domiciliario (bloqueo consultivo de la transacción). */
    void bloquearDomiciliario(UUID domiciliarioId);

    /** Domicilios del domiciliario en {@code ASIGNADO}, {@code RECOGIDO} o {@code EN_CAMINO}. */
    List<Domicilio> listarEnCurso(UUID domiciliarioId);

    /** Registra el rechazo; si ya existía no hace nada. */
    void registrarRechazo(UUID domiciliarioId, UUID domicilioId, Instant now);

    boolean fueRechazadoPor(UUID domiciliarioId, UUID domicilioId);

    /**
     * Disponibles que el domiciliario no rechazó y cuyo origen (A) está a {@code radioMetros} o menos de su
     * posición, con sus puntos y distancias (RF-16).
     */
    List<DomicilioDisponibleProjection> listarDisponibles(
            UUID domiciliarioId, OrdenDisponibles orden, GeoPoint posicion, long radioMetros, long offset, int limit);

    long contarDisponibles(UUID domiciliarioId, GeoPoint posicion, long radioMetros);

    /** Distancia en línea recta de A a B, en metros. */
    long distanciaMetros(UUID ubicacionOrigenId, UUID ubicacionDestinoId);

    enum OrdenDisponibles { RECIENTES, CERCANIA, GANANCIA }
}
