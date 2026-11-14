package co.edu.napa.calificaciones.domain.event;

import co.edu.napa.calificaciones.domain.model.AccionModeracion;
import co.edu.napa.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** E17: el administrador editó o eliminó un comentario; notificaciones avisa al autor con el motivo (RF-06 FR-005). */
public record ComentarioModeradoEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID actorUserId,
        UUID autorId,
        AccionModeracion accion,
        String motivo
) implements DomainEvent {
    public static ComentarioModeradoEvent de(UUID calificacionId, UUID administradorId, UUID autorId,
                                             AccionModeracion accion, String motivo, Instant ahora) {
        return new ComentarioModeradoEvent(UUID.randomUUID(), ahora, calificacionId, administradorId, autorId,
                accion, motivo);
    }

    @Override
    public String type() {
        return "calificaciones.comentario-moderado";
    }

    @Override
    public String aggregateType() {
        return "Calificacion";
    }

    @Override
    public Map<String, Object> payload() {
        return Map.of(
                "calificacionId", aggregateId,
                "autorId", autorId,
                "accion", accion.name(),
                "motivo", motivo
        );
    }
}
