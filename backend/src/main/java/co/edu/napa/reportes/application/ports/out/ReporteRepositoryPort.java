package co.edu.napa.reportes.application.ports.out;

import co.edu.napa.reportes.domain.model.EstadoReporte;
import co.edu.napa.reportes.domain.model.MotivoReporte;
import co.edu.napa.reportes.domain.model.Reporte;
import co.edu.napa.reportes.domain.model.TipoEntidadReportada;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReporteRepositoryPort {
    void insertar(Reporte reporte);

    /** RF-60 Edge 2: un reporte PENDIENTE o EN_REVISION del mismo autor, entidad y motivo. */
    boolean existeAbierto(UUID autorId, MotivoReporte motivo, TipoEntidadReportada tipoEntidad, UUID entidadId);

    Optional<Reporte> buscar(UUID id);

    /** Bloquea la fila hasta el fin de la transacción. */
    Optional<Reporte> buscarParaActualizar(UUID id);

    void actualizar(Reporte reporte);

    /** {@code autorId} y {@code estado} son filtros opcionales. */
    List<Reporte> listar(UUID autorId, EstadoReporte estado, int page, int size, boolean ascendente);

    long contar(UUID autorId, EstadoReporte estado);
}
