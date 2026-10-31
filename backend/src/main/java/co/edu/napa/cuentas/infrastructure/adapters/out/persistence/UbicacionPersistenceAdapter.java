package co.edu.napa.cuentas.infrastructure.adapters.out.persistence;

import co.edu.napa.cuentas.application.ports.out.UbicacionRepositoryPort;
import co.edu.napa.cuentas.domain.model.Ubicacion;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class UbicacionPersistenceAdapter implements UbicacionRepositoryPort {
    private final UbicacionJpaRepository repository;

    public UbicacionPersistenceAdapter(UbicacionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Ubicacion save(Ubicacion ubicacion) {
        return repository.saveAndFlush(new UbicacionJpaEntity(ubicacion)).toDomain();
    }

    @Override
    public Optional<Ubicacion> findById(UUID id) {
        return repository.findById(id).map(UbicacionJpaEntity::toDomain);
    }
}
