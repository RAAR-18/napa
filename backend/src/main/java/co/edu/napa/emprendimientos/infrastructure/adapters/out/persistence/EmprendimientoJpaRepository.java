package co.edu.napa.emprendimientos.infrastructure.adapters.out.persistence;

import co.edu.napa.emprendimientos.domain.model.EstadoEmprendimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmprendimientoJpaRepository extends JpaRepository<EmprendimientoJpaEntity, UUID> {
    boolean existsByUsuarioIdAndEstado(UUID usuarioId, EstadoEmprendimiento estado);
}
