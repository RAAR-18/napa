package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConfirmarVerificacionPort;
import co.edu.napa.cuentas.application.ports.in.PerfilUsuario;
import co.edu.napa.cuentas.application.ports.in.SolicitarVerificacionPort;
import co.edu.napa.cuentas.application.ports.out.AlmacenFotosPort;
import co.edu.napa.cuentas.application.ports.out.CambioCelularPendientePort;
import co.edu.napa.cuentas.application.ports.out.GestionUsuariosRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CambiarCelularYFotoUseCaseTest {
    private static final Instant AHORA = Instant.parse("2026-11-13T15:00:00Z");
    private static final UUID USUARIO = UUID.randomUUID();
    private static final String NUEVO = "+573109876543";

    private SolicitarVerificacionPort solicitar;
    private ConfirmarVerificacionPort confirmar;
    private CambioCelularPendientePort pendientes;
    private GestionUsuariosRepositoryPort usuarios;
    private AlmacenFotosPort fotos;
    private UsuarioRepositoryPort cuentas;
    private CambiarCelularYFotoUseCase useCase;

    @BeforeEach
    void setUp() {
        solicitar = mock(SolicitarVerificacionPort.class);
        confirmar = mock(ConfirmarVerificacionPort.class);
        pendientes = mock(CambioCelularPendientePort.class);
        usuarios = mock(GestionUsuariosRepositoryPort.class);
        fotos = mock(AlmacenFotosPort.class);
        cuentas = mock(UsuarioRepositoryPort.class);
        useCase = new CambiarCelularYFotoUseCase(solicitar, confirmar, pendientes, usuarios, fotos, cuentas,
                Clock.fixed(AHORA, ZoneOffset.UTC));
        when(usuarios.perfil(USUARIO)).thenReturn(Optional.of(new PerfilUsuario(USUARIO, "Ana", "+573001234567",
                null, null, "CLIENTE", "ACTIVA", true, AHORA, null)));
        when(usuarios.bloquear(USUARIO)).thenReturn(Optional.of(
                new GestionUsuariosRepositoryPort.RolEstado("CLIENTE", "ACTIVA")));
    }

    @Test
    void elCelularNuevoEsperaHastaConfirmarElCodigo() {
        useCase.solicitarCambioCelular(USUARIO, "CLIENTE", "310 987 6543", null, "127.0.0.1");

        verify(solicitar).solicitar(NUEVO, PropositoVerificacion.CAMBIO, CanalVerificacion.WHATSAPP, "127.0.0.1");
        verify(pendientes).guardar(USUARIO, NUEVO, CambiarCelularYFotoUseCase.VIGENCIA_CAMBIO);
        verify(usuarios, never()).actualizarCelular(any(), anyString(), any());

        when(pendientes.consultar(USUARIO)).thenReturn(Optional.of(NUEVO));
        useCase.confirmarCambioCelular(USUARIO, "CLIENTE", "654321");

        verify(confirmar).confirmar(NUEVO, PropositoVerificacion.CAMBIO, "654321");
        verify(usuarios).actualizarCelular(USUARIO, NUEVO, AHORA);
        verify(pendientes).borrar(USUARIO);
    }

    @Test
    void unCodigoIncorrectoOVencidoNoCambiaNada() {
        assertCodigo("OTP_EXPIRED", () -> useCase.confirmarCambioCelular(USUARIO, "CLIENTE", "654321"));
        when(pendientes.consultar(USUARIO)).thenReturn(Optional.of(NUEVO));
        doThrow(new DomainException("OTP_INVALID", "Código incorrecto")).when(confirmar)
                .confirmar(eq(NUEVO), eq(PropositoVerificacion.CAMBIO), anyString());
        assertCodigo("OTP_INVALID", () -> useCase.confirmarCambioCelular(USUARIO, "CLIENTE", "111111"));
        verify(usuarios, never()).actualizarCelular(any(), anyString(), any());
    }

    @Test
    void validaCelularMismoNumeroYRol() {
        assertCodigo("VALIDATION_ERROR", () -> useCase.solicitarCambioCelular(USUARIO, "CLIENTE", "abc", null, "ip"));
        assertCodigo("VALIDATION_ERROR", () ->
                useCase.solicitarCambioCelular(USUARIO, "CLIENTE", "+573001234567", null, "ip"));
        assertCodigo("FORBIDDEN", () -> useCase.solicitarCambioCelular(USUARIO, "ADMINISTRADOR", NUEVO, null, "ip"));
        when(cuentas.existsByCelular(NUEVO)).thenReturn(true);
        assertCodigo("PHONE_ALREADY_REGISTERED", () ->
                useCase.solicitarCambioCelular(USUARIO, "CLIENTE", NUEVO, null, "ip"));
        verify(solicitar, never()).solicitar(any(), any(), any(), any());
    }

    @Test
    void laFotoSeValidaPorSuContenido() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        when(fotos.guardar(USUARIO, png, "png")).thenReturn("/media/perfiles/a.png");

        assertEquals("/media/perfiles/a.png", useCase.cambiarFoto(USUARIO, png));
        verify(usuarios).actualizarFoto(USUARIO, "/media/perfiles/a.png", AHORA);
        assertEquals("jpg", CambiarCelularYFotoUseCase.extension(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}));
        assertEquals("webp", CambiarCelularYFotoUseCase.extension("RIFF0000WEBPVP8 ".getBytes()));
        assertCodigo("VALIDATION_ERROR", () -> useCase.cambiarFoto(USUARIO, "GIF89a....".getBytes()));
        assertCodigo("VALIDATION_ERROR", () ->
                useCase.cambiarFoto(USUARIO, new byte[CambiarCelularYFotoUseCase.FOTO_MAXIMA + 1]));
    }

    private static void assertCodigo(String codigo, Executable accion) {
        assertEquals(codigo, assertThrows(DomainException.class, accion).code());
    }
}
