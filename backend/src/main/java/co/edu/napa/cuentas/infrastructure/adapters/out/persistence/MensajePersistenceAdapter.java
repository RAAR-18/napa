package co.edu.napa.cuentas.infrastructure.adapters.out.persistence;

import co.edu.napa.cuentas.application.ports.out.MensajeRepositoryPort;
import co.edu.napa.cuentas.domain.model.Mensaje;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class MensajePersistenceAdapter implements MensajeRepositoryPort {
    private final MensajeJpaRepository repository;

    public MensajePersistenceAdapter(MensajeJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mensaje save(Mensaje mensaje) {
        return repository.saveAndFlush(new MensajeJpaEntity(mensaje)).toDomain();
    }

    @Override
    public Optional<Mensaje> findById(UUID id) {
        return repository.findById(id).map(MensajeJpaEntity::toDomain);
    }
}