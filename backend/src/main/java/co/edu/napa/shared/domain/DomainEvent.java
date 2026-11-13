package co.edu.napa.shared.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface DomainEvent {
    UUID eventId();

    String type();

    default int version() {
        return 1;
    }

    Instant occurredAt();

    String aggregateType();

    UUID aggregateId();

    UUID actorUserId();

    Map<String, Object> payload();
}
