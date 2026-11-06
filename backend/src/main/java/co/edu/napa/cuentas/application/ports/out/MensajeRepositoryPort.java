package co.edu.napa.cuentas.application.ports.out;

import co.edu.napa.cuentas.domain.model.Mensaje;

import java.util.Optional;
import java.util.UUID;

public interface MensajeRepositoryPort {
    Mensaje save(Mensaje mensaje);

    Optional<Mensaje> findById(UUID id);
}