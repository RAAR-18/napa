package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.AuthTokens;
import co.edu.napa.cuentas.application.ports.out.IssuedTokens;
import co.edu.napa.cuentas.application.ports.out.PasswordHashingPort;
import co.edu.napa.cuentas.application.ports.out.SesionRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.SessionVersionPort;
import co.edu.napa.cuentas.application.ports.out.TokenServicePort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmarRecuperacionUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Mock private UsuarioRepositoryPort usuarios;
    @Mock private SesionRepositoryPort sesiones;
    @Mock private SessionVersionPort sessionVersions;
    @Mock private VerificarCelularPort verificarCelular;
    @Mock private PasswordHashingPort passwordHashing;
    @Mock private TokenServicePort tokenService;

    private ConfirmarRecuperacionUseCase useCase;

    @BeforeEach
    void setUp() {
        lenient().when(passwordHashing.hash(anyString())).thenAnswer(invocation ->
                "hashed:" + invocation.getArgument(0, String.class));
        useCase = new ConfirmarRecuperacionUseCase(
                usuarios, sesiones, sessionVersions, verificarCelular, passwordHashing, tokenService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void correctCodeSetsTheNewPasswordClosesAllSessionsAndStartsANewOne() {
        Usuario user = activeUser();
        UUID oldSessionId = UUID.randomUUID();
        Sesion revoked = new Sesion(
                oldSessionId, user.id(), "old-hash", "old-device", 2,
                NOW.minusSeconds(60), NOW.plusSeconds(3600), NOW
        );
        when(verificarCelular.confirmar("+573001234567", EstadoVerificacion.RECUPERACION_CONTRASENA, "123456"))
                .thenReturn(new VerificacionResultado(VerificacionResultado.Estado.APROBADA, 5));
        when(usuarios.findByCelular("+573001234567")).thenReturn(Optional.of(user));
        when(usuarios.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sesiones.revokeAllSessions(user.id(), NOW)).thenReturn(List.of(revoked));
        when(tokenService.newRefreshToken()).thenReturn("opaque-refresh");
        when(tokenService.hashRefreshToken("opaque-refresh")).thenReturn("refresh-hash");
        when(tokenService.refreshTokenTtl()).thenReturn(Duration.ofDays(30));
        when(sesiones.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenService.issue(any(), any(), eq("opaque-refresh")))
                .thenReturn(new IssuedTokens("access", "opaque-refresh", 900));

        AuthTokens tokens = useCase.confirmar("+573001234567", "123456", "new-password", "device-1");

        assertEquals("access", tokens.accessToken());
        ArgumentCaptor<Usuario> saved = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(saved.capture());
        assertEquals("hashed:new-password", saved.getValue().passwordHash());
        verify(sessionVersions).revoke(oldSessionId, 2, Duration.ofSeconds(3600));
        verify(sesiones).save(any(Sesion.class));
    }

    @Test
    void wrongCodeReportsRemainingAttemptsAndChangesNothing() {
        when(verificarCelular.confirmar("+573001234567", EstadoVerificacion.RECUPERACION_CONTRASENA, "000000"))
                .thenReturn(new VerificacionResultado(VerificacionResultado.Estado.INCORRECTA, 4));

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.confirmar("+573001234567", "000000", "new-password", "device-1")
        );

        assertEquals("OTP_INVALID", exception.code());
        assertEquals(4, exception.details().get("intentosRestantes"));
        verify(usuarios, never()).save(any());
        verify(sesiones, never()).revokeAllSessions(any(), any());
    }

    @Test
    void expiredCodeIsReportedAsExpired() {
        when(verificarCelular.confirmar("+573001234567", EstadoVerificacion.RECUPERACION_CONTRASENA, "123456"))
                .thenReturn(new VerificacionResultado(VerificacionResultado.Estado.VENCIDA, 0));

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.confirmar("+573001234567", "123456", "new-password", "device-1")
        );

        assertEquals("OTP_EXPIRED", exception.code());
    }

    @Test
    void rejectsAShortNewPasswordBeforeCheckingTheCode() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.confirmar("+573001234567", "123456", "short", "device-1")
        );

        assertEquals("VALIDATION_ERROR", exception.code());
        verify(verificarCelular, never()).confirmar(anyString(), any(), anyString());
    }

    private Usuario activeUser() {
        return new Usuario(
                UUID.randomUUID(), "Ana", "+573001234567", "ana@example.com",
                "hashed:old-password", RolUsuario.CLIENTE, EstadoUsuario.ACTIVA,
                NOW.minusSeconds(100), NOW.minusSeconds(200), NOW.minusSeconds(100)
        );
    }
}