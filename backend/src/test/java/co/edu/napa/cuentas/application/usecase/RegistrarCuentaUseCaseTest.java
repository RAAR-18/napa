package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.RegistrarCuentaPort;
import co.edu.napa.cuentas.application.ports.out.PasswordHashingPort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.EstadoVerificacion;
import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.cuentas.domain.model.Usuario;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrarCuentaUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Mock private UsuarioRepositoryPort usuarios;
    @Mock private VerificarCelularPort verificarCelular;
    @Mock private PasswordHashingPort passwordHashing;

    private RegistrarCuentaUseCase useCase;

    @BeforeEach
    void setUp() {
        lenient().when(passwordHashing.hash(anyString())).thenAnswer(invocation ->
                "hashed:" + invocation.getArgument(0, String.class));
        useCase = new RegistrarCuentaUseCase(
                usuarios, verificarCelular, passwordHashing, Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void rejectsPasswordConfirmationMismatchBeforePersistence() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.registrar(
                        "Ana", "+57 300 123 4567", null,
                        "password1", "password2",
                        RolUsuario.CLIENTE, "device-1", "127.0.0.1"
                )
        );

        assertEquals("VALIDATION_ERROR", exception.code());
        verify(usuarios, never()).save(any());
        verify(verificarCelular, never()).solicitar(anyString(), any(), anyString());
    }

    @Test
    void rejectsAdministratorRole() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.registrar(
                        "Ana", "+573001234567", null,
                        "password1", "password1",
                        RolUsuario.ADMINISTRADOR, "device-1", "127.0.0.1"
                )
        );

        assertEquals("VALIDATION_ERROR", exception.code());
        verify(usuarios, never()).save(any());
    }

    @Test
    void rejectsAnAlreadyRegisteredPhone() {
        when(usuarios.existsByCelular("+573001234567")).thenReturn(true);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.registrar(
                        "Ana", "300 123 4567", null,
                        "password1", "password1",
                        RolUsuario.CLIENTE, "device-1", "127.0.0.1"
                )
        );

        assertEquals("PHONE_ALREADY_REGISTERED", exception.code());
        verify(usuarios, never()).save(any());
    }

    @Test
    void rejectsAnAlreadyRegisteredEmail() {
        when(usuarios.existsByCelular("+573001234567")).thenReturn(false);
        when(usuarios.existsByCorreo("ana@example.com")).thenReturn(true);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.registrar(
                        "Ana", "300 123 4567", "Ana@Example.com",
                        "password1", "password1",
                        RolUsuario.CLIENTE, "device-1", "127.0.0.1"
                )
        );

        assertEquals("EMAIL_ALREADY_REGISTERED", exception.code());
        verify(usuarios, never()).save(any());
    }

    @Test
    void storesPendingAccountAndRequestsVerification() {
        when(usuarios.existsByCelular("+573001234567")).thenReturn(false);
        when(usuarios.existsByCorreo("ana@example.com")).thenReturn(false);
        when(usuarios.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RegistrarCuentaPort.RegistroResultado result = useCase.registrar(
                "Ana", "+57 300 123 4567", "ANA@example.com",
                "password1", "password1",
                RolUsuario.CLIENTE, "device-1", "127.0.0.1"
        );

        ArgumentCaptor<Usuario> userCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(userCaptor.capture());
        assertEquals(EstadoUsuario.PENDIENTE_VERIFICACION, userCaptor.getValue().estado());
        assertEquals("+573001234567", userCaptor.getValue().celular());
        assertEquals("ana@example.com", userCaptor.getValue().correo());
        assertEquals(userCaptor.getValue().id(), result.usuarioId());
        verify(verificarCelular).solicitar(
                "+573001234567", EstadoVerificacion.REGISTRO, "127.0.0.1"
        );
    }

    @Test
    void normalizesTenDigitColombianMobileNumbers() {
        when(usuarios.existsByCelular("+573001234567")).thenReturn(false);
        when(usuarios.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.registrar(
                "Ana", "300 123 4567", null,
                "password1", "password1",
                RolUsuario.CLIENTE, "device-1", "127.0.0.1"
        );

        ArgumentCaptor<Usuario> userCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(userCaptor.capture());
        assertEquals("+573001234567", userCaptor.getValue().celular());
        verify(verificarCelular).solicitar(
                "+573001234567", EstadoVerificacion.REGISTRO, "127.0.0.1"
        );
    }
}