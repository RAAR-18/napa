package co.edu.napa.cuentas.infrastructure.adapters.out.persistence;

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

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query(
            "delete from UsuarioJpaEntity u where u.id = :id"
                    + " and u.estado = co.edu.napa.cuentas.domain.model.EstadoUsuario.PENDIENTE_VERIFICACION")
    int deleteSinVerificar(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query(
            "delete from UsuarioJpaEntity u where u.createdAt < :limite"
                    + " and u.estado = co.edu.napa.cuentas.domain.model.EstadoUsuario.PENDIENTE_VERIFICACION")
    int deleteSinVerificarAntesDe(@org.springframework.data.repository.query.Param("limite") java.time.Instant limite);
}
