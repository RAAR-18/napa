package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.AuthTokens;
import co.edu.napa.cuentas.application.ports.out.IssuedTokens;
import co.edu.napa.cuentas.application.ports.out.LoginAttemptPort;
import co.edu.napa.cuentas.application.ports.out.PasswordHashingPort;
import co.edu.napa.cuentas.application.ports.out.SesionRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.TokenServicePort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.cuentas.domain.model.Sesion;
import co.edu.napa.cuentas.domain.model.Usuario;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IniciarSesionUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Mock private UsuarioRepositoryPort usuarios;
    @Mock private SesionRepositoryPort sesiones;
    @Mock private LoginAttemptPort loginAttempts;
    @Mock private PasswordHashingPort passwordHashing;
    @Mock private TokenServicePort tokenService;

    private IniciarSesionUseCase useCase;

    @BeforeEach
    void setUp() {
        lenient().when(passwordHashing.hash(anyString())).thenAnswer(invocation ->
                "hashed:" + invocation.getArgument(0, String.class));
        lenient().when(passwordHashing.matches(anyString(), anyString())).thenAnswer(invocation ->
                invocation.getArgument(1, String.class)
                        .equals("hashed:" + invocation.getArgument(0, String.class)));
        useCase = new IniciarSesionUseCase(
                usuarios, sesiones, loginAttempts, passwordHashing, tokenService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void rejectsUnknownAccountWithoutRevealingWhichCredentialFailed() {
        when(usuarios.findByCorreo("unknown@example.com")).thenReturn(Optional.empty());

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.iniciarSesion("unknown@example.com", "incorrect", "device-1", "127.0.0.1")
        );

        assertEquals("INVALID_CREDENTIALS", exception.code());
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
                "ANA@example.com", "correct-password", "device-1", "127.0.0.1"
        );

        assertEquals("access", tokens.accessToken());
        assertEquals("opaque-refresh", tokens.refreshToken());
        assertEquals("CLIENTE", tokens.rol());
        verify(loginAttempts).clearIdentifier("ana@example.com");
        verify(sesiones).save(any(Sesion.class));
    }

    @Test
    void doesNotCreateSessionForUnverifiedAccount() {
        Usuario user = new Usuario(
                UUID.randomUUID(), "Ana", "+573001234567", "ana@example.com",
                "hashed:correct-password", RolUsuario.CLIENTE,
                EstadoUsuario.PENDIENTE_VERIFICACION, null, NOW, NOW
        );
        when(usuarios.findByCorreo("ana@example.com")).thenReturn(Optional.of(user));

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.iniciarSesion("ana@example.com", "correct-password", "device-1", "127.0.0.1")
        );

        assertEquals("ACCOUNT_NOT_VERIFIED", exception.code());
        verify(sesiones, never()).save(any());
    }

    private Usuario activeUser() {
        return new Usuario(
                UUID.randomUUID(), "Ana", "+573001234567", "ana@example.com",
                "hashed:correct-password", RolUsuario.CLIENTE, EstadoUsuario.ACTIVA,
                NOW.minusSeconds(100), NOW.minusSeconds(200), NOW.minusSeconds(100)
        );
    }
}