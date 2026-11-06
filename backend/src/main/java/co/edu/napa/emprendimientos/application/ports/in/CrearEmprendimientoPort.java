package co.edu.napa.emprendimientos.application.ports.in;

import co.edu.napa.emprendimientos.application.usecase.CrearEmprendimientoCommand;
import co.edu.napa.emprendimientos.application.usecase.CrearEmprendimientoResult;

public interface CrearEmprendimientoPort {
    CrearEmprendimientoResult crear(CrearEmprendimientoCommand command);
}
