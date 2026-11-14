package co.edu.napa.calificaciones.application.ports.in;

import co.edu.napa.calificaciones.domain.model.Calificacion;
import co.edu.napa.calificaciones.domain.model.RolUsuario;
import co.edu.napa.calificaciones.domain.model.TipoEntidad;

import java.util.UUID;

/** RF-01: calificar a una parte de un pedido entregado. */
public interface CalificarPort {
    Calificacion calificar(UUID autorId, RolUsuario rol, NuevaCalificacion calificacion);

    /** A quién puede calificar el usuario en un pedido entregado (para la pantalla "Calificar"). */
    Opciones opciones(UUID usuarioId, RolUsuario rol, UUID pedidoId, UUID domicilioId);

    /** {@code calificacionId} no es nulo si ya lo calificó; {@code rol} es CLIENTE o DOMICILIARIO para usuarios. */
    record Opcion(TipoEntidad tipoEntidad, UUID entidadId, String nombre, String rol, UUID calificacionId) {}

    record Opciones(UUID pedidoId, String estadoPedido, java.util.List<Opcion> opciones) {}

    record NuevaCalificacion(UUID pedidoId, TipoEntidad tipoEntidad, UUID entidadId, int puntaje, String comentario) {}
}
