package co.edu.napa.calificaciones.application.ports.in;

import co.edu.napa.calificaciones.domain.model.Calificacion;

import java.util.UUID;

/** RF-06: el administrador elimina un comentario o corrige su texto, siempre con un motivo. */
public interface ModerarCalificacionPort {
    Calificacion eliminar(UUID administradorId, UUID calificacionId, String motivo);

    Calificacion editarTexto(UUID administradorId, UUID calificacionId, String comentario, String motivo);
}
