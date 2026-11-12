package co.edu.napa.emprendimientos.application.ports.in;

public interface ListarEmprendimientosPort {
    EmprendimientosPagina listar(ListarEmprendimientosQuery query);
}
