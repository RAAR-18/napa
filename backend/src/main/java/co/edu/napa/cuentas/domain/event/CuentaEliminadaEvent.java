package co.edu.napa.cuentas.domain.event;

import co.edu.napa.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** E18: la cuenta pasó a ELIMINADA (RF-11 o RF-68); notificaciones borra las del usuario. */
public record CuentaEliminadaEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID actorUserId
) implements DomainEvent {
    public static CuentaEliminadaEvent de(UUID usuarioId, UUID actorId, Instant ahora) {
        return new CuentaEliminadaEvent(UUID.randomUUID(), ahora, usuarioId, actorId);
    }

    @Override
    public String type() {
        return "cuentas.cuenta-eliminada";
    }

    @Override
    public String aggregateType() {
        return "Usuario";
    }

    @Override
    public Map<String, Object> payload() {
        return Map.of("usuarioId", aggregateId);
    }
}
