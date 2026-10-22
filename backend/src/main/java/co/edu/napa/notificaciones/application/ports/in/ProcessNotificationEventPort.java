package co.edu.napa.notificaciones.application.ports.in;

import co.edu.napa.notificaciones.application.ports.out.PushNotificationDispatch;

import java.util.List;

public interface ProcessNotificationEventPort {
    List<PushNotificationDispatch> process(NotificationEvent event);
}
