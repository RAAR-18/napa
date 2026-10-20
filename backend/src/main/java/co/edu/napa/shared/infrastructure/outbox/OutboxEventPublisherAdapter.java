package co.edu.napa.shared.infrastructure.outbox;

import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

@Component
public class OutboxEventPublisherAdapter implements EventPublisherPort {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisherAdapter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "event");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Domain events must be published inside a database transaction");
        }

        OutboxEnvelope envelope = new OutboxEnvelope(
                event.eventId(),
                event.type(),
                event.version(),
                event.occurredAt(),
                event.aggregateType(),
                event.aggregateId(),
                event.actorUserId(),
                event.payload(),
                0
        );
        String payload = objectMapper.writeValueAsString(envelope.payload());
        jdbcTemplate.update(
                """
                INSERT INTO outbox_event (
                    event_id, event_type, event_version, occurred_at,
                    aggregate_type, aggregate_id, actor_user_id, payload
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """,
                envelope.eventId(),
                envelope.type(),
                envelope.version(),
                envelope.occurredAt(),
                envelope.aggregateType(),
                envelope.aggregateId(),
                envelope.actorUserId(),
                payload
        );
    }
}
