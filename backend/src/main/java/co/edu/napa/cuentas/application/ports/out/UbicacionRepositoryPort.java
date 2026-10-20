package co.edu.napa.cuentas.application.ports.out;

import co.edu.napa.cuentas.domain.model.Ubicacion;

import java.util.Optional;
import java.util.UUID;

public interface UbicacionRepositoryPort {
    Ubicacion save(Ubicacion ubicacion);

    Optional<Ubicacion> findById(UUID id);
}
