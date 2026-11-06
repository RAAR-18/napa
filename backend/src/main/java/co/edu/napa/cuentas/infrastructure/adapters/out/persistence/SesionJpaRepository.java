package co.edu.napa.cuentas.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SesionJpaRepository extends JpaRepository<SesionJpaEntity, UUID> {
    Optional<SesionJpaEntity> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update SesionJpaEntity session
            set session.tokenHash = :nextTokenHash
            where session.id = :sessionId
              and session.tokenHash = :currentTokenHash
              and session.revokedAt is null
              and session.expiresAt > :now
            """)
    int rotateRefreshToken(
            @Param("sessionId") UUID sessionId,
            @Param("currentTokenHash") String currentTokenHash,
            @Param("nextTokenHash") String nextTokenHash,
            @Param("now") Instant now
    );

    List<SesionJpaEntity> findByUsuario_IdAndRevokedAtIsNullAndExpiresAtAfterAndIdNot(
            UUID usuarioId,
            Instant now,
            UUID currentSessionId
    );

    List<SesionJpaEntity> findByUsuario_IdAndRevokedAtIsNullAndExpiresAtAfter(
            UUID usuarioId,
            Instant now
    );

    Optional<SesionJpaEntity> findByIdAndRevokedAtIsNullAndExpiresAtAfter(UUID id, Instant now);
}
