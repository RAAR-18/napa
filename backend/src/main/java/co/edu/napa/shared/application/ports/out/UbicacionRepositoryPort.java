package co.edu.napa.shared.application.ports.out;

import co.edu.napa.shared.domain.geo.Ubicacion;

import java.util.Optional;
import java.util.UUID;

public interface UbicacionRepositoryPort {
    Ubicacion save(Ubicacion ubicacion);

    Optional<Ubicacion> findById(UUID id);
}
