package co.edu.napa.calificaciones.application.ports.in;

import co.edu.napa.calificaciones.domain.model.EstadoCalificacion;
import co.edu.napa.calificaciones.domain.model.RolUsuario;
import co.edu.napa.calificaciones.domain.model.TipoEntidad;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** RF-02 (participantes) y RF-03/RF-06 (administrador): listado de calificaciones con su promedio. */
public interface ConsultarCalificacionesPort {
    Pagina<CalificacionPublica> consultar(UUID usuarioId, RolUsuario rol, Consulta consulta);

    Pagina<CalificacionSupervisada> supervisar(Consulta consulta);

    /** Promedio y total de las calificaciones ACTIVA de un destino; lo usan otras pantallas (RF-27). */
    Resumen resumen(TipoEntidad tipoEntidad, UUID entidadId);

    /**
     * RF-02 en listados (catálogo): promedio de varios emprendimientos o productos a la vez. El vendedor solo recibe
     * los suyos; los destinos que no existen o no son visibles se omiten.
     */
    List<ResumenEntidad> resumenes(UUID usuarioId, RolUsuario rol, TipoEntidad tipoEntidad, List<UUID> entidadIds);

    record ResumenEntidad(UUID entidadId, Double promedio, long total) {}

    record Consulta(
            TipoEntidad tipoEntidad,
            UUID entidadId,
            EstadoCalificacion estado,
            int page,
            int size,
            String sortCampo,
            boolean ascendente
    ) {}

    /** {@code promedio} con un decimal, nulo si no hay calificaciones ("Aún sin calificaciones"). */
    record Resumen(Double promedio, long total) {}

    record Pagina<T>(List<T> content, int page, int size, long totalElements, Resumen resumen) {
        public Pagina {
            content = List.copyOf(content);
        }
    }

    record CalificacionPublica(
            UUID id,
            int puntaje,
            String comentario,
            String autorNombre,
            boolean esMia,
            Instant createdAt
    ) {}

    record CalificacionSupervisada(
            UUID id,
            int puntaje,
            String comentario,
            String estado,
            boolean moderada,
            Autor autor,
            Entidad entidad,
            UUID pedidoId,
            Instant createdAt,
            Instant updatedAt
    ) {}

    record Autor(UUID id, String nombre, String rol) {}

    record Entidad(String tipo, UUID id, String nombre) {}
}
