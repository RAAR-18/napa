package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.AuthTokens;
import co.edu.napa.cuentas.application.ports.in.IniciarSesionPort;
import co.edu.napa.cuentas.application.ports.out.LoginAttemptPort;
import co.edu.napa.cuentas.application.ports.out.PasswordHashingPort;
import co.edu.napa.cuentas.application.ports.out.SesionRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.TokenServicePort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.domain.model.Celular;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.cuentas.domain.model.Sesion;
import co.edu.napa.cuentas.domain.model.Usuario;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class IniciarSesionUseCase implements IniciarSesionPort {
    private static final String CREDENCIALES_INVALIDAS = "El correo o celular y la contraseña no coinciden.";

    private final UsuarioRepositoryPort usuarios;
    private final SesionRepositoryPort sesiones;
    private final LoginAttemptPort loginAttempts;
    private final PasswordHashingPort passwordHashing;
    private final TokenServicePort tokenService;
    private final Clock clock;
    private final String dummyPasswordHash;

    public IniciarSesionUseCase(
            UsuarioRepositoryPort usuarios,
            SesionRepositoryPort sesiones,
            LoginAttemptPort loginAttempts,
            PasswordHashingPort passwordHashing,
            TokenServicePort tokenService,
            Clock clock
    ) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.sesiones = Objects.requireNonNull(sesiones, "sesiones");
        this.loginAttempts = Objects.requireNonNull(loginAttempts, "loginAttempts");
        this.passwordHashing = Objects.requireNonNull(passwordHashing, "passwordHashing");
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService");
        this.clock = Objects.requireNonNull(clock, "clock");
        // Hash de relleno: si la cuenta no existe se verifica igual contra él, para que
        // responder tarde lo mismo y no revelar si el usuario existe.
        this.dummyPasswordHash = passwordHashing.hash(UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public AuthTokens iniciarSesion(String identificadorInput, String password, String deviceId, String ipAddress) {
        Sesion.validarDeviceId(deviceId);
        String identificador = normalizar(identificadorInput);
        loginAttempts.ensureAllowed(identificador, ipAddress);
        Usuario usuario = buscar(identificador).orElse(null);
        String hashGuardado = usuario == null ? dummyPasswordHash : usuario.passwordHash();
        if (!passwordHashing.matches(password, hashGuardado)
                || usuario == null
                || usuario.estado() == EstadoUsuario.ELIMINADA) {
            loginAttempts.recordFailure(identificador, ipAddress);
            throw new DomainException("INVALID_CREDENTIALS", CREDENCIALES_INVALIDAS);
        }
        if (usuario.estado() != EstadoUsuario.ACTIVA
                || (usuario.rol() != RolUsuario.ADMINISTRADOR && usuario.celularVerificadoAt() == null)) {
            throw new DomainException(
                    "ACCOUNT_NOT_VERIFIED",
                    "Debes verificar tu celular antes de iniciar sesión."
            );
        }
        loginAttempts.clearIdentifier(identificador);

        Instant now = Instant.now(clock);
        String refreshToken = tokenService.newRefreshToken();
        Sesion sesion = Sesion.nueva(
                usuario.id(),
                tokenService.hashRefreshToken(refreshToken),
                deviceId,
                now,
                tokenService.refreshTokenTtl()
        );
        return AuthTokens.desde(tokenService.issue(sesiones.save(sesion), usuario, refreshToken), usuario);
    }

    private Optional<Usuario> buscar(String identificador) {
        return identificador.contains("@")
                ? usuarios.findByCorreo(identificador)
                : usuarios.findByCelular(identificador);
    }

    private String normalizar(String identificador) {
        if (identificador == null || identificador.isBlank()) {
            return "";
        }
        String normalizado = identificador.trim().toLowerCase(Locale.ROOT);
        if (normalizado.contains("@")) {
            return normalizado;
        }
        try {
            return Celular.desde(normalizado).valor();
        } catch (DomainException exception) {
            return normalizado;
        }
    }
}