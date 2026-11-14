package co.edu.napa.cuentas.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispositivoTokenJpaRepository extends JpaRepository<DispositivoTokenJpaEntity, UUID> {
    List<DispositivoTokenJpaEntity> findByUsuario_IdOrderByUltimaConexionAtDesc(UUID usuarioId);

    Optional<DispositivoTokenJpaEntity> findByUsuario_IdAndToken(UUID usuarioId, String token);

    @Modifying
    @Query("DELETE FROM DispositivoTokenJpaEntity d WHERE d.token = :token AND d.usuario.id <> :usuarioId")
    void deleteByTokenForOtherUsers(@Param("token") String token, @Param("usuarioId") UUID usuarioId);

    @Modifying
    @Query("DELETE FROM DispositivoTokenJpaEntity d WHERE d.token = :token AND d.usuario.id = :usuarioId")
    void deleteByUsuarioIdAndToken(@Param("usuarioId") UUID usuarioId, @Param("token") String token);
}
