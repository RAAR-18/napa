package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.AuthTokens;
import co.edu.napa.cuentas.application.ports.in.ConfirmarRecuperacionPort;
import co.edu.napa.cuentas.application.ports.in.ConfirmarVerificacionPort;
import co.edu.napa.cuentas.application.ports.out.PasswordHashingPort;
import co.edu.napa.cuentas.application.ports.out.SesionRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.SessionVersionPort;
import co.edu.napa.cuentas.application.ports.out.TokenServicePort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.domain.model.Celular;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.cuentas.domain.model.PasswordPolicy;
import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.cuentas.domain.model.Sesion;
import co.edu.napa.cuentas.domain.model.Usuario;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Component
public class ConfirmarRecuperacionUseCase implements ConfirmarRecuperacionPort {
    private final UsuarioRepositoryPort usuarios;
    private final SesionRepositoryPort sesiones;
    private final SessionVersionPort sessionVersions;
    private final ConfirmarVerificacionPort confirmarVerificacion;
    private final PasswordHashingPort passwordHashing;
    private final TokenServicePort tokenService;
    private final Clock clock;

    public ConfirmarRecuperacionUseCase(
            UsuarioRepositoryPort usuarios,
            SesionRepositoryPort sesiones,
            SessionVersionPort sessionVersions,
            ConfirmarVerificacionPort confirmarVerificacion,
            PasswordHashingPort passwordHashing,
            TokenServicePort tokenService,
            Clock clock
    ) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.sesiones = Objects.requireNonNull(sesiones, "sesiones");
        this.sessionVersions = Objects.requireNonNull(sessionVersions, "sessionVersions");
        this.confirmarVerificacion = Objects.requireNonNull(confirmarVerificacion, "confirmarVerificacion");
        this.passwordHashing = Objects.requireNonNull(passwordHashing, "passwordHashing");
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public AuthTokens confirmar(String celularInput, String codigo, String passwordNueva, String deviceId) {
        String celular = Celular.desde(celularInput).valor();
        PasswordPolicy.validar(passwordNueva);
        Sesion.validarDeviceId(deviceId);
        confirmarVerificacion.confirmar(celular, PropositoVerificacion.RECUPERACION, codigo);
        Usuario usuario = usuarios.findByCelular(celular)
                .filter(cuenta -> cuenta.estado() == EstadoUsuario.ACTIVA)
                .filter(cuenta -> cuenta.rol() != RolUsuario.ADMINISTRADOR)
                .orElseThrow(() -> new DomainException(
                        "VALIDATION_ERROR",
                        "No fue posible recuperar la contraseña."
                ));

        Instant now = Instant.now(clock);
        Usuario actualizado = usuario.cambiarContrasena(passwordHashing.hash(passwordNueva), now);
        usuarios.save(actualizado);
        revocar(sesiones.revokeAllSessions(usuario.id(), now), now);

        String refreshToken = tokenService.newRefreshToken();
        Sesion sesion = Sesion.nueva(
                actualizado.id(),
                tokenService.hashRefreshToken(refreshToken),
                deviceId,
                now,
                tokenService.refreshTokenTtl()
        );
        return AuthTokens.desde(tokenService.issue(sesiones.save(sesion), actualizado, refreshToken), actualizado);
    }

    private void revocar(List<Sesion> revocadas, Instant now) {
        for (Sesion sesion : revocadas) {
            sessionVersions.revoke(
                    sesion.id(),
                    sesion.sessionVersion(),
                    Duration.between(now, sesion.expiresAt())
            );
        }
    }
}