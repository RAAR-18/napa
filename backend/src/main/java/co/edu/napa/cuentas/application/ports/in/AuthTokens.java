package co.edu.napa.cuentas.application.ports.in;

import co.edu.napa.cuentas.application.ports.out.IssuedTokens;
import co.edu.napa.cuentas.domain.model.Usuario;

public record AuthTokens(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType,
        String rol
) {
    public static AuthTokens desde(IssuedTokens emitidos, Usuario usuario) {
        return new AuthTokens(
                emitidos.accessToken(),
                emitidos.refreshToken(),
                emitidos.expiresIn(),
                "Bearer",
                usuario.rol().name()
        );
    }
}