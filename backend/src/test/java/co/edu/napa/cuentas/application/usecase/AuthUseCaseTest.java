package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.out.IssuedTokens;
import co.edu.napa.cuentas.application.ports.out.LoginAttemptPort;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Mock private UsuarioRepositoryPort usuarios;
    @Mock private SesionRepositoryPort sesiones;
    @Mock private VerificarCelularPort verificarCelular;
    @Mock private VerificationRateLimitPort verificationRateLimits;
    @Mock private LoginAttemptPort loginAttempts;
    @Mock private SessionVersionPort sessionVersions;
    @Mock private PasswordHashingPort passwordHashing;
    @Mock private TokenServicePort tokenService;

    private AuthUseCase useCase;

    @BeforeEach
    void setUp() {
        when(passwordHashing.hash(anyString())).thenAnswer(invocation ->
                "hashed:" + invocation.getArgument(0, String.class));
        lenient().when(passwordHashing.matches(anyString(), anyString())).thenAnswer(invocation ->
                invocation.getArgument(1, String.class)
                        .equals("hashed:" + invocation.getArgument(0, String.class)));
        useCase = new AuthUseCase(
                usuarios,
                sesiones,
                verificarCelular,
                verificationRateLimits,
                loginAttempts,
                sessionVersions,
                passwordHashing,
                tokenService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void registrationRejectsPasswordConfirmationMismatchBeforePersistence() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.registrar(
                        "Ana",
                        "+57 300 123 4567",
                        null,
                        "password1",
                        "password2",
                        RolUsuario.CLIENTE,
                        "device-1",
                        "127.0.0.1"
                )
        );

        assertEquals("VALIDATION_ERROR", exception.code());
        verify(usuarios, never()).save(any());
        verify(verificarCelular, never()).solicitar(anyString(), any(), anyString());
    }

    @Test
    void registrationStoresPendingAccountAndRequestsVerification() {
        when(usuarios.existsByCelular("+573001234567")).thenReturn(false);
        when(usuarios.existsByCorreo("ana@example.com")).thenReturn(false);
        when(usuarios.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(verificarCelular).solicitar(
                "+573001234567",
                EstadoVerificacion.REGISTRO,
                "127.0.0.1"
        );

        AuthUseCase.RegistroResultado result = useCase.registrar(
                "Ana",
                "+57 300 123 4567",
                "ANA@example.com",
                "password1",
                "password1",
                RolUsuario.CLIENTE,
                "device-1",
                "127.0.0.1"
        );

        ArgumentCaptor<Usuario> userCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(userCaptor.capture());
        assertEquals(EstadoUsuario.PENDIENTE_VERIFICACION, userCaptor.getValue().estado());
        assertEquals("+573001234567", userCaptor.getValue().celular());
        assertEquals("ana@example.com", userCaptor.getValue().correo());
        assertEquals(userCaptor.getValue().id(), result.usuarioId());
        verify(verificarCelular).solicitar(
                "+573001234567",
                EstadoVerificacion.REGISTRO,
                "127.0.0.1"
        );
        verify(verificationRateLimits).checkAndRecord("+573001234567", "127.0.0.1");
    }

    @Test
    void registrationNormalizesTenDigitColombianMobileNumbers() {
        when(usuarios.existsByCelular("+573001234567")).thenReturn(false);
        when(usuarios.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.registrar(
                "Ana",
                "300 123 4567",
                null,
                "password1",
                "password1",
                RolUsuario.CLIENTE,
                "device-1",
                "127.0.0.1"
        );

        ArgumentCaptor<Usuario> userCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(userCaptor.capture());
        assertEquals("+573001234567", userCaptor.getValue().celular());
        verify(verificarCelular).solicitar(
                "+573001234567",
                EstadoVerificacion.REGISTRO,
                "127.0.0.1"
        );
    }

    @Test
    void loginRejectsUnknownAccountWithoutRevealingWhichCredentialFailed() {
        when(usuarios.findByCorreo("unknown@example.com")).thenReturn(Optional.empty());

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.iniciarSesion(
                        "unknown@example.com",
                        "incorrect",
                        "device-1",
                        "127.0.0.1"
                )
        );

        assertEquals("UNAUTHORIZED", exception.code());
        assertEquals("El correo o celular y la contraseña no coinciden.", exception.getMessage());
        verify(loginAttempts).recordFailure("unknown@example.com", "127.0.0.1");
        verify(sesiones, never()).save(any());
    }

    @Test
    void successfulLoginCreatesRefreshSessionAndReturnsTokens() {
        Usuario user = activeUser();
        when(usuarios.findByCorreo("ana@example.com")).thenReturn(Optional.of(user));
        when(tokenService.newRefreshToken()).thenReturn("opaque-refresh");
        when(tokenService.hashRefreshToken("opaque-refresh")).thenReturn("refresh-hash");
        when(tokenService.refreshTokenTtl()).thenReturn(Duration.ofDays(30));
        when(sesiones.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenService.issue(any(), eq(user), eq("opaque-refresh")))
                .thenReturn(new IssuedTokens("access", "opaque-refresh", 900));

        AuthTokens tokens = useCase.iniciarSesion(
                "ANA@example.com",
                "correct-password",
                "device-1",
                "127.0.0.1"
        );

        assertEquals("access", tokens.accessToken());
        assertEquals("opaque-refresh", tokens.refreshToken());
        assertEquals("CLIENTE", tokens.rol());
        verify(loginAttempts).clearIdentifier("ana@example.com");
        verify(sesiones).save(any(Sesion.class));
    }

    @Test
    void refreshTokenIsRotatedAtomically() {
        Usuario user = activeUser();
        UUID sessionId = UUID.randomUUID();
        Sesion session = new Sesion(
                sessionId,
                user.id(),
                "current-refresh-hash",
                "device-1",
                1,
                NOW.minusSeconds(60),
                NOW.plus(Duration.ofDays(30)),
                null
        );
        when(tokenService.hashRefreshToken("current-refresh")).thenReturn("current-refresh-hash");
        when(sesiones.findByRefreshTokenHash("current-refresh-hash")).thenReturn(Optional.of(session));
        when(usuarios.findById(user.id())).thenReturn(Optional.of(user));
        when(tokenService.newRefreshToken()).thenReturn("next-refresh");
        when(tokenService.hashRefreshToken("next-refresh")).thenReturn("next-refresh-hash");
        when(sesiones.rotateRefreshToken(
                sessionId,
                "current-refresh-hash",
                "next-refresh-hash",
                NOW
        )).thenReturn(true);
        when(tokenService.issue(any(), eq(user), eq("next-refresh")))
                .thenReturn(new IssuedTokens("new-access", "next-refresh", 900));

        AuthTokens tokens = useCase.renovarSesion("current-refresh");

        assertEquals("new-access", tokens.accessToken());
        assertEquals("next-refresh", tokens.refreshToken());
        verify(sesiones).rotateRefreshToken(
                sessionId,
                "current-refresh-hash",
                "next-refresh-hash",
                NOW
        );
    }

    @Test
    void loginDoesNotCreateSessionForUnverifiedAccount() {
        Usuario user = new Usuario(
                UUID.randomUUID(),
                "Ana",
                "+573001234567",
                "ana@example.com",
                "hashed:correct-password",
                RolUsuario.CLIENTE,
                EstadoUsuario.PENDIENTE_VERIFICACION,
                null,
                NOW,
                NOW
        );
        when(usuarios.findByCorreo("ana@example.com")).thenReturn(Optional.of(user));

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.iniciarSesion(
                        "ana@example.com",
                        "correct-password",
                        "device-1",
                        "127.0.0.1"
                )
        );

        assertEquals("ACCOUNT_NOT_VERIFIED", exception.code());
        verify(sesiones, never()).save(any());
    }

    @Test
    void correctRegistrationCodeActivatesAccountAndStartsSession() {
        Usuario pendingUser = new Usuario(
                UUID.randomUUID(),
                "Ana",
                "+573001234567",
                "ana@example.com",
                "hashed:correct-password",
                RolUsuario.CLIENTE,
                EstadoUsuario.PENDIENTE_VERIFICACION,
                null,
                NOW.minusSeconds(200),
                NOW.minusSeconds(100)
        );
        when(usuarios.findByCelular(pendingUser.celular())).thenReturn(Optional.of(pendingUser));
        when(verificarCelular.confirmar(
                pendingUser.celular(),
                EstadoVerificacion.REGISTRO,
                "123456"
        )).thenReturn(new VerificacionResultado(VerificacionResultado.Estado.APROBADA, 5));
        when(usuarios.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenService.newRefreshToken()).thenReturn("opaque-refresh");
        when(tokenService.hashRefreshToken("opaque-refresh")).thenReturn("refresh-hash");
        when(tokenService.refreshTokenTtl()).thenReturn(Duration.ofDays(30));
        when(sesiones.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenService.issue(any(), any(), eq("opaque-refresh")))
                .thenReturn(new IssuedTokens("access", "opaque-refresh", 900));

        AuthTokens tokens = useCase.confirmarRegistro(
                pendingUser.celular(),
                "123456",
                "device-1"
        );

        assertEquals("access", tokens.accessToken());
        ArgumentCaptor<Usuario> userCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(userCaptor.capture());
        assertEquals(EstadoUsuario.ACTIVA, userCaptor.getValue().estado());
        verify(sesiones).save(any(Sesion.class));
    }

    @Test
    void failedRegistrationCodeReportsRemainingAttempts() {
        Usuario pendingUser = new Usuario(
                UUID.randomUUID(),
                "Ana",
                "+573001234567",
                null,
                "hashed:correct-password",
                RolUsuario.CLIENTE,
                EstadoUsuario.PENDIENTE_VERIFICACION,
                null,
                NOW.minusSeconds(200),
                NOW.minusSeconds(100)
        );
        when(usuarios.findByCelular(pendingUser.celular())).thenReturn(Optional.of(pendingUser));
        when(verificarCelular.confirmar(
                pendingUser.celular(),
                EstadoVerificacion.REGISTRO,
                "123456"
        )).thenReturn(new VerificacionResultado(VerificacionResultado.Estado.INCORRECTA, 3));

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.confirmarRegistro(pendingUser.celular(), "123456", "device-1")
        );

        assertEquals("VALIDATION_ERROR", exception.code());
        assertEquals(3, exception.details().get("attemptsRemaining"));
        verify(usuarios, never()).save(any());
    }

    @Test
    void passwordChangeRejectsPasswordsShorterThanEightCharacters() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.cambiarContrasena(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "current-password",
                        "short"
                )
        );

        assertEquals("VALIDATION_ERROR", exception.code());
        verify(usuarios, never()).findById(any());
    }

    @Test
    void passwordChangeRevokesOtherSessionsButKeepsCurrentSession() {
        Usuario user = activeUser();
        UUID currentSessionId = UUID.randomUUID();
        UUID otherSessionId = UUID.randomUUID();
        Sesion revokedSession = new Sesion(
                otherSessionId,
                user.id(),
                "refresh-hash",
                "other-device",
                2,
                NOW.minusSeconds(60),
                NOW.plusSeconds(3600),
                NOW
        );
        when(usuarios.findById(user.id())).thenReturn(Optional.of(user));
        when(usuarios.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sesiones.revokeOtherSessions(user.id(), currentSessionId, NOW))
                .thenReturn(List.of(revokedSession));

        useCase.cambiarContrasena(
                user.id(),
                currentSessionId,
                "correct-password",
                "new-password"
        );

        verify(sessionVersions).revoke(otherSessionId, 2, Duration.ofSeconds(3600));
        verify(sesiones, never()).revoke(currentSessionId, NOW);
    }

    private Usuario activeUser() {
        return new Usuario(
                UUID.randomUUID(),
                "Ana",
                "+573001234567",
                "ana@example.com",
                "hashed:correct-password",
                RolUsuario.CLIENTE,
                EstadoUsuario.ACTIVA,
                NOW.minusSeconds(100),
                NOW.minusSeconds(200),
                NOW.minusSeconds(100)
        );
    }
}
