package co.edu.napa.calificaciones.application.usecase;

import co.edu.napa.calificaciones.application.ports.in.ModerarCalificacionPort;
import co.edu.napa.calificaciones.application.ports.out.CalificacionRepositoryPort;
import co.edu.napa.calificaciones.domain.event.ComentarioModeradoEvent;
import co.edu.napa.calificaciones.domain.model.AccionModeracion;
import co.edu.napa.calificaciones.domain.model.Calificacion;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * RF-06: cada acción deja un registro con el administrador, el motivo y el texto anterior (FR-004) y emite E17 para
 * avisar al autor (FR-005). Solo la eliminación cambia el promedio (FR-003).
 */
@Component
public class ModerarCalificacionUseCase implements ModerarCalificacionPort {
    private final CalificacionRepositoryPort calificaciones;
    private final EventPublisherPort eventos;
    private final Clock clock;

    public ModerarCalificacionUseCase(CalificacionRepositoryPort calificaciones, EventPublisherPort eventos, Clock clock) {
        this.calificaciones = Objects.requireNonNull(calificaciones, "calificaciones");
        this.eventos = Objects.requireNonNull(eventos, "eventos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public Calificacion eliminar(UUID administradorId, UUID calificacionId, String motivo) {
        String motivoValido = Calificacion.validarMotivoModeracion(motivo);
        Calificacion actual = buscar(calificacionId);
        Instant ahora = clock.instant();
        Calificacion eliminada = actual.eliminarPorModeracion(ahora);
        return registrar(administradorId, actual, eliminada, AccionModeracion.ELIMINADO, motivoValido, ahora);
    }

    @Override
    @Transactional
    public Calificacion editarTexto(UUID administradorId, UUID calificacionId, String comentario, String motivo) {
        String motivoValido = Calificacion.validarMotivoModeracion(motivo);
        Calificacion actual = buscar(calificacionId);
        Instant ahora = clock.instant();
        Calificacion editada = actual.moderarTexto(comentario, ahora);
        return registrar(administradorId, actual, editada, AccionModeracion.EDITADO, motivoValido, ahora);
    }

    private Calificacion registrar(UUID administradorId, Calificacion anterior, Calificacion nueva,
                                   AccionModeracion accion, String motivo, Instant ahora) {
        Objects.requireNonNull(administradorId, "administradorId");
        calificaciones.actualizar(nueva);
        calificaciones.registrarModeracion(nueva.id(), administradorId, accion, motivo, anterior.comentario(), ahora);
        eventos.publish(ComentarioModeradoEvent.de(nueva.id(), administradorId, nueva.autorId(), accion, motivo, ahora));
        return nueva;
    }

    private Calificacion buscar(UUID calificacionId) {
        Objects.requireNonNull(calificacionId, "calificacionId");
        return calificaciones.buscarParaActualizar(calificacionId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "No encontramos esa calificación."));
    }
}
