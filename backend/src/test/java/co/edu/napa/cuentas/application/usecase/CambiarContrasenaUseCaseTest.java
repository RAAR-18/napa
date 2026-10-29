package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.AuthTokens;
import co.edu.napa.cuentas.application.ports.out.IssuedTokens;
import co.edu.napa.cuentas.application.ports.out.PasswordHashingPort;
import co.edu.napa.cuentas.application.ports.out.SesionRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.SessionVersionPort;
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
import java.util.List;
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
class CambiarContrasenaUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Mock private UsuarioRepositoryPort usuarios;
    @Mock private SesionRepositoryPort sesiones;
    @Mock private SessionVersionPort sessionVersions;
    @Mock private PasswordHashingPort passwordHashing;
    @Mock private TokenServicePort tokenService;

    private CambiarContrasenaUseCase useCase;

    @BeforeEach
    void setUp() {
        lenient().when(passwordHashing.hash(anyString())).thenAnswer(invocation ->
                "hashed:" + invocation.getArgument(0, String.class));
        lenient().when(passwordHashing.matches(anyString(), anyString())).thenAnswer(invocation ->
                invocation.getArgument(1, String.class)
                        .equals("hashed:" + invocation.getArgument(0, String.class)));
        useCase = new CambiarContrasenaUseCase(
                usuarios, sesiones, sessionVersions, passwordHashing, tokenService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void rejectsPasswordsShorterThanEightCharacters() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.cambiar(UUID.randomUUID(), UUID.randomUUID(), "current-password", "short")
        );

        assertEquals("VALIDATION_ERROR", exception.code());
        verify(usuarios, never()).findById(any());
    }

    @Test
    void rejectsAWrongCurrentPasswordWithoutChangingIt() {
        Usuario user = activeUser();
        when(usuarios.findById(user.id())).thenReturn(Optional.of(user));

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.cambiar(user.id(), UUID.randomUUID(), "wrong-password", "new-password")
        );

        assertEquals("INVALID_CURRENT_PASSWORD", exception.code());
        verify(usuarios, never()).save(any());
    }

    @Test
    void rejectsReusingTheCurrentPassword() {
        Usuario user = activeUser();
        when(usuarios.findById(user.id())).thenReturn(Optional.of(user));

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.cambiar(user.id(), UUID.randomUUID(), "correct-password", "correct-password")
        );

        assertEquals("PASSWORD_REUSED", exception.code());
        verify(usuarios, never()).save(any());
    }

    @Test
    void revokesOtherSessionsAndRotatesTheCurrentOne() {
        Usuario user = activeUser();
        UUID currentSessionId = UUID.randomUUID();
        UUID otherSessionId = UUID.randomUUID();
        Sesion revokedSession = new Sesion(
                otherSessionId, user.id(), "refresh-hash", "other-device", 2,
                NOW.minusSeconds(60), NOW.plusSeconds(3600), NOW
        );
        Sesion currentSession = new Sesion(
                currentSessionId, user.id(), "new-refresh-hash", "device-1", 1,
                NOW.minusSeconds(60), NOW.plusSeconds(3600), null
        );
        when(usuarios.findById(user.id())).thenReturn(Optional.of(user));
        when(usuarios.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sesiones.revokeOtherSessions(user.id(), currentSessionId, NOW))
                .thenReturn(List.of(revokedSession));
        when(tokenService.newRefreshToken()).thenReturn("new-refresh");
        when(tokenService.hashRefreshToken("new-refresh")).thenReturn("new-refresh-hash");
        when(sesiones.replaceRefreshToken(currentSessionId, "new-refresh-hash", NOW))
                .thenReturn(Optional.of(currentSession));
        when(tokenService.issue(eq(currentSession), any(), eq("new-refresh")))
                .thenReturn(new IssuedTokens("new-access", "new-refresh", 900));

        AuthTokens tokens = useCase.cambiar(user.id(), currentSessionId, "correct-password", "new-password");

        assertEquals("new-access", tokens.accessToken());
        assertEquals("new-refresh", tokens.refreshToken());
        verify(sessionVersions).revoke(otherSessionId, 2, Duration.ofSeconds(3600));
        verify(sesiones, never()).revoke(currentSessionId, NOW);
    }

    private Usuario activeUser() {
        return new Usuario(
                UUID.randomUUID(), "Ana", "+573001234567", "ana@example.com",
                "hashed:correct-password", RolUsuario.CLIENTE, EstadoUsuario.ACTIVA,
                NOW.minusSeconds(100), NOW.minusSeconds(200), NOW.minusSeconds(100)
        );
    }
}