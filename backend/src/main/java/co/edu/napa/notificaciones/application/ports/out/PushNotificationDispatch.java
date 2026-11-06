package co.edu.napa.notificaciones.application.ports.out;

import java.util.List;

public record PushNotificationDispatch(List<String> tokens, String title, String body) {
    public PushNotificationDispatch {
        tokens = List.copyOf(tokens);
    }
}
