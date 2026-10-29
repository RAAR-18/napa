package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.AuthTokens;
import co.edu.napa.cuentas.application.ports.out.IssuedTokens;
import co.edu.napa.cuentas.application.ports.out.SesionRepositoryPort;
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
class ConfirmarRegistroUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Mock private UsuarioRepositoryPort usuarios;
    @Mock private SesionRepositoryPort sesiones;
    @Mock private VerificarCelularPort verificarCelular;
    @Mock private TokenServicePort tokenService;

    private ConfirmarRegistroUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ConfirmarRegistroUseCase(
                usuarios, sesiones, verificarCelular, tokenService, Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void correctCodeActivatesAccountAndStartsSession() {
        Usuario pendingUser = pendingUser("ana@example.com");
        when(usuarios.findByCelular(pendingUser.celular())).thenReturn(Optional.of(pendingUser));
        when(verificarCelular.confirmar(pendingUser.celular(), EstadoVerificacion.REGISTRO, "123456"))
                .thenReturn(new VerificacionResultado(VerificacionResultado.Estado.APROBADA, 5));
        when(usuarios.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenService.newRefreshToken()).thenReturn("opaque-refresh");
        when(tokenService.hashRefreshToken("opaque-refresh")).thenReturn("refresh-hash");
        when(tokenService.refreshTokenTtl()).thenReturn(Duration.ofDays(30));
        when(sesiones.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenService.issue(any(), any(), eq("opaque-refresh")))
                .thenReturn(new IssuedTokens("access", "opaque-refresh", 900));

        AuthTokens tokens = useCase.confirmar(pendingUser.celular(), "123456", "device-1");

        assertEquals("access", tokens.accessToken());
        ArgumentCaptor<Usuario> userCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(userCaptor.capture());
        assertEquals(EstadoUsuario.ACTIVA, userCaptor.getValue().estado());
        assertEquals(NOW, userCaptor.getValue().celularVerificadoAt());
        verify(sesiones).save(any(Sesion.class));
    }

    @Test
    void failedCodeReportsRemainingAttempts() {
        Usuario pendingUser = pendingUser(null);
        when(usuarios.findByCelular(pendingUser.celular())).thenReturn(Optional.of(pendingUser));
        when(verificarCelular.confirmar(pendingUser.celular(), EstadoVerificacion.REGISTRO, "123456"))
                .thenReturn(new VerificacionResultado(VerificacionResultado.Estado.INCORRECTA, 3));

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.confirmar(pendingUser.celular(), "123456", "device-1")
        );

        assertEquals("OTP_INVALID", exception.code());
        assertEquals(3, exception.details().get("intentosRestantes"));
        verify(usuarios, never()).save(any());
    }

    private Usuario pendingUser(String correo) {
        return new Usuario(
                UUID.randomUUID(), "Ana", "+573001234567", correo,
                "hashed:correct-password", RolUsuario.CLIENTE,
                EstadoUsuario.PENDIENTE_VERIFICACION, null,
                NOW.minusSeconds(200), NOW.minusSeconds(100)
        );
    }
}