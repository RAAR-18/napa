package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.AuthTokens;
import co.edu.napa.cuentas.application.ports.out.IssuedTokens;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefrescarSesionUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Mock private UsuarioRepositoryPort usuarios;
    @Mock private SesionRepositoryPort sesiones;
    @Mock private TokenServicePort tokenService;

    private RefrescarSesionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RefrescarSesionUseCase(usuarios, sesiones, tokenService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void refreshTokenIsRotatedAtomically() {
        Usuario user = activeUser();
        UUID sessionId = UUID.randomUUID();
        Sesion session = session(user, sessionId);
        when(tokenService.hashRefreshToken("current-refresh")).thenReturn("current-refresh-hash");
        when(sesiones.findByRefreshTokenHash("current-refresh-hash")).thenReturn(Optional.of(session));
        when(usuarios.findById(user.id())).thenReturn(Optional.of(user));
        when(tokenService.newRefreshToken()).thenReturn("next-refresh");
        when(tokenService.hashRefreshToken("next-refresh")).thenReturn("next-refresh-hash");
        when(sesiones.rotateRefreshToken(sessionId, "current-refresh-hash", "next-refresh-hash", NOW))
                .thenReturn(true);
        when(tokenService.issue(any(), eq(user), eq("next-refresh")))
                .thenReturn(new IssuedTokens("new-access", "next-refresh", 900));

        AuthTokens tokens = useCase.refrescar("current-refresh", "device-1");

        assertEquals("new-access", tokens.accessToken());
        assertEquals("next-refresh", tokens.refreshToken());
        verify(sesiones).rotateRefreshToken(sessionId, "current-refresh-hash", "next-refresh-hash", NOW);
    }

    @Test
    void rejectsARefreshTokenPresentedFromAnotherDevice() {
        Usuario user = activeUser();
        Sesion session = session(user, UUID.randomUUID());
        when(tokenService.hashRefreshToken("current-refresh")).thenReturn("current-refresh-hash");
        when(sesiones.findByRefreshTokenHash("current-refresh-hash")).thenReturn(Optional.of(session));

        DomainException exception = assertThrows(
                DomainException.class, () -> useCase.refrescar("current-refresh", "device-2")
        );

        assertEquals("UNAUTHORIZED", exception.code());
        verify(sesiones, never()).rotateRefreshToken(any(), any(), any(), any());
    }

    @Test
    void rejectsAReusedRefreshToken() {
        Usuario user = activeUser();
        Sesion session = session(user, UUID.randomUUID());
        when(tokenService.hashRefreshToken("current-refresh")).thenReturn("current-refresh-hash");
        when(sesiones.findByRefreshTokenHash("current-refresh-hash")).thenReturn(Optional.of(session));
        when(usuarios.findById(user.id())).thenReturn(Optional.of(user));
        when(tokenService.newRefreshToken()).thenReturn("next-refresh");
        when(tokenService.hashRefreshToken("next-refresh")).thenReturn("next-refresh-hash");
        when(sesiones.rotateRefreshToken(any(), any(), any(), any())).thenReturn(false);

        DomainException exception = assertThrows(
                DomainException.class, () -> useCase.refrescar("current-refresh", "device-1")
        );

        assertEquals("UNAUTHORIZED", exception.code());
    }

    @Test
    void reportsAnExpiredSession() {
        Usuario user = activeUser();
        Sesion expired = new Sesion(
                UUID.randomUUID(), user.id(), "current-refresh-hash", "device-1", 1,
                NOW.minus(Duration.ofDays(31)), NOW.minusSeconds(1), null
        );
        when(tokenService.hashRefreshToken("current-refresh")).thenReturn("current-refresh-hash");
        when(sesiones.findByRefreshTokenHash("current-refresh-hash")).thenReturn(Optional.of(expired));

        DomainException exception = assertThrows(
                DomainException.class, () -> useCase.refrescar("current-refresh", "device-1")
        );

        assertEquals("TOKEN_EXPIRED", exception.code());
    }

    private Sesion session(Usuario user, UUID sessionId) {
        return new Sesion(
                sessionId, user.id(), "current-refresh-hash", "device-1", 1,
                NOW.minusSeconds(60), NOW.plus(Duration.ofDays(30)), null
        );
    }

    private Usuario activeUser() {
        return new Usuario(
                UUID.randomUUID(), "Ana", "+573001234567", "ana@example.com",
                "hashed:correct-password", RolUsuario.CLIENTE, EstadoUsuario.ACTIVA,
                NOW.minusSeconds(100), NOW.minusSeconds(200), NOW.minusSeconds(100)
        );
    }
}