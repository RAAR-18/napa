package co.edu.napa.reportes.domain.event;

import co.edu.napa.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** E16: el administrador cerró el reporte; notificaciones avisa al autor. */
public record ReporteResueltoEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID actorUserId,
        UUID autorId
) implements DomainEvent {
    public static ReporteResueltoEvent de(UUID reporteId, UUID administradorId, UUID autorId, Instant ahora) {
        return new ReporteResueltoEvent(UUID.randomUUID(), ahora, reporteId, administradorId, autorId);
    }

    @Override
    public String type() {
        return "reportes.reporte-resuelto";
    }

    @Override
    public String aggregateType() {
        return "Reporte";
    }

    @Override
    public Map<String, Object> payload() {
        return Map.of("reporteId", aggregateId, "autorId", autorId);
    }
}
