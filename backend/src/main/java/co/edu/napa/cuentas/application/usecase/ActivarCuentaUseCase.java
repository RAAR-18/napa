package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ActivarCuentaPort;
import co.edu.napa.cuentas.application.ports.in.AuthTokens;
import co.edu.napa.cuentas.application.ports.out.SesionRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.TokenServicePort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.domain.model.Celular;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.Sesion;
import co.edu.napa.cuentas.domain.model.Usuario;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Component
public class ActivarCuentaUseCase implements ActivarCuentaPort {
    private final UsuarioRepositoryPort usuarios;
    private final SesionRepositoryPort sesiones;
    private final TokenServicePort tokenService;
    private final Clock clock;

    public ActivarCuentaUseCase(
            UsuarioRepositoryPort usuarios,
            SesionRepositoryPort sesiones,
            TokenServicePort tokenService,
            Clock clock
    ) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.sesiones = Objects.requireNonNull(sesiones, "sesiones");
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public AuthTokens activar(String celularInput, String deviceId) {
        String celular = Celular.desde(celularInput).valor();
        Sesion.validarDeviceId(deviceId);
        Usuario usuario = usuarios.findByCelular(celular)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "No hay una verificación pendiente."));
        if (usuario.estado() != EstadoUsuario.PENDIENTE_VERIFICACION) {
            throw new DomainException("CONFLICT", "La cuenta no está pendiente de verificación.");
        }
        Instant now = Instant.now(clock);
        Usuario activado = usuario.activar(now);
        usuarios.save(activado);

        String refreshToken = tokenService.newRefreshToken();
        Sesion sesion = Sesion.nueva(
                activado.id(),
                tokenService.hashRefreshToken(refreshToken),
                deviceId,
                now,
                tokenService.refreshTokenTtl()
        );
        return AuthTokens.desde(tokenService.issue(sesiones.save(sesion), activado, refreshToken), activado);
    }
}