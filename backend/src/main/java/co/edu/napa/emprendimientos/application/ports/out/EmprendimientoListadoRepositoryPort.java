package co.edu.napa.emprendimientos.application.ports.out;

import java.util.List;

public interface EmprendimientoListadoRepositoryPort {
    List<EmprendimientoListadoProjection> buscar(
            FiltroEmprendimientos filtro,
            long offset,
            int limit
    );

    long contar(FiltroEmprendimientos filtro);
}
