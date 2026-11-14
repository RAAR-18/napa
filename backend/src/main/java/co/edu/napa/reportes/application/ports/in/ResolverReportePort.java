package co.edu.napa.reportes.application.ports.in;

import co.edu.napa.reportes.domain.model.Reporte;

import java.util.UUID;

/** RF-62. */
public interface ResolverReportePort {
    Reporte resolver(UUID administradorId, UUID reporteId, String nota);
}
