package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.RegistrarCuentaPort;
import co.edu.napa.cuentas.application.ports.in.SolicitarVerificacionPort;
import co.edu.napa.cuentas.application.ports.out.PasswordHashingPort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    @Mock private SolicitarVerificacionPort solicitarVerificacion;
    @Mock private PasswordHashingPort passwordHashing;

    private RegistrarCuentaUseCase useCase;

    @BeforeEach
    void setUp() {
        lenient().when(passwordHashing.hash(anyString())).thenAnswer(invocation ->
                "hashed:" + invocation.getArgument(0, String.class));
        useCase = new RegistrarCuentaUseCase(
                usuarios, solicitarVerificacion, passwordHashing, Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void rejectsPasswordConfirmationMismatchBeforePersistence() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> registrar("+57 300 123 4567", null, "password1", "password2", RolUsuario.CLIENTE)
        );

        assertEquals("VALIDATION_ERROR", exception.code());
        verify(usuarios, never()).save(any());
        verify(solicitarVerificacion, never()).solicitar(anyString(), any(), any(), anyString());
    }

    @Test
    void rejectsAdministratorRole() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> registrar("+573001234567", null, "password1", "password1", RolUsuario.ADMINISTRADOR)
        );

        assertEquals("VALIDATION_ERROR", exception.code());
        verify(usuarios, never()).save(any());
    }

    @Test
    void rejectsAnAlreadyRegisteredPhone() {
        when(usuarios.existsByCelular("+573001234567")).thenReturn(true);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> registrar("300 123 4567", null, "password1", "password1", RolUsuario.CLIENTE)
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
                () -> registrar("300 123 4567", "Ana@Example.com", "password1", "password1", RolUsuario.CLIENTE)
        );

        assertEquals("EMAIL_ALREADY_REGISTERED", exception.code());
        verify(usuarios, never()).save(any());
    }

    @Test
    void storesAPendingAccountAndRequestsTheCode() {
        UUID mensajeId = UUID.randomUUID();
        when(usuarios.existsByCelular("+573001234567")).thenReturn(false);
        when(usuarios.existsByCorreo("ana@example.com")).thenReturn(false);
        when(usuarios.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(solicitarVerificacion.solicitar(
                "+573001234567", PropositoVerificacion.REGISTRO, CanalVerificacion.WHATSAPP, "127.0.0.1"
        )).thenReturn(new SolicitarVerificacionPort.EnvioSolicitado(mensajeId, CanalVerificacion.WHATSAPP, 60));

        RegistrarCuentaPort.RegistroResultado result =
                registrar("+57 300 123 4567", "ANA@example.com", "password1", "password1", RolUsuario.CLIENTE);

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(guardado.capture());
        assertEquals(EstadoUsuario.PENDIENTE_VERIFICACION, guardado.getValue().estado());
        assertEquals("+573001234567", guardado.getValue().celular());
        assertEquals("ana@example.com", guardado.getValue().correo());
        assertEquals(guardado.getValue().id(), result.usuarioId());
        assertEquals("ENVIADO", result.verificacion().estado());
        assertEquals(mensajeId, result.verificacion().mensajeId());
        assertEquals(60, result.verificacion().reenvioDisponibleEn());
    }

    @Test
    void normalizesTenDigitColombianMobileNumbers() {
        when(usuarios.existsByCelular("+573001234567")).thenReturn(false);
        when(usuarios.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(solicitarVerificacion.solicitar(anyString(), any(), any(), anyString()))
                .thenReturn(new SolicitarVerificacionPort.EnvioSolicitado(UUID.randomUUID(), CanalVerificacion.WHATSAPP, 60));

        registrar("300 123 4567", null, "password1", "password1", RolUsuario.CLIENTE);

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(guardado.capture());
        assertEquals("+573001234567", guardado.getValue().celular());
    }

    @Test
    void keepsTheAccountWhenSendingTheCodeFails() {
        UUID mensajeId = UUID.randomUUID();
        when(usuarios.existsByCelular("+573001234567")).thenReturn(false);
        when(usuarios.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(solicitarVerificacion.solicitar(anyString(), any(), any(), anyString()))
                .thenThrow(new DomainException(
                        "EXTERNAL_SERVICE_UNAVAILABLE", "Proveedor caído",
                        Map.of("mensajeId", mensajeId.toString())
                ));

        RegistrarCuentaPort.RegistroResultado result =
                registrar("300 123 4567", null, "password1", "password1", RolUsuario.CLIENTE);

        verify(usuarios).save(any());
        assertEquals("FALLIDO", result.verificacion().estado());
        assertEquals(mensajeId, result.verificacion().mensajeId());
        assertEquals(0, result.verificacion().reenvioDisponibleEn());
    }

    @Test
    void keepsTheAccountEvenWhenThereIsNoMessageId() {
        when(usuarios.existsByCelular("+573001234567")).thenReturn(false);
        when(usuarios.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(solicitarVerificacion.solicitar(anyString(), any(), any(), anyString()))
                .thenThrow(new DomainException("EXTERNAL_SERVICE_UNAVAILABLE", "Redis caído"));

        RegistrarCuentaPort.RegistroResultado result =
                registrar("300 123 4567", null, "password1", "password1", RolUsuario.CLIENTE);

        assertEquals("FALLIDO", result.verificacion().estado());
        assertNull(result.verificacion().mensajeId());
    }

    @Test
    void aRateLimitErrorAbortsTheRegistration() {
        when(usuarios.existsByCelular("+573001234567")).thenReturn(false);
        when(usuarios.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(solicitarVerificacion.solicitar(anyString(), any(), any(), anyString()))
                .thenThrow(new DomainException("RATE_LIMITED", "Demasiadas solicitudes", Map.of("retryAfter", 30L)));

        DomainException exception = assertThrows(
                DomainException.class,
                () -> registrar("300 123 4567", null, "password1", "password1", RolUsuario.CLIENTE)
        );

        assertEquals("RATE_LIMITED", exception.code());
    }

    private RegistrarCuentaPort.RegistroResultado registrar(
            String celular, String correo, String password, String confirmacion, RolUsuario rol
    ) {
        return useCase.registrar("Ana", celular, correo, password, confirmacion, rol, "device-1", "127.0.0.1");
    }
}