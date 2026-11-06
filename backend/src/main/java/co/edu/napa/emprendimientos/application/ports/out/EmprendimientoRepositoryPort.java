package co.edu.napa.emprendimientos.application.ports.out;

import co.edu.napa.emprendimientos.domain.model.Emprendimiento;

import java.util.UUID;

public interface EmprendimientoRepositoryPort {
    boolean existsActiveByUsuarioId(UUID usuarioId);

    Emprendimiento save(Emprendimiento emprendimiento);
}
