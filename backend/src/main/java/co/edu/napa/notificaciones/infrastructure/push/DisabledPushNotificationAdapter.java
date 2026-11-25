package co.edu.napa.notificaciones.infrastructure.push;

import co.edu.napa.notificaciones.application.ports.out.PushDeliveryResult;
import co.edu.napa.notificaciones.application.ports.out.PushNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "napa.notifications.fcm",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class DisabledPushNotificationAdapter implements PushNotificationPort {
    private static final Logger log = LoggerFactory.getLogger(DisabledPushNotificationAdapter.class);

    public DisabledPushNotificationAdapter() {
        log.warn("FCM push is disabled: notifications stay in the in-app inbox only. "
                + "Set NAPA_NOTIFICATIONS_FCM_ENABLED=true and the Firebase credentials to send them to phones.");
    }

    @Override
    public PushDeliveryResult send(List<String> tokens, String title, String body) {
        if (tokens.isEmpty()) {
            return new PushDeliveryResult(0, 0, 0, false);
        }
        log.warn(
                "FCM is disabled; {} push notification(s) were not sent, but remain available in-app",
                tokens.size()
        );
        return new PushDeliveryResult(tokens.size(), 0, tokens.size(), false);
    }
}
