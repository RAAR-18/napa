package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.AuthTokens;
import co.edu.napa.cuentas.application.ports.in.RefrescarSesionPort;
import co.edu.napa.cuentas.application.ports.out.SesionRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.TokenServicePort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
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
public class RefrescarSesionUseCase implements RefrescarSesionPort {
    private static final String REFRESH_INVALIDO = "La sesión de renovación no es válida.";

    private final UsuarioRepositoryPort usuarios;
    private final SesionRepositoryPort sesiones;
    private final TokenServicePort tokenService;
    private final Clock clock;

    public RefrescarSesionUseCase(
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
    public AuthTokens refrescar(String refreshToken, String deviceId) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new DomainException("UNAUTHORIZED", REFRESH_INVALIDO);
        }
        Sesion.validarDeviceId(deviceId);
        String hashActual = tokenService.hashRefreshToken(refreshToken);
        Sesion sesion = sesiones.findByRefreshTokenHash(hashActual)
                .orElseThrow(() -> new DomainException("UNAUTHORIZED", REFRESH_INVALIDO));
        Instant now = Instant.now(clock);
        if (!sesion.estaActiva(now)) {
            throw new DomainException("TOKEN_EXPIRED", "La sesión venció o fue revocada. Inicia sesión de nuevo.");
        }
        if (!sesion.deviceId().equals(deviceId)) {
            throw new DomainException("UNAUTHORIZED", REFRESH_INVALIDO);
        }
        Usuario usuario = usuarios.findById(sesion.usuarioId())
                .filter(cuenta -> cuenta.estado() == EstadoUsuario.ACTIVA)
                .orElseThrow(() -> new DomainException("UNAUTHORIZED", "La cuenta no está activa."));
        String siguienteRefreshToken = tokenService.newRefreshToken();
        String siguienteHash = tokenService.hashRefreshToken(siguienteRefreshToken);
        if (!sesiones.rotateRefreshToken(sesion.id(), hashActual, siguienteHash, now)) {
            throw new DomainException("UNAUTHORIZED", "La sesión de renovación ya fue utilizada.");
        }
        Sesion rotada = sesion.rotarRefreshToken(siguienteHash);
        return AuthTokens.desde(tokenService.issue(rotada, usuario, siguienteRefreshToken), usuario);
    }
}