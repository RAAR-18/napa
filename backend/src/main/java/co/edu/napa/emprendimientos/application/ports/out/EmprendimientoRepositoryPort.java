package co.edu.napa.emprendimientos.application.ports.out;

import co.edu.napa.emprendimientos.domain.model.Emprendimiento;

import java.util.UUID;
import java.util.Optional;

public interface EmprendimientoRepositoryPort {
    boolean existsActiveByUsuarioId(UUID usuarioId);

    Emprendimiento save(Emprendimiento emprendimiento);

    Optional<Emprendimiento> findActiveByUsuarioId(UUID usuarioId);

    Optional<Emprendimiento> findActiveById(UUID id);
}
