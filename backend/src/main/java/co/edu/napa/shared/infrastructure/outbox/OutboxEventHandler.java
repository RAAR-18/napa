package co.edu.napa.shared.infrastructure.outbox;

public interface OutboxEventHandler {
    String eventType();

    void handle(OutboxEnvelope event) throws Exception;
}
