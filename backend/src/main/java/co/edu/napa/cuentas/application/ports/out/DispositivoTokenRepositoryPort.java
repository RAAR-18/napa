package co.edu.napa.cuentas.application.ports.out;

import co.edu.napa.cuentas.domain.model.DispositivoToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispositivoTokenRepositoryPort {
    DispositivoToken save(DispositivoToken dispositivoToken);

    Optional<DispositivoToken> findByUsuarioIdAndToken(UUID usuarioId, String token);

    List<DispositivoToken> findByUsuarioId(UUID usuarioId);

    /** Borra el token de las demás cuentas: un token identifica una instalación, que solo tiene una sesión. */
    void deleteByTokenForOtherUsers(String token, UUID usuarioId);

    void deleteByUsuarioIdAndToken(UUID usuarioId, String token);
}
