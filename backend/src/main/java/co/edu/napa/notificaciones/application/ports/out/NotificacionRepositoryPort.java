package co.edu.napa.notificaciones.application.ports.out;

import co.edu.napa.notificaciones.domain.model.Notificacion;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificacionRepositoryPort {
    boolean saveIfAbsent(UUID eventId, Notificacion notificacion);

    NotificationPage findByUsuario(UUID usuarioId, Boolean leida, int page, int size, String order);

    Optional<Notificacion> openForUsuario(UUID notificacionId, UUID usuarioId, Instant openedAt);

    int deleteByUsuario(UUID usuarioId);

    int deleteCreatedBefore(Instant cutoff);
}
