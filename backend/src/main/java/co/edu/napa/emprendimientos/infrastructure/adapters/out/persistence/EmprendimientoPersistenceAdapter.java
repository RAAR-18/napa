package co.edu.napa.emprendimientos.infrastructure.adapters.out.persistence;

import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoRepositoryPort;
import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoRepositoryPort.EmprendimientoListRow;
import co.edu.napa.emprendimientos.domain.model.Emprendimiento;
import co.edu.napa.emprendimientos.domain.model.EstadoEmprendimiento;
import co.edu.napa.shared.domain.DomainException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public class EmprendimientoPersistenceAdapter implements EmprendimientoRepositoryPort {
    private final EmprendimientoJpaRepository repository;
    private final UsuarioRepositoryPort usuarios;

    public EmprendimientoPersistenceAdapter(
            EmprendimientoJpaRepository repository,
            UsuarioRepositoryPort usuarios
    ) {
        this.repository = repository;
        this.usuarios = usuarios;
    }

    @Override
    public Emprendimiento save(Emprendimiento emprendimiento) {
        try {
            EmprendimientoJpaEntity saved = repository.saveAndFlush(new EmprendimientoJpaEntity(emprendimiento));
            return saved.toDomain(tipoVendedor(emprendimiento.usuarioId()));
        } catch (DataIntegrityViolationException exception) {
            if (exception.getCause() instanceof ConstraintViolationException violation
                    && "uq_emprendimiento_activo_por_usuario".equals(violation.getConstraintName())) {
                throw new DomainException("CONFLICT", "Ya tienes un emprendimiento activo.");
            }
            throw exception;
        }
    }

    @Override
    public Optional<Emprendimiento> findActivoByUsuarioId(UUID usuarioId) {
        return repository.findByUsuarioIdAndEstado(usuarioId, EstadoEmprendimiento.ACTIVO)
                .map(entity -> entity.toDomain(tipoVendedor(usuarioId)));
    }

    @Override
    public Page<EmprendimientoListRow> findActivos(Pageable pageable) {
        return repository.findActivos(pageable);
    }

    private co.edu.napa.cuentas.domain.model.RolUsuario tipoVendedor(UUID usuarioId) {
        return usuarios.findById(usuarioId)
                .map(user -> user.rol())
                .orElseThrow(() -> new DomainException("UNAUTHORIZED", "El propietario del emprendimiento no existe."));
    }
}
