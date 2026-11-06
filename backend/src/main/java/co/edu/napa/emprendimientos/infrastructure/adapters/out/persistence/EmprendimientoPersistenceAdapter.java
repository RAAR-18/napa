package co.edu.napa.emprendimientos.infrastructure.adapters.out.persistence;

import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoRepositoryPort;
import co.edu.napa.emprendimientos.domain.model.Emprendimiento;
import co.edu.napa.emprendimientos.domain.model.EstadoEmprendimiento;
import co.edu.napa.shared.domain.DomainException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class EmprendimientoPersistenceAdapter implements EmprendimientoRepositoryPort {
    private static final String ACTIVE_BUSINESS_CONSTRAINT = "uq_emprendimiento_activo_por_usuario";

    private final EmprendimientoJpaRepository repository;

    public EmprendimientoPersistenceAdapter(EmprendimientoJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsActiveByUsuarioId(UUID usuarioId) {
        return repository.existsByUsuarioIdAndEstado(usuarioId, EstadoEmprendimiento.ACTIVO);
    }

    @Override
    public Emprendimiento save(Emprendimiento emprendimiento) {
        try {
            return repository.saveAndFlush(new EmprendimientoJpaEntity(emprendimiento)).toDomain();
        } catch (DataIntegrityViolationException exception) {
            if (exception.getCause() instanceof ConstraintViolationException violation
                    && ACTIVE_BUSINESS_CONSTRAINT.equals(violation.getConstraintName())) {
                throw new DomainException("CONFLICT", "Ya tienes un emprendimiento activo.");
            }
            throw exception;
        }
    }
}
