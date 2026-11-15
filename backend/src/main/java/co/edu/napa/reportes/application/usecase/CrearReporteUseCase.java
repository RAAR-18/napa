package co.edu.napa.reportes.application.usecase;

import co.edu.napa.reportes.application.ports.in.CrearReportePort;
import co.edu.napa.reportes.application.ports.out.RelacionesReportePort;
import co.edu.napa.reportes.application.ports.out.ReporteRepositoryPort;
import co.edu.napa.reportes.domain.event.ReporteCreadoEvent;
import co.edu.napa.reportes.domain.model.Reporte;
import co.edu.napa.reportes.domain.model.TipoEntidadReportada;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** RF-60: cliente, vendedor o domiciliario reportan un problema; nace PENDIENTE y avisa a los administradores. */
@Component
public class CrearReporteUseCase implements CrearReportePort {
    private static final Set<String> ROLES = Set.of("CLIENTE", "VENDEDOR_AMBULANTE", "VENDEDOR_PUNTO_FIJO", "DOMICILIARIO");
    private static final String NO_ENCONTRADO = "No encontramos lo que quieres reportar.";

    private final ReporteRepositoryPort reportes;
    private final RelacionesReportePort relaciones;
    private final EventPublisherPort eventos;
    private final Clock clock;

    public CrearReporteUseCase(ReporteRepositoryPort reportes, RelacionesReportePort relaciones,
                               EventPublisherPort eventos, Clock clock) {
        this.reportes = Objects.requireNonNull(reportes, "reportes");
        this.relaciones = Objects.requireNonNull(relaciones, "relaciones");
        this.eventos = Objects.requireNonNull(eventos, "eventos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public Reporte crear(UUID autorId, String rol, NuevoReporte datos) {
        Objects.requireNonNull(autorId, "autorId");
        Objects.requireNonNull(datos, "datos");
        if (!ROLES.contains(rol)) {
            throw new DomainException("FORBIDDEN", "No tienes permiso para realizar esta acción.");
        }
        long relacionadas = Stream.of(datos.pedidoId(), datos.domicilioId(), datos.usuarioId())
                .filter(Objects::nonNull).count();
        if (relacionadas > 1) {
            String mensaje = "Relaciona como máximo un pedido, un domicilio o un usuario.";
            throw new DomainException("VALIDATION_ERROR", mensaje, Map.of("fields", Map.of("entidad", mensaje)));
        }
        TipoEntidadReportada tipo = datos.pedidoId() != null ? TipoEntidadReportada.PEDIDO
                : datos.domicilioId() != null ? TipoEntidadReportada.DOMICILIO
                : datos.usuarioId() != null ? TipoEntidadReportada.USUARIO
                : null;
        UUID entidadId = tipo == null ? null : switch (tipo) {
            case PEDIDO -> datos.pedidoId();
            case DOMICILIO -> datos.domicilioId();
            case USUARIO -> datos.usuarioId();
        };
        Instant ahora = clock.instant();
        Reporte reporte = Reporte.nuevo(autorId, datos.motivo(), datos.descripcion(), tipo, entidadId, ahora);

        if (tipo != null && !relacionado(autorId, tipo, entidadId)) {
            throw new DomainException("NOT_FOUND", NO_ENCONTRADO);
        }
        if (reportes.existeAbierto(autorId, reporte.motivo(), tipo, entidadId)) {
            throw new DomainException("CONFLICT", "Ya tienes un reporte abierto sobre esto.");
        }
        reportes.insertar(reporte);
        eventos.publish(ReporteCreadoEvent.de(reporte.id(), autorId, ahora));
        return reporte;
    }

    private boolean relacionado(UUID autorId, TipoEntidadReportada tipo, UUID entidadId) {
        return switch (tipo) {
            case PEDIDO -> relaciones.participaEnPedido(autorId, entidadId);
            case DOMICILIO -> relaciones.participaEnDomicilio(autorId, entidadId);
            case USUARIO -> !autorId.equals(entidadId) && relaciones.compartieronPedido(autorId, entidadId);
        };
    }
}
