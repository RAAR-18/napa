package co.edu.napa.shared.infrastructure.outbox;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record OutboxEnvelope(
        UUID eventId,
        String type,
        int version,
        Instant occurredAt,
        String aggregateType,
        UUID aggregateId,
        UUID actorUserId,
        Map<String, Object> payload,
        int attemptCount
) {
    public OutboxEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(actorUserId, "actorUserId");
        payload = Map.copyOf(Objects.requireNonNull(payload, "payload"));
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must not be negative");
        }
    }
}
