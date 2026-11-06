package co.edu.napa.emprendimientos.application.usecase;

import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.emprendimientos.domain.model.Emprendimiento;
import co.edu.napa.shared.domain.geo.Ubicacion;

public record CrearEmprendimientoResult(
        Emprendimiento emprendimiento,
        Ubicacion ubicacion,
        RolUsuario tipoVendedor
) {
}
