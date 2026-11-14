package co.edu.napa.calificaciones.application.ports.in;

import co.edu.napa.calificaciones.domain.model.Calificacion;
import co.edu.napa.calificaciones.domain.model.RolUsuario;
import co.edu.napa.calificaciones.domain.model.TipoEntidad;

import java.util.UUID;

/** RF-01: calificar a una parte de un pedido entregado. */
public interface CalificarPort {
    Calificacion calificar(UUID autorId, RolUsuario rol, NuevaCalificacion calificacion);

    record NuevaCalificacion(UUID pedidoId, TipoEntidad tipoEntidad, UUID entidadId, int puntaje, String comentario) {}
}
