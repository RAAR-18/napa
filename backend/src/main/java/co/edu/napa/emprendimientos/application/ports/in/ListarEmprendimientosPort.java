package co.edu.napa.emprendimientos.application.ports.in;

import co.edu.napa.emprendimientos.application.usecase.EmprendimientosPagina;
import co.edu.napa.emprendimientos.application.usecase.ListarEmprendimientosQuery;

public interface ListarEmprendimientosPort {
    EmprendimientosPagina listar(ListarEmprendimientosQuery query);
}
