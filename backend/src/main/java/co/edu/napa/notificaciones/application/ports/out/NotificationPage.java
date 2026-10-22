package co.edu.napa.notificaciones.application.ports.out;

import co.edu.napa.notificaciones.domain.model.Notificacion;

import java.util.List;

public record NotificationPage(
        List<Notificacion> content,
        int page,
        int size,
        long totalElements
) {
    public NotificationPage {
        content = List.copyOf(content);
    }
}
