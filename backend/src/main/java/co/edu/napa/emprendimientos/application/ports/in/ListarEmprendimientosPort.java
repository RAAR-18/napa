package co.edu.napa.emprendimientos.application.ports.in;

import co.edu.napa.emprendimientos.domain.model.CategoriaEmprendimiento;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public interface ListarEmprendimientosPort {
    Resultado listar(int page, int size);

    record Resultado(List<Item> content, int page, int size, long totalElements) {
    }

    record Item(
            UUID id,
            String nombre,
            CategoriaEmprendimiento categoria,
            String tipoVendedor,
            String descripcion,
            String imagenUrl,
            LocalTime horaApertura,
            LocalTime horaCierre,
            boolean abierto,
            double latitud,
            double longitud,
            String direccion,
            String referencia
    ) {
    }
}
