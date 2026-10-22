package co.edu.napa.notificaciones.application.ports.out;

import java.util.List;

public interface PushNotificationPort {
    PushDeliveryResult send(List<String> tokens, String title, String body);
}
