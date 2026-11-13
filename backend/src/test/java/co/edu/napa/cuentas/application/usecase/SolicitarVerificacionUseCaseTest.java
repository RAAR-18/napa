package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.SolicitarVerificacionPort;
import co.edu.napa.cuentas.application.ports.out.LimitadorVerificacionPort;
import co.edu.napa.cuentas.application.ports.out.MensajeRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.EstadoMensaje;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.Mensaje;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitarVerificacionUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final String CELULAR = "+573001234567";

    @Mock private UsuarioRepositoryPort usuarios;
    @Mock private VerificarCelularPort verificarCelular;
    @Mock private MensajeRepositoryPort mensajes;
    @Mock private LimitadorVerificacionPort limitador;

    private SolicitarVerificacionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SolicitarVerificacionUseCase(
                usuarios, verificarCelular, mensajes, limitador, Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void sendsTheCodeAndRecordsTheMessageForAPendingAccount() {
        when(usuarios.findByCelular(CELULAR))
                .thenReturn(Optional.of(user(RolUsuario.CLIENTE, EstadoUsuario.PENDIENTE_VERIFICACION)));
        when(verificarCelular.iniciar(CELULAR, PropositoVerificacion.REGISTRO, CanalVerificacion.WHATSAPP))
                .thenReturn(new VerificarCelularPort.EnvioVerificacion("sid-1"));
        when(mensajes.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SolicitarVerificacionPort.EnvioSolicitado envio = useCase.solicitar(
                "300 123 4567", PropositoVerificacion.REGISTRO, CanalVerificacion.WHATSAPP, "127.0.0.1"
        );

        ArgumentCaptor<Mensaje> guardado = ArgumentCaptor.forClass(Mensaje.class);
        verify(mensajes).save(guardado.capture());
        assertEquals(EstadoMensaje.ENVIADO, guardado.getValue().estado());
        assertEquals("sid-1", guardado.getValue().proveedorId());
        assertEquals(CELULAR, guardado.getValue().destino());
        assertEquals(guardado.getValue().id(), envio.mensajeId());
        assertEquals(60, envio.reenvioDisponibleEn());
        verify(limitador).registrarEnvio(CELULAR, "127.0.0.1");
        verify(limitador).reiniciarIntentos(CELULAR, PropositoVerificacion.REGISTRO);
    }

    @Test
    void answersTheSameForAnUnknownPhoneWithoutCallingTheProvider() {
        when(usuarios.findByCelular(CELULAR)).thenReturn(Optional.empty());

        SolicitarVerificacionPort.EnvioSolicitado envio = useCase.solicitar(
                CELULAR, PropositoVerificacion.RECUPERACION, CanalVerificacion.WHATSAPP, "127.0.0.1"
        );

        assertNotNull(envio.mensajeId());
        assertEquals(60, envio.reenvioDisponibleEn());
        verify(limitador).registrarEnvio(CELULAR, "127.0.0.1");
        verify(verificarCelular, never()).iniciar(anyString(), any(), any());
        verify(mensajes, never()).save(any());
    }

    @Test
    void recoveryDoesNotSendToAnAdministrator() {
        when(usuarios.findByCelular(CELULAR))
                .thenReturn(Optional.of(user(RolUsuario.ADMINISTRADOR, EstadoUsuario.ACTIVA)));

        useCase.solicitar(CELULAR, PropositoVerificacion.RECUPERACION, CanalVerificacion.WHATSAPP, "127.0.0.1");

        verify(verificarCelular, never()).iniciar(anyString(), any(), any());
    }

    @Test
    void recoverySendsToAnActiveAccount() {
        when(usuarios.findByCelular(CELULAR))
                .thenReturn(Optional.of(user(RolUsuario.CLIENTE, EstadoUsuario.ACTIVA)));
        when(verificarCelular.iniciar(CELULAR, PropositoVerificacion.RECUPERACION, CanalVerificacion.SMS))
                .thenReturn(new VerificarCelularPort.EnvioVerificacion("sid-2"));
        when(mensajes.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SolicitarVerificacionPort.EnvioSolicitado envio = useCase.solicitar(
                CELULAR, PropositoVerificacion.RECUPERACION, CanalVerificacion.SMS, "127.0.0.1"
        );

        assertEquals(CanalVerificacion.SMS, envio.canal());
        verify(verificarCelular).iniciar(CELULAR, PropositoVerificacion.RECUPERACION, CanalVerificacion.SMS);
    }

    @Test
    void providerFailureIsRecordedAndReportedWithTheMessageId() {
        when(usuarios.findByCelular(CELULAR))
                .thenReturn(Optional.of(user(RolUsuario.CLIENTE, EstadoUsuario.PENDIENTE_VERIFICACION)));
        when(verificarCelular.iniciar(CELULAR, PropositoVerificacion.REGISTRO, CanalVerificacion.WHATSAPP))
                .thenThrow(new DomainException("EXTERNAL_SERVICE_UNAVAILABLE", "Proveedor caído"));
        when(mensajes.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.solicitar(CELULAR, PropositoVerificacion.REGISTRO, CanalVerificacion.WHATSAPP, "127.0.0.1")
        );

        assertEquals("EXTERNAL_SERVICE_UNAVAILABLE", exception.code());
        ArgumentCaptor<Mensaje> guardado = ArgumentCaptor.forClass(Mensaje.class);
        verify(mensajes).save(guardado.capture());
        assertEquals(EstadoMensaje.FALLIDO, guardado.getValue().estado());
        assertEquals(guardado.getValue().id().toString(), exception.details().get("mensajeId"));
        verify(limitador, never()).reiniciarIntentos(anyString(), any());
    }

    @Test
    void rateLimitStopsBeforeAnythingElse() {
        doThrow(new DomainException("RATE_LIMITED", "Demasiadas solicitudes", Map.of("retryAfter", 42L)))
                .when(limitador).registrarEnvio(CELULAR, "127.0.0.1");

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.solicitar(CELULAR, PropositoVerificacion.REGISTRO, CanalVerificacion.WHATSAPP, "127.0.0.1")
        );

        assertEquals("RATE_LIMITED", exception.code());
        verify(verificarCelular, never()).iniciar(anyString(), any(), any());
    }

    @Test
    void phoneChangeRejectsANumberThatAlreadyHasAnAccount() {
        when(usuarios.existsByCelular(CELULAR)).thenReturn(true);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.solicitar(CELULAR, PropositoVerificacion.CAMBIO, CanalVerificacion.WHATSAPP, "127.0.0.1")
        );

        assertEquals("PHONE_ALREADY_REGISTERED", exception.code());
        verify(verificarCelular, never()).iniciar(anyString(), any(), any());
    }

    private Usuario user(RolUsuario rol, EstadoUsuario estado) {
        return new Usuario(
                UUID.randomUUID(), "Ana", CELULAR, "ana@example.com",
                "hashed:correct-password", rol, estado, null, NOW, NOW
        );
    }
}