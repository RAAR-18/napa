package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.EstadoVerificacion;
import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.cuentas.domain.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitarRecuperacionUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Mock private UsuarioRepositoryPort usuarios;
    @Mock private VerificarCelularPort verificarCelular;

    private SolicitarRecuperacionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SolicitarRecuperacionUseCase(usuarios, verificarCelular);
    }

    @Test
    void sendsTheCodeToAnActiveAccount() {
        when(usuarios.findByCelular("+573001234567"))
                .thenReturn(Optional.of(user(RolUsuario.CLIENTE, EstadoUsuario.ACTIVA)));

        useCase.solicitar("300 123 4567", "127.0.0.1");

        verify(verificarCelular).solicitar(
                "+573001234567", EstadoVerificacion.RECUPERACION_CONTRASENA, "127.0.0.1"
        );
    }

    @Test
    void doesNotSendAnythingForAnUnknownPhone() {
        when(usuarios.findByCelular("+573001234567")).thenReturn(Optional.empty());

        useCase.solicitar("300 123 4567", "127.0.0.1");

        verify(verificarCelular, never()).solicitar(anyString(), any(), anyString());
    }

    @Test
    void doesNotSendAnythingToAnAdministrator() {
        when(usuarios.findByCelular("+573001234567"))
                .thenReturn(Optional.of(user(RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVA)));

        useCase.solicitar("+573001234567", "127.0.0.1");

        verify(verificarCelular, never()).solicitar(anyString(), any(), anyString());
    }

    @Test
    void doesNotSendAnythingToAnUnverifiedAccount() {
        when(usuarios.findByCelular("+573001234567"))
                .thenReturn(Optional.of(user(RolUsuario.CLIENTE, EstadoUsuario.PENDIENTE_VERIFICACION)));

        useCase.solicitar("+573001234567", "127.0.0.1");

        verify(verificarCelular, never()).solicitar(anyString(), any(), anyString());
    }

    private Usuario user(RolUsuario rol, EstadoUsuario estado) {
        return new Usuario(
                UUID.randomUUID(), "Ana", "+573001234567", "ana@example.com",
                "hashed:correct-password", rol, estado, null, NOW, NOW
        );
    }
}