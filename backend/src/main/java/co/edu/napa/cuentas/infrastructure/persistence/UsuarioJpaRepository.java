package co.edu.napa.cuentas.infrastructure.persistence;

import co.edu.napa.cuentas.domain.model.RolUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioJpaRepository extends JpaRepository<UsuarioJpaEntity, UUID> {
    Optional<UsuarioJpaEntity> findByCelular(String celular);

    Optional<UsuarioJpaEntity> findByCorreoIgnoreCase(String correo);

    boolean existsByCelular(String celular);

    boolean existsByCorreoIgnoreCase(String correo);

    boolean existsByRol(RolUsuario rol);
}
