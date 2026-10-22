package co.edu.napa.shared.infrastructure.outbox;

import java.util.Set;

public interface OutboxEventHandler {
    String eventType();

    default Set<String> eventTypes() {
        return Set.of(eventType());
    }

    void handle(OutboxEnvelope event) throws Exception;
}
