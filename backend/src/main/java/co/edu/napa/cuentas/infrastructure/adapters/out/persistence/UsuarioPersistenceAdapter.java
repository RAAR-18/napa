package co.edu.napa.cuentas.infrastructure.adapters.out.persistence;

import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.cuentas.domain.model.Usuario;
import co.edu.napa.shared.domain.DomainException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class UsuarioPersistenceAdapter implements UsuarioRepositoryPort {
    private final UsuarioJpaRepository repository;

    public UsuarioPersistenceAdapter(UsuarioJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Usuario save(Usuario usuario) {
        try {
            return repository.saveAndFlush(new UsuarioJpaEntity(usuario)).toDomain();
        } catch (DataIntegrityViolationException exception) {
            if (exception.getCause() instanceof ConstraintViolationException violation) {
                if ("uq_usuario_celular".equals(violation.getConstraintName())) {
                    throw new DomainException("PHONE_ALREADY_REGISTERED", "Ese celular ya está registrado.");
                }
                if ("uq_usuario_correo_normalizado".equals(violation.getConstraintName())) {
                    throw new DomainException("EMAIL_ALREADY_REGISTERED", "Ese correo ya está registrado.");
                }
            }
            throw exception;
        }
    }

    @Override
    public Optional<Usuario> findById(UUID id) {
        return repository.findById(id).map(UsuarioJpaEntity::toDomain);
    }

    @Override
    public Optional<Usuario> findByCelular(String celular) {
        return repository.findByCelular(celular).map(UsuarioJpaEntity::toDomain);
    }

    @Override
    public Optional<Usuario> findByCorreo(String correo) {
        return repository.findByCorreoIgnoreCase(correo).map(UsuarioJpaEntity::toDomain);
    }

    @Override
    public boolean existsByCelular(String celular) {
        return repository.existsByCelular(celular);
    }

    @Override
    public boolean existsByCorreo(String correo) {
        return repository.existsByCorreoIgnoreCase(correo);
    }

    @Override
    public boolean existsByRol(RolUsuario rol) {
        return repository.existsByRol(rol);
    }

    @Override
    public Map<UUID, String> findNamesByIds(Set<UUID> ids) {
        return repository.findAllById(ids).stream()
                .collect(Collectors.toMap(UsuarioJpaEntity::getId, UsuarioJpaEntity::getNombre));
    }
}
