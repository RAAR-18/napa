package co.edu.napa.cuentas.infrastructure.persistence;

import co.edu.napa.cuentas.application.ports.out.DispositivoTokenRepositoryPort;
import co.edu.napa.cuentas.domain.model.DispositivoToken;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DispositivoTokenPersistenceAdapter implements DispositivoTokenRepositoryPort {
    private final DispositivoTokenJpaRepository repository;
    private final UsuarioJpaRepository usuarioRepository;

    public DispositivoTokenPersistenceAdapter(
            DispositivoTokenJpaRepository repository,
            UsuarioJpaRepository usuarioRepository
    ) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public DispositivoToken save(DispositivoToken dispositivoToken) {
        UsuarioJpaEntity usuario = usuarioRepository.getReferenceById(dispositivoToken.usuarioId());
        return repository.saveAndFlush(new DispositivoTokenJpaEntity(dispositivoToken, usuario)).toDomain();
    }

    @Override
    public Optional<DispositivoToken> findByUsuarioIdAndToken(UUID usuarioId, String token) {
        return repository.findByUsuario_IdAndToken(usuarioId, token).map(DispositivoTokenJpaEntity::toDomain);
    }

    @Override
    public List<DispositivoToken> findByUsuarioId(UUID usuarioId) {
        return repository.findByUsuario_IdOrderByUltimaConexionAtDesc(usuarioId)
                .stream()
                .map(DispositivoTokenJpaEntity::toDomain)
                .toList();
    }
}
