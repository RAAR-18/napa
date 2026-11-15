package co.edu.napa.calificaciones.application.ports.out;

import co.edu.napa.calificaciones.domain.model.AccionModeracion;
import co.edu.napa.calificaciones.domain.model.Calificacion;
import co.edu.napa.calificaciones.domain.model.EstadoCalificacion;
import co.edu.napa.calificaciones.domain.model.TipoEntidad;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalificacionRepositoryPort {
    boolean existe(UUID autorId, UUID pedidoId, TipoEntidad tipoEntidad, UUID entidadId);

    /** Calificaciones del autor en el pedido (cualquier estado). */
    List<Calificacion> delAutorEnPedido(UUID autorId, UUID pedidoId);

    /** Lanza {@code CONFLICT} si el índice único detecta una calificación igual (carrera de dos envíos). */
    void insertar(Calificacion calificacion);

    /** Bloquea la fila hasta el fin de la transacción. */
    Optional<Calificacion> buscarParaActualizar(UUID id);

    void actualizar(Calificacion calificacion);

    void registrarModeracion(UUID calificacionId, UUID administradorId, AccionModeracion accion, String motivo,
                             String contenidoAnterior, Instant ahora);

    List<CalificacionListada> listar(Filtro filtro, int page, int size, Orden orden);

    long contar(Filtro filtro);

    /** Promedio y total de las {@code ACTIVA} del destino. */
    Resumen resumen(TipoEntidad tipoEntidad, UUID entidadId);

    /** Lo mismo para varios destinos en una consulta; los que no tienen calificaciones no aparecen. */
    java.util.Map<UUID, Resumen> resumenes(TipoEntidad tipoEntidad, java.util.Collection<UUID> entidadIds);

    /** {@code tipoEntidad}/{@code entidadId} y {@code estado} son opcionales (nulos = sin filtro). */
    record Filtro(TipoEntidad tipoEntidad, UUID entidadId, EstadoCalificacion estado) {}

    record Orden(String campo, boolean ascendente) {}

    record Resumen(Double promedio, long total) {}

    record CalificacionListada(
            UUID id,
            int puntaje,
            String comentario,
            EstadoCalificacion estado,
            boolean moderada,
            TipoEntidad tipoEntidad,
            UUID entidadId,
            String entidadNombre,
            UUID pedidoId,
            UUID autorId,
            String autorNombre,
            String autorRol,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
