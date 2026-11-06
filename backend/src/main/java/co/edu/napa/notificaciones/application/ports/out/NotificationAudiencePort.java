package co.edu.napa.notificaciones.application.ports.out;

import java.util.List;
import java.util.UUID;

public interface NotificationAudiencePort {
    boolean isActiveUser(UUID usuarioId);

    List<UUID> findActiveCouriers();

    List<String> findPushTokens(UUID usuarioId);
}
