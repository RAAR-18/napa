package co.edu.napa.cuentas.application.ports.out;

import co.edu.napa.cuentas.domain.model.DispositivoToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispositivoTokenRepositoryPort {
    DispositivoToken save(DispositivoToken dispositivoToken);

    Optional<DispositivoToken> findByUsuarioIdAndToken(UUID usuarioId, String token);

    List<DispositivoToken> findByUsuarioId(UUID usuarioId);
}
