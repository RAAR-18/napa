package co.edu.napa.reportes.application.usecase;

import co.edu.napa.reportes.application.ports.in.ResolverReportePort;
import co.edu.napa.reportes.application.ports.out.ReporteRepositoryPort;
import co.edu.napa.reportes.domain.event.ReporteResueltoEvent;
import co.edu.napa.reportes.domain.model.Reporte;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** RF-62: el administrador cierra el reporte con una nota y el autor recibe E16. */
@Component
public class ResolverReporteUseCase implements ResolverReportePort {
    private final ReporteRepositoryPort reportes;
    private final EventPublisherPort eventos;
    private final Clock clock;

    public ResolverReporteUseCase(ReporteRepositoryPort reportes, EventPublisherPort eventos, Clock clock) {
        this.reportes = Objects.requireNonNull(reportes, "reportes");
        this.eventos = Objects.requireNonNull(eventos, "eventos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public Reporte resolver(UUID administradorId, UUID reporteId, String nota) {
        Objects.requireNonNull(administradorId, "administradorId");
        Objects.requireNonNull(reporteId, "reporteId");
        Reporte actual = reportes.buscarParaActualizar(reporteId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "No encontramos el reporte."));
        Instant ahora = clock.instant();
        Reporte resuelto = actual.resolver(administradorId, nota, ahora);
        reportes.actualizar(resuelto);
        eventos.publish(ReporteResueltoEvent.de(resuelto.id(), administradorId, resuelto.autorId(), ahora));
        return resuelto;
    }
}
