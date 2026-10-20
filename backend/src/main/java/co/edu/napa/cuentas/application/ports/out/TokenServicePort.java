package co.edu.napa.cuentas.application.ports.out;

import co.edu.napa.cuentas.domain.model.Sesion;
import co.edu.napa.cuentas.domain.model.Usuario;

import java.time.Duration;

public interface TokenServicePort {
    IssuedTokens issue(Sesion session, Usuario user, String rawRefreshToken);

    String newRefreshToken();

    String hashRefreshToken(String refreshToken);

    Duration refreshTokenTtl();
}
