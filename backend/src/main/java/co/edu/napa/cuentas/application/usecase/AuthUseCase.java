package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.out.LoginAttemptPort;
import co.edu.napa.cuentas.application.ports.out.IssuedTokens;
import co.edu.napa.cuentas.application.ports.out.PasswordHashingPort;
import co.edu.napa.cuentas.application.ports.out.SesionRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.SessionVersionPort;
import co.edu.napa.cuentas.application.ports.out.TokenServicePort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.VerificationRateLimitPort;
import co.edu.napa.cuentas.application.ports.out.VerificacionResultado;
import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.EstadoVerificacion;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class AuthUseCase {
    private static final Pattern E164_PHONE = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final Pattern COLOMBIAN_MOBILE = Pattern.compile("^3[0-9]{9}$");
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final String INVALID_CREDENTIALS = "El correo o celular y la contraseña no coinciden.";

    private final UsuarioRepositoryPort usuarios;
    private final SesionRepositoryPort sesiones;
    private final VerificarCelularPort verificarCelular;
    private final VerificationRateLimitPort verificationRateLimits;
    private final LoginAttemptPort loginAttempts;
    private final SessionVersionPort sessionVersions;
    private final PasswordHashingPort passwordHashing;
    private final TokenServicePort tokenService;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthUseCase(
            UsuarioRepositoryPort usuarios,
            SesionRepositoryPort sesiones,
            VerificarCelularPort verificarCelular,
            VerificationRateLimitPort verificationRateLimits,
            LoginAttemptPort loginAttempts,
            SessionVersionPort sessionVersions,
            PasswordHashingPort passwordHashing,
            TokenServicePort tokenService,
            Clock clock
    ) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.sesiones = Objects.requireNonNull(sesiones, "sesiones");
        this.verificarCelular = Objects.requireNonNull(verificarCelular, "verificarCelular");
        this.verificationRateLimits = Objects.requireNonNull(
                verificationRateLimits,
                "verificationRateLimits"
        );
        this.loginAttempts = Objects.requireNonNull(loginAttempts, "loginAttempts");
        this.sessionVersions = Objects.requireNonNull(sessionVersions, "sessionVersions");
        this.passwordHashing = Objects.requireNonNull(passwordHashing, "passwordHashing");
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.dummyPasswordHash = passwordHashing.hash(UUID.randomUUID().toString());
    }

    @Transactional
    public RegistroResultado registrar(
            String nombre,
            String celularInput,
            String correoInput,
            String password,
            String confirmacionPassword,
            RolUsuario rol,
            String deviceId,
            String ipAddress
    ) {
        String celular = normalizarCelular(celularInput);
        String correo = normalizarCorreo(correoInput);
        validarNombre(nombre);
        validarPassword(password);
        if (!Objects.equals(password, confirmacionPassword)) {
            throw new DomainException(
                    "VALIDATION_ERROR",
                    "La contraseña y su confirmación deben coincidir.",
                    Map.of("field", "confirmacionPassword")
            );
        }
        if (rol == null || rol == RolUsuario.ADMINISTRADOR) {
            throw new DomainException("VALIDATION_ERROR", "Selecciona un rol válido para registrarte.");
        }
        validarDeviceId(deviceId);
        if (usuarios.existsByCelular(celular) || (correo != null && usuarios.existsByCorreo(correo))) {
            throw new DomainException("CONFLICT", "El celular o correo ya está registrado.");
        }
        verificationRateLimits.checkAndRecord(celular, ipAddress);

        Instant now = Instant.now(clock);
        Usuario usuario = new Usuario(
                UUID.randomUUID(),
                nombre.trim(),
                celular,
                correo,
                passwordHashing.hash(password),
                rol,
                EstadoUsuario.PENDIENTE_VERIFICACION,
                null,
                now,
                now
        );
        usuarios.save(usuario);
        verificarCelular.solicitar(celular, EstadoVerificacion.REGISTRO, ipAddress);
        return new RegistroResultado(usuario.id(), usuario.estado().name());
    }

    public void solicitarVerificacion(String celularInput, EstadoVerificacion estado, String ipAddress) {
        String celular = normalizarCelular(celularInput);
        if (estado == null) {
            throw new DomainException("VALIDATION_ERROR", "El propósito de la verificación es obligatorio.");
        }
        if (estado == EstadoVerificacion.REGISTRO) {
            Usuario usuario = usuarios.findByCelular(celular)
                    .orElseThrow(() -> new DomainException("NOT_FOUND", "No se encontró una cuenta pendiente."));
            if (usuario.estado() != EstadoUsuario.PENDIENTE_VERIFICACION) {
                throw new DomainException("CONFLICT", "La cuenta no está pendiente de verificación.");
            }
        }
        verificationRateLimits.checkAndRecord(celular, ipAddress);
        verificarCelular.solicitar(celular, estado, ipAddress);
    }

    @Transactional
    public AuthTokens confirmarRegistro(
            String celularInput,
            String codigo,
            String deviceId
    ) {
        String celular = normalizarCelular(celularInput);
        validarDeviceId(deviceId);
        Usuario usuario = usuarios.findByCelular(celular)
                .orElseThrow(() -> new DomainException("VALIDATION_ERROR", "El código no es válido o venció."));
        if (usuario.estado() != EstadoUsuario.PENDIENTE_VERIFICACION) {
            throw new DomainException("CONFLICT", "La cuenta no está pendiente de verificación.");
        }
        VerificacionResultado resultado = verificarCelular.confirmar(
                celular,
                EstadoVerificacion.REGISTRO,
                codigo
        );
        if (resultado.estado() != VerificacionResultado.Estado.APROBADA) {
            String message = switch (resultado.estado()) {
                case VENCIDA -> "El código venció. Solicita uno nuevo.";
                case AGOTADA -> "Se agotaron los intentos. Solicita un código nuevo.";
                case INCORRECTA -> "El código no es válido.";
                case APROBADA -> throw new IllegalStateException("Approved result should not be rejected");
            };
            throw new DomainException(
                    "VALIDATION_ERROR",
                    message,
                    Map.of("attemptsRemaining", resultado.intentosRestantes())
            );
        }
        Instant now = Instant.now(clock);
        Usuario activado = new Usuario(
                usuario.id(), usuario.nombre(), usuario.celular(), usuario.correo(), usuario.passwordHash(),
                usuario.rol(), EstadoUsuario.ACTIVA, now, usuario.createdAt(), now
        );
        usuarios.save(activado);
        return iniciarSesion(activado, deviceId);
    }

    @Transactional
    public AuthTokens iniciarSesion(String identifierInput, String password, String deviceId, String ipAddress) {
        validarDeviceId(deviceId);
        String identifier = normalizarIdentificador(identifierInput);
        loginAttempts.ensureAllowed(identifier, ipAddress);
        Usuario usuario = buscarPorIdentificador(identifier).orElse(null);
        String storedHash = usuario == null ? dummyPasswordHash : usuario.passwordHash();
        if (!passwordHashing.matches(password, storedHash)
                || usuario == null
                || usuario.estado() == EstadoUsuario.ELIMINADA) {
            loginAttempts.recordFailure(identifier, ipAddress);
            throw new DomainException("UNAUTHORIZED", INVALID_CREDENTIALS);
        }
        if (usuario.estado() != EstadoUsuario.ACTIVA
                || (usuario.rol() != RolUsuario.ADMINISTRADOR && usuario.celularVerificadoAt() == null)) {
            throw new DomainException(
                    "ACCOUNT_NOT_VERIFIED",
                    "Debes verificar tu celular antes de iniciar sesión."
            );
        }
        loginAttempts.clearIdentifier(identifier);
        return iniciarSesion(usuario, deviceId);
    }

    @Transactional
    public AuthTokens renovarSesion(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new DomainException("UNAUTHORIZED", "La sesión de renovación no es válida.");
        }
        String currentHash = tokenService.hashRefreshToken(refreshToken);
        Sesion sesion = sesiones.findByRefreshTokenHash(currentHash)
                .orElseThrow(() -> new DomainException("UNAUTHORIZED", "La sesión de renovación no es válida."));
        Instant now = Instant.now(clock);
        if (!sesion.estaActiva(now)) {
            throw new DomainException("UNAUTHORIZED", "La sesión de renovación venció o fue revocada.");
        }
        Usuario usuario = usuarios.findById(sesion.usuarioId())
                .filter(account -> account.estado() == EstadoUsuario.ACTIVA)
                .orElseThrow(() -> new DomainException("UNAUTHORIZED", "La cuenta no está activa."));
        String nextRefreshToken = tokenService.newRefreshToken();
        String nextHash = tokenService.hashRefreshToken(nextRefreshToken);
        if (!sesiones.rotateRefreshToken(sesion.id(), currentHash, nextHash, now)) {
            throw new DomainException("UNAUTHORIZED", "La sesión de renovación ya fue utilizada.");
        }
        Sesion rotated = sesion.rotarRefreshToken(nextHash);
        return tokens(tokenService.issue(rotated, usuario, nextRefreshToken), usuario);
    }

    @Transactional
    public void cerrarSesion(UUID sessionId, long sessionVersion) {
        Instant now = Instant.now(clock);
        sesiones.revoke(sessionId, now).ifPresent(revoked ->
                sessionVersions.revoke(
                        sessionId,
                        Math.max(sessionVersion + 1, revoked.sessionVersion()),
                        Duration.between(now, revoked.expiresAt())
                )
        );
    }

    @Transactional
    public void cambiarContrasena(
            UUID usuarioId,
            UUID currentSessionId,
            String currentPassword,
            String newPassword
    ) {
        validarPassword(newPassword);
        Usuario usuario = usuarios.findById(usuarioId)
                .filter(account -> account.estado() == EstadoUsuario.ACTIVA)
                .orElseThrow(() -> new DomainException("UNAUTHORIZED", "La cuenta no está activa."));
        if (!passwordHashing.matches(currentPassword, usuario.passwordHash())) {
            throw new DomainException("UNAUTHORIZED", "La contraseña actual no es correcta.");
        }
        Instant now = Instant.now(clock);
        usuarios.save(usuario.cambiarContrasena(passwordHashing.hash(newPassword), now));
        revocar(sesiones.revokeOtherSessions(usuarioId, currentSessionId, now), now);
    }

    @Transactional
    public void recuperarContrasena(String celularInput, String codigo, String newPassword) {
        String celular = normalizarCelular(celularInput);
        validarPassword(newPassword);
        VerificacionResultado resultado = verificarCelular.confirmar(
                celular,
                EstadoVerificacion.RECUPERACION_CONTRASENA,
                codigo
        );
        if (resultado.estado() != VerificacionResultado.Estado.APROBADA) {
            throw new DomainException("VALIDATION_ERROR", "El código no es válido o venció.");
        }
        Usuario usuario = usuarios.findByCelular(celular).orElse(null);
        if (usuario == null || usuario.estado() != EstadoUsuario.ACTIVA
                || usuario.rol() == RolUsuario.ADMINISTRADOR) {
            return;
        }
        Instant now = Instant.now(clock);
        usuarios.save(usuario.cambiarContrasena(passwordHashing.hash(newPassword), now));
        revocar(sesiones.revokeAllSessions(usuario.id(), now), now);
    }

    private AuthTokens iniciarSesion(Usuario usuario, String deviceId) {
        Instant now = Instant.now(clock);
        String refreshToken = tokenService.newRefreshToken();
        Sesion sesion = new Sesion(
                UUID.randomUUID(),
                usuario.id(),
                tokenService.hashRefreshToken(refreshToken),
                deviceId,
                1,
                now,
                now.plus(tokenService.refreshTokenTtl()),
                null
        );
        return tokens(tokenService.issue(sesiones.save(sesion), usuario, refreshToken), usuario);
    }

    private AuthTokens tokens(IssuedTokens issuedTokens, Usuario usuario) {
        return new AuthTokens(
                issuedTokens.accessToken(),
                issuedTokens.refreshToken(),
                issuedTokens.expiresIn(),
                "Bearer",
                usuario.rol().name()
        );
    }

    private void revocar(List<Sesion> sessions, Instant now) {
        for (Sesion session : sessions) {
            sessionVersions.revoke(
                    session.id(),
                    session.sessionVersion(),
                    Duration.between(now, session.expiresAt())
            );
        }
    }

    private java.util.Optional<Usuario> buscarPorIdentificador(String identifier) {
        return identifier.contains("@")
                ? usuarios.findByCorreo(identifier)
                : usuarios.findByCelular(identifier);
    }

    private String normalizarIdentificador(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return "";
        }
        String normalized = identifier.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("@")) {
            return normalized;
        }
        try {
            return normalizarCelular(normalized);
        } catch (DomainException exception) {
            return normalized;
        }
    }

    private String normalizarCelular(String celularInput) {
        if (celularInput == null) {
            throw new DomainException("VALIDATION_ERROR", "El celular debe estar en formato internacional.");
        }
        String celular = celularInput.trim().replaceAll("[\\s().-]", "");
        if (celular.startsWith("00")) {
            celular = "+" + celular.substring(2);
        } else if (COLOMBIAN_MOBILE.matcher(celular).matches()) {
            celular = "+57" + celular;
        }
        if (!E164_PHONE.matcher(celular).matches()) {
            throw new DomainException(
                    "VALIDATION_ERROR",
                    "El celular debe estar en formato internacional, por ejemplo +573001234567."
            );
        }
        return celular;
    }

    private String normalizarCorreo(String correoInput) {
        if (correoInput == null || correoInput.isBlank()) {
            return null;
        }
        return correoInput.trim().toLowerCase(Locale.ROOT);
    }

    private void validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank() || nombre.trim().length() > 120) {
            throw new DomainException("VALIDATION_ERROR", "El nombre es obligatorio y no puede superar 120 caracteres.");
        }
    }

    private void validarPassword(String password) {
        int length = password == null ? 0 : password.codePointCount(0, password.length());
        if (length < MIN_PASSWORD_LENGTH || length > MAX_PASSWORD_LENGTH) {
            throw new DomainException(
                    "VALIDATION_ERROR",
                    "La contraseña debe tener entre 8 y 128 caracteres."
            );
        }
    }

    private void validarDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank() || deviceId.length() > 128) {
            throw new DomainException("VALIDATION_ERROR", "La cabecera X-Device-Id es obligatoria.");
        }
    }

    public record RegistroResultado(UUID usuarioId, String estado) {
    }
}
