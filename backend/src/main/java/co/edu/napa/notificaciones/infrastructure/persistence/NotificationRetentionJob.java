package co.edu.napa.notificaciones.infrastructure.persistence;

import co.edu.napa.notificaciones.application.ports.out.NotificacionRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class NotificationRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(NotificationRetentionJob.class);
    private static final Duration RETENTION = Duration.ofDays(30);

    private final NotificacionRepositoryPort notifications;
    private final Clock clock;

    public NotificationRetentionJob(NotificacionRepositoryPort notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Scheduled(cron = "${napa.notifications.retention-cron:0 0 3 * * *}", zone = "UTC")
    public void deleteExpiredNotifications() {
        Instant cutoff = Instant.now(clock).minus(RETENTION);
        int deleted = notifications.deleteCreatedBefore(cutoff);
        if (deleted > 0) {
            log.info("Deleted {} notification(s) older than 30 days", deleted);
        }
    }
}
