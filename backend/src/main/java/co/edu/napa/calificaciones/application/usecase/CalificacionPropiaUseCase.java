package co.edu.napa.calificaciones.application.usecase;

import co.edu.napa.calificaciones.application.ports.in.CalificacionPropiaPort;
import co.edu.napa.calificaciones.application.ports.out.CalificacionRepositoryPort;
import co.edu.napa.calificaciones.domain.model.Calificacion;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** RF-04 (editar) y RF-05 (eliminar): solo el autor, y solo mientras la calificación está ACTIVA. */
@Component
public class CalificacionPropiaUseCase implements CalificacionPropiaPort {
    private final CalificacionRepositoryPort calificaciones;
    private final Clock clock;

    public CalificacionPropiaUseCase(CalificacionRepositoryPort calificaciones, Clock clock) {
        this.calificaciones = Objects.requireNonNull(calificaciones, "calificaciones");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public Calificacion editar(UUID autorId, UUID calificacionId, Integer puntaje, String comentario) {
        if (puntaje == null && comentario == null) {
            throw new DomainException("VALIDATION_ERROR", "Envía el puntaje, el comentario o ambos.",
                    Map.of("fields", Map.of("puntaje", "Obligatorio si no envías comentario")));
        }
        Calificacion actual = propia(autorId, calificacionId, "Solo puedes editar tus calificaciones.");
        Calificacion editada = actual.editar(puntaje, comentario, clock.instant());
        if (editada != actual) {
            calificaciones.actualizar(editada);
        }
        return editada;
    }

    @Override
    @Transactional
    public Calificacion eliminar(UUID autorId, UUID calificacionId) {
        Calificacion actual = propia(autorId, calificacionId, "Solo puedes eliminar tus calificaciones.");
        Calificacion eliminada = actual.eliminarPorAutor(clock.instant());
        calificaciones.actualizar(eliminada);
        return eliminada;
    }

    private Calificacion propia(UUID autorId, UUID calificacionId, String mensajeAjena) {
        Objects.requireNonNull(autorId, "autorId");
        Objects.requireNonNull(calificacionId, "calificacionId");
        Calificacion calificacion = calificaciones.buscarParaActualizar(calificacionId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "No encontramos la calificación."));
        if (!calificacion.autorId().equals(autorId)) {
            throw new DomainException("FORBIDDEN", mensajeAjena);
        }
        return calificacion;
    }
}
