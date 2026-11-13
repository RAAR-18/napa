package co.edu.napa.cuentas.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispositivoTokenJpaRepository extends JpaRepository<DispositivoTokenJpaEntity, UUID> {
    List<DispositivoTokenJpaEntity> findByUsuario_IdOrderByUltimaConexionAtDesc(UUID usuarioId);

    Optional<DispositivoTokenJpaEntity> findByUsuario_IdAndToken(UUID usuarioId, String token);
}
