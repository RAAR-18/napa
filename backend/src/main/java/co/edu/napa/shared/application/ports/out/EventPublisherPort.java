package co.edu.napa.shared.application.ports.out;

import co.edu.napa.shared.domain.DomainEvent;

public interface EventPublisherPort {
    void publish(DomainEvent event);
}
