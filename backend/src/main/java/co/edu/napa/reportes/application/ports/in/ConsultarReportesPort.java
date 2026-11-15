package co.edu.napa.reportes.application.ports.in;

import co.edu.napa.reportes.domain.model.EstadoReporte;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF-61 (administrador) y RF-60 Edge 3 (el autor ve el estado de sus reportes). */
public interface ConsultarReportesPort {
    Pagina listarTodos(EstadoReporte estado, int page, int size, boolean ascendente);

    Pagina listarPropios(UUID autorId, EstadoReporte estado, int page, int size, boolean ascendente);

    Detalle detalle(UUID reporteId);

    record Pagina(List<Item> content, int page, int size, long totalElements) {
        public Pagina {
            content = List.copyOf(content);
        }
    }

    record Item(UUID id, String estado, String motivo, Persona autor, Entidad entidad, Instant createdAt) {}

    record Persona(UUID id, String nombre, String rol) {}

    record Entidad(String tipo, UUID id) {}

    record Detalle(
            UUID id,
            String estado,
            String motivo,
            String descripcion,
            Persona autor,
            Instant createdAt,
            Instant updatedAt,
            String notaResolucion,
            Persona resueltoPor,
            EntidadDetalle entidad
    ) {}

    /** {@code resumen} depende del tipo: PEDIDO, DOMICILIO o USUARIO (plan de RF-61). */
    record EntidadDetalle(String tipo, UUID id, boolean disponible, Map<String, Object> resumen) {}
}
