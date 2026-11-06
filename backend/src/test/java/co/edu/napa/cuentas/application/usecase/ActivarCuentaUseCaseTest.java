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
class ActivarCuentaUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Mock private UsuarioRepositoryPort usuarios;
    @Mock private SesionRepositoryPort sesiones;
    @Mock private TokenServicePort tokenService;

    private ActivarCuentaUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ActivarCuentaUseCase(usuarios, sesiones, tokenService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void activatesThePendingAccountAndStartsTheFirstSession() {
        Usuario pendiente = user(EstadoUsuario.PENDIENTE_VERIFICACION);
        when(usuarios.findByCelular("+573001234567")).thenReturn(Optional.of(pendiente));
        when(usuarios.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenService.newRefreshToken()).thenReturn("opaque-refresh");
        when(tokenService.hashRefreshToken("opaque-refresh")).thenReturn("refresh-hash");
        when(tokenService.refreshTokenTtl()).thenReturn(Duration.ofDays(30));
        when(sesiones.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenService.issue(any(), any(), eq("opaque-refresh")))
                .thenReturn(new IssuedTokens("access", "opaque-refresh", 900));

        AuthTokens tokens = useCase.activar("300 123 4567", "device-1");

        assertEquals("access", tokens.accessToken());
        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(guardado.capture());
        assertEquals(EstadoUsuario.ACTIVA, guardado.getValue().estado());
        assertEquals(NOW, guardado.getValue().celularVerificadoAt());
        verify(sesiones).save(any(Sesion.class));
    }

    @Test
    void reportsThereIsNothingPendingForAnUnknownPhone() {
        when(usuarios.findByCelular("+573001234567")).thenReturn(Optional.empty());

        DomainException exception = assertThrows(
                DomainException.class, () -> useCase.activar("+573001234567", "device-1")
        );

        assertEquals("NOT_FOUND", exception.code());
    }

    @Test
    void rejectsAnAccountThatIsAlreadyActive() {
        when(usuarios.findByCelular("+573001234567")).thenReturn(Optional.of(user(EstadoUsuario.ACTIVA)));

        DomainException exception = assertThrows(
                DomainException.class, () -> useCase.activar("+573001234567", "device-1")
        );

        assertEquals("CONFLICT", exception.code());
        verify(usuarios, never()).save(any());
    }

    private Usuario user(EstadoUsuario estado) {
        return new Usuario(
                UUID.randomUUID(), "Ana", "+573001234567", "ana@example.com",
                "hashed:correct-password", RolUsuario.CLIENTE, estado,
                estado == EstadoUsuario.ACTIVA ? NOW.minusSeconds(10) : null,
                NOW.minusSeconds(200), NOW.minusSeconds(100)
        );
    }
}