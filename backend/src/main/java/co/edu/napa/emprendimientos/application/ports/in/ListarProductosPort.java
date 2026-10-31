package co.edu.napa.emprendimientos.application.ports.in;

import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.UUID;

public interface ListarProductosPort {
    Resultado listar(int page, int size);

    Resultado listarDelVendedor(UUID usuarioId, int page, int size);

    record Resultado(
            java.util.List<Item> content,
            int page,
            int size,
            long totalElements
    ) {}

    record Item(
            UUID id,
            UUID emprendimientoId,
            String emprendimientoNombre,
            String nombre,
            BigDecimal precio,
            String unidad,
            String descripcion,
            int cantidadDisponible,
            String imagenUrl
    ) {}
}
