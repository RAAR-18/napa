package co.edu.napa.emprendimientos.application.ports.in;

import java.util.List;

public record EmprendimientosPagina(
        List<EmprendimientoListadoItem> content,
        int page,
        int size,
        long totalElements
) {
    public EmprendimientosPagina {
        content = List.copyOf(content);
    }
}
