package co.edu.napa.emprendimientos.application.usecase;

import co.edu.napa.emprendimientos.application.ports.out.FiltroEmprendimientos;

public record ListarEmprendimientosQuery(
        FiltroEmprendimientos filtro,
        int page,
        int size
) {
}
