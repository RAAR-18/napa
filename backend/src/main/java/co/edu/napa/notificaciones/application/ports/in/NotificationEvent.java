package co.edu.napa.notificaciones.application.ports.in;

import java.util.Map;
import java.util.UUID;

public record NotificationEvent(UUID eventId, String type, Map<String, Object> payload) {
    public NotificationEvent {
        payload = Map.copyOf(payload);
    }
}
