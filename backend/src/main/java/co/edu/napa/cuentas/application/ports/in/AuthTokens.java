package co.edu.napa.cuentas.application.ports.in;

import co.edu.napa.cuentas.application.ports.out.IssuedTokens;
import co.edu.napa.cuentas.domain.model.Usuario;

import java.util.UUID;

/** Respuesta común de login, refresh, cambio y recuperación de contraseña y confirmación del celular. */
public record AuthTokens(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UsuarioResumen usuario
) {
    public static AuthTokens desde(IssuedTokens emitidos, Usuario usuario) {
        return new AuthTokens(
                emitidos.accessToken(),
                emitidos.refreshToken(),
                emitidos.expiresIn(),
                new UsuarioResumen(usuario.id(), usuario.nombre(), usuario.rol().name())
        );
    }

    public record UsuarioResumen(UUID id, String nombre, String rol) {
    }
}