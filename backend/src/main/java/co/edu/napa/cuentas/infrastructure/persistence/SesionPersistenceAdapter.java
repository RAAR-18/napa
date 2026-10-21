package co.edu.napa.cuentas.infrastructure.persistence;

import co.edu.napa.cuentas.application.ports.out.SesionRepositoryPort;
import co.edu.napa.cuentas.domain.model.Sesion;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SesionPersistenceAdapter implements SesionRepositoryPort {
    private final SesionJpaRepository repository;
    private final UsuarioJpaRepository usuarioRepository;

    public SesionPersistenceAdapter(
            SesionJpaRepository repository,
            UsuarioJpaRepository usuarioRepository
    ) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public Sesion save(Sesion sesion) {
        UsuarioJpaEntity usuario = usuarioRepository.getReferenceById(sesion.usuarioId());
        return repository.save(new SesionJpaEntity(sesion, usuario)).toDomain();
    }

    @Override
    public Optional<Sesion> findByRefreshTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(SesionJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public boolean rotateRefreshToken(
            UUID sessionId,
            String currentTokenHash,
            String nextTokenHash,
            Instant now
    ) {
        return repository.rotateRefreshToken(sessionId, currentTokenHash, nextTokenHash, now) == 1;
    }

    @Override
    @Transactional
    public List<Sesion> revokeOtherSessions(UUID usuarioId, UUID currentSessionId, Instant revokedAt) {
        List<SesionJpaEntity> sessions = repository
                .findByUsuario_IdAndRevokedAtIsNullAndExpiresAtAfterAndIdNot(
                        usuarioId,
                        revokedAt,
                        currentSessionId
                );
        sessions.forEach(session -> session.revoke(revokedAt));
        repository.flush();
        return sessions.stream().map(SesionJpaEntity::toDomain).toList();
    }

    @Override
    @Transactional
    public List<Sesion> revokeAllSessions(UUID usuarioId, Instant revokedAt) {
        List<SesionJpaEntity> sessions = repository
                .findByUsuario_IdAndRevokedAtIsNullAndExpiresAtAfter(usuarioId, revokedAt);
        sessions.forEach(session -> session.revoke(revokedAt));
        repository.flush();
        return sessions.stream().map(SesionJpaEntity::toDomain).toList();
    }

    @Override
    @Transactional
    public Optional<Sesion> revoke(UUID sessionId, Instant revokedAt) {
        return repository.findByIdAndRevokedAtIsNullAndExpiresAtAfter(sessionId, revokedAt)
                .map(session -> {
                    session.revoke(revokedAt);
                    repository.flush();
                    return session.toDomain();
                });
    }
}
