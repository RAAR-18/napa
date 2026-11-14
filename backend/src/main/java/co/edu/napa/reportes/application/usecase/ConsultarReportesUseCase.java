package co.edu.napa.reportes.application.usecase;

import co.edu.napa.reportes.application.ports.in.ConsultarReportesPort;
import co.edu.napa.reportes.application.ports.out.RelacionesReportePort;
import co.edu.napa.reportes.application.ports.out.ReporteRepositoryPort;
import co.edu.napa.reportes.domain.model.EstadoReporte;
import co.edu.napa.reportes.domain.model.Reporte;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class ConsultarReportesUseCase implements ConsultarReportesPort {
    private final ReporteRepositoryPort reportes;
    private final RelacionesReportePort relaciones;

    public ConsultarReportesUseCase(ReporteRepositoryPort reportes, RelacionesReportePort relaciones) {
        this.reportes = Objects.requireNonNull(reportes, "reportes");
        this.relaciones = Objects.requireNonNull(relaciones, "relaciones");
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina listarTodos(EstadoReporte estado, int page, int size, boolean ascendente) {
        return pagina(null, estado, page, size, ascendente);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina listarPropios(UUID autorId, EstadoReporte estado, int page, int size, boolean ascendente) {
        return pagina(Objects.requireNonNull(autorId, "autorId"), estado, page, size, ascendente);
    }

    @Override
    @Transactional(readOnly = true)
    public Detalle detalle(UUID reporteId) {
        Reporte reporte = reportes.buscar(reporteId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "No encontramos el reporte."));
        Persona resueltoPor = reporte.resueltoPorId() == null ? null : persona(reporte.resueltoPorId());
        return new Detalle(reporte.id(), reporte.estado().name(), reporte.motivo().name(), reporte.descripcion(),
                persona(reporte.autorId()), reporte.createdAt(), reporte.updatedAt(), reporte.notaResolucion(),
                resueltoPor, entidad(reporte));
    }

    private Pagina pagina(UUID autorId, EstadoReporte estado, int page, int size, boolean ascendente) {
        List<Item> content = reportes.listar(autorId, estado, page, size, ascendente).stream()
                .map(r -> new Item(r.id(), r.estado().name(), r.motivo().name(), persona(r.autorId()),
                        r.tipoEntidad() == null ? null : new Entidad(r.tipoEntidad().name(), r.entidadId()),
                        r.createdAt()))
                .toList();
        return new Pagina(content, page, size, reportes.contar(autorId, estado));
    }

    private EntidadDetalle entidad(Reporte reporte) {
        if (reporte.tipoEntidad() == null) {
            return null;
        }
        Optional<Map<String, Object>> resumen = switch (reporte.tipoEntidad()) {
            case PEDIDO -> relaciones.resumenPedido(reporte.entidadId());
            case DOMICILIO -> relaciones.resumenDomicilio(reporte.entidadId());
            case USUARIO -> relaciones.usuario(reporte.entidadId()).map(u -> {
                Map<String, Object> datos = new LinkedHashMap<>();
                datos.put("nombre", u.nombre());
                datos.put("rol", u.rol());
                datos.put("estado", u.estado());
                return datos;
            });
        };
        return new EntidadDetalle(reporte.tipoEntidad().name(), reporte.entidadId(), resumen.isPresent(),
                resumen.orElse(null));
    }

    private Persona persona(UUID usuarioId) {
        return relaciones.usuario(usuarioId)
                .map(u -> new Persona(u.id(), u.nombre(), u.rol()))
                .orElseGet(() -> new Persona(usuarioId, null, null));
    }
}
