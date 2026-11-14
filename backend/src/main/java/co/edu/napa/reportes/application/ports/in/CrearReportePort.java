package co.edu.napa.reportes.application.ports.in;

import co.edu.napa.reportes.domain.model.MotivoReporte;
import co.edu.napa.reportes.domain.model.Reporte;

import java.util.UUID;

/** RF-60. */
public interface CrearReportePort {
    Reporte crear(UUID autorId, String rol, NuevoReporte reporte);

    /** {@code pedidoId}, {@code domicilioId} y {@code usuarioId} son excluyentes: a lo sumo uno. */
    record NuevoReporte(MotivoReporte motivo, String descripcion, UUID pedidoId, UUID domicilioId, UUID usuarioId) {}
}
