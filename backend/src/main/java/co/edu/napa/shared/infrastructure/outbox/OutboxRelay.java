package co.edu.napa.shared.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "napa.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;
    private static final int LEASE_SECONDS = 30;
    private static final int MAX_ATTEMPTS = 8;
    private static final int MAX_ERROR_LENGTH = 2_000;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Map<String, OutboxEventHandler> handlers;

    public OutboxRelay(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            List<OutboxEventHandler> handlers
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager")
        );
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(
                OutboxEventHandler::eventType,
                Function.identity(),
                (first, second) -> {
                    throw new IllegalStateException("Multiple outbox handlers registered for one event type");
                }
        ));
    }

    @Scheduled(fixedDelayString = "${napa.outbox.poll-interval-ms:500}")
    public void dispatchPendingEvents() {
        UUID workerId = UUID.randomUUID();
        List<OutboxEnvelope> events = claimBatch(workerId);
        events.forEach(event -> dispatch(workerId, event));
    }

    private List<OutboxEnvelope> claimBatch(UUID workerId) {
        return transactionTemplate.execute(status -> jdbcTemplate.query(
                """
                UPDATE outbox_event
                SET status = 'PROCESSING',
                    claimed_by = ?,
                    locked_until = now() + (? * interval '1 second')
                WHERE event_id IN (
                    SELECT event_id
                    FROM outbox_event
                    WHERE (status = 'PENDING' AND next_attempt_at <= now())
                       OR (status = 'PROCESSING' AND locked_until <= now())
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                RETURNING event_id, event_type, event_version, occurred_at,
                          aggregate_type, aggregate_id, actor_user_id, payload, attempt_count
                """,
                this::mapEnvelope,
                workerId,
                LEASE_SECONDS,
                BATCH_SIZE
        ));
    }

    private void dispatch(UUID workerId, OutboxEnvelope event) {
        try {
            OutboxEventHandler handler = handlers.get(event.type());
            if (handler == null) {
                throw new IllegalStateException("No handler registered for event type " + event.type());
            }
            handler.handle(event);
            int updated = jdbcTemplate.update(
                    """
                    UPDATE outbox_event
                    SET status = 'PROCESSED', processed_at = now(),
                        claimed_by = NULL, locked_until = NULL, last_error = NULL
                    WHERE event_id = ? AND status = 'PROCESSING' AND claimed_by = ?
                    """,
                    event.eventId(),
                    workerId
            );
            if (updated == 0) {
                log.warn("Outbox lease expired before event {} could be acknowledged", event.eventId());
            }
        } catch (Exception exception) {
            retryOrMarkDead(workerId, event, exception);
        }
    }

    private void retryOrMarkDead(UUID workerId, OutboxEnvelope event, Exception exception) {
        int attempts = event.attemptCount() + 1;
        boolean dead = attempts >= MAX_ATTEMPTS;
        long delaySeconds = Math.min(3_600, 2L << Math.min(attempts - 1, 10));
        String error = errorMessage(exception);
        int updated = jdbcTemplate.update(
                """
                UPDATE outbox_event
                SET status = ?, attempt_count = ?, next_attempt_at = now() + (? * interval '1 second'),
                    claimed_by = NULL, locked_until = NULL, last_error = ?
                WHERE event_id = ? AND status = 'PROCESSING' AND claimed_by = ?
                """,
                dead ? "DEAD" : "PENDING",
                attempts,
                delaySeconds,
                error,
                event.eventId(),
                workerId
        );
        if (updated == 0) {
            log.warn("Outbox lease expired while handling event {}", event.eventId(), exception);
        } else if (dead) {
            log.error("Outbox event {} exhausted {} attempts and was marked DEAD", event.eventId(), attempts, exception);
        } else {
            log.warn("Outbox event {} failed on attempt {}; retrying", event.eventId(), attempts, exception);
        }
    }

    private OutboxEnvelope mapEnvelope(ResultSet resultSet, int rowNumber) throws SQLException {
        Map<String, Object> payload = objectMapper.readValue(
                resultSet.getString("payload"),
                new TypeReference<LinkedHashMap<String, Object>>() {}
        );
        return new OutboxEnvelope(
                resultSet.getObject("event_id", UUID.class),
                resultSet.getString("event_type"),
                resultSet.getInt("event_version"),
                resultSet.getObject("occurred_at", Instant.class),
                resultSet.getString("aggregate_type"),
                resultSet.getObject("aggregate_id", UUID.class),
                resultSet.getObject("actor_user_id", UUID.class),
                payload,
                resultSet.getInt("attempt_count")
        );
    }

    private String errorMessage(Exception exception) {
        String message = exception.getMessage();
        String error = exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
