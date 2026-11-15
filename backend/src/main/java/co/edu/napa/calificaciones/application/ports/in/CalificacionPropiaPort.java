package co.edu.napa.calificaciones.application.ports.in;

import co.edu.napa.calificaciones.domain.model.Calificacion;

import java.util.UUID;

/** RF-04 y RF-05: el autor edita o elimina su propia calificación. */
public interface CalificacionPropiaPort {
    /** {@code puntaje} o {@code comentario} nulos se dejan como están. */
    Calificacion editar(UUID autorId, UUID calificacionId, Integer puntaje, String comentario);

    Calificacion eliminar(UUID autorId, UUID calificacionId);
}
