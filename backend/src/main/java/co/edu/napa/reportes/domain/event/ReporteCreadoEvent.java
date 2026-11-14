package co.edu.napa.reportes.domain.event;

import co.edu.napa.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** E19: un usuario creó un reporte; notificaciones avisa a los administradores con cuenta ACTIVA. */
public record ReporteCreadoEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID actorUserId
) implements DomainEvent {
    public static ReporteCreadoEvent de(UUID reporteId, UUID autorId, Instant ahora) {
        return new ReporteCreadoEvent(UUID.randomUUID(), ahora, reporteId, autorId);
    }

    @Override
    public String type() {
        return "reportes.reporte-creado";
    }

    @Override
    public String aggregateType() {
        return "Reporte";
    }

    @Override
    public Map<String, Object> payload() {
        return Map.of("reporteId", aggregateId, "autorId", actorUserId);
    }
}
