package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.AdministrarUsuariosPort.Cambios;
import co.edu.napa.cuentas.application.ports.in.PerfilUsuario;
import co.edu.napa.cuentas.application.ports.out.GestionUsuariosRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.GestionUsuariosRepositoryPort.RolEstado;
import co.edu.napa.cuentas.application.ports.out.OperacionesPendientesPort;
import co.edu.napa.cuentas.application.ports.out.OperacionesPendientesPort.OperacionesPendientes;
import co.edu.napa.cuentas.domain.event.CuentaEliminadaEvent;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RF-08, RF-11, RF-66, RF-67 y RF-68. */
class GestionUsuariosUseCaseTest {
    private static final Instant AHORA = Instant.parse("2026-11-13T15:00:00Z");
    private static final UUID USUARIO = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final OperacionesPendientes NINGUNA = new OperacionesPendientes(0, 0, 0, false);

    private GestionUsuariosRepositoryPort usuarios;
    private OperacionesPendientesPort pendientes;
    private CierreCuenta cierre;
    private EventPublisherPort eventos;
    private GestionarCuentaPropiaUseCase propia;
    private AdministrarUsuariosUseCase administrar;

    @BeforeEach
    void setUp() {
        usuarios = mock(GestionUsuariosRepositoryPort.class);
        pendientes = mock(OperacionesPendientesPort.class);
        cierre = mock(CierreCuenta.class);
        eventos = mock(EventPublisherPort.class);
        Clock clock = Clock.fixed(AHORA, ZoneOffset.UTC);
        propia = new GestionarCuentaPropiaUseCase(usuarios, pendientes, cierre, eventos, clock);
        administrar = new AdministrarUsuariosUseCase(usuarios, pendientes, cierre, eventos, clock);
        when(usuarios.bloquear(USUARIO)).thenReturn(Optional.of(new RolEstado("CLIENTE", "ACTIVA")));
        when(usuarios.perfil(USUARIO)).thenReturn(Optional.of(new PerfilUsuario(USUARIO, "Ana", "+573001234567",
                null, null, "CLIENTE", "ACTIVA", true, AHORA, null)));
        when(pendientes.contar(USUARIO)).thenReturn(NINGUNA);
    }

    @Test
    void elUsuarioEditaNombreYUbicacionValidados() {
        propia.editar(USUARIO, "  Ana María ", new PerfilUsuario.Ubicacion(11.24, -74.21, " Calle 15 ", " "));

        verify(usuarios).actualizarNombre(USUARIO, "Ana María", AHORA);
        verify(usuarios).guardarUbicacion(USUARIO, new PerfilUsuario.Ubicacion(11.24, -74.21, "Calle 15", null), AHORA);
    }

    @Test
    void datosInvalidosNoSeGuardan() {
        assertCodigo("VALIDATION_ERROR", () -> propia.editar(USUARIO, null, null));
        assertCodigo("VALIDATION_ERROR", () -> propia.editar(USUARIO, " ", null));
        assertCodigo("VALIDATION_ERROR", () -> propia.editar(USUARIO, null,
                new PerfilUsuario.Ubicacion(91, 0, "Calle", null)));
        assertCodigo("VALIDATION_ERROR", () -> propia.editar(USUARIO, null,
                new PerfilUsuario.Ubicacion(0, 0, " ", null)));
        verify(usuarios, never()).actualizarNombre(any(), anyString(), any());
    }

    @Test
    void elUsuarioEliminaSuCuentaCierraSesionesYEmiteE18() {
        propia.eliminar(USUARIO);

        verify(usuarios).marcarEliminada(USUARIO, AHORA);
        verify(cierre).cerrarSesiones(USUARIO, AHORA);
        ArgumentCaptor<CuentaEliminadaEvent> evento = ArgumentCaptor.forClass(CuentaEliminadaEvent.class);
        verify(eventos).publish(evento.capture());
        assertEquals(Map.of("usuarioId", USUARIO), evento.getValue().payload());
    }

    @Test
    void conOperacionesPendientesNoSeEliminaYSoloSeInformanLasDistintasDeCero() {
        when(pendientes.contar(USUARIO)).thenReturn(new OperacionesPendientes(2, 0, 1, false));

        DomainException e = assertCodigo("HAS_PENDING_OPERATIONS", () -> propia.eliminar(USUARIO));

        assertEquals(Map.of("pedidosPendientes", 2L, "tarifasPendientes", 1L), e.details());
        verify(usuarios, never()).marcarEliminada(any(), any());
    }

    @Test
    void elAdministradorNoEliminaSuPropiaCuentaPorLaRutaDelUsuario() {
        when(usuarios.bloquear(ADMIN)).thenReturn(Optional.of(new RolEstado("ADMINISTRADOR", "ACTIVA")));

        assertCodigo("FORBIDDEN", () -> propia.eliminar(ADMIN));
    }

    @Test
    void elAdministradorCambiaElRolSoloSinPendientesYCierraLasSesiones() {
        administrar.editar(ADMIN, USUARIO, new Cambios(null, null, null, "DOMICILIARIO", null));
        verify(usuarios).actualizarRol(USUARIO, "DOMICILIARIO", AHORA);
        verify(cierre).cerrarSesiones(USUARIO, AHORA);

        when(pendientes.contar(USUARIO)).thenReturn(new OperacionesPendientes(0, 0, 0, true));
        assertCodigo("HAS_PENDING_OPERATIONS", () ->
                administrar.editar(ADMIN, USUARIO, new Cambios(null, null, null, "VENDEDOR_AMBULANTE", null)));
    }

    @Test
    void elAdministradorNoAsignaNiEditaAdministradores() {
        assertCodigo("BUSINESS_RULE", () ->
                administrar.editar(ADMIN, USUARIO, new Cambios(null, null, null, "ADMINISTRADOR", null)));
        when(usuarios.bloquear(ADMIN)).thenReturn(Optional.of(new RolEstado("ADMINISTRADOR", "ACTIVA")));
        assertCodigo("BUSINESS_RULE", () ->
                administrar.editar(ADMIN, ADMIN, new Cambios("Otro", null, null, null, null)));
    }

    @Test
    void elAdministradorFijaCelularYCorreoNormalizados() {
        administrar.editar(ADMIN, USUARIO, new Cambios(null, "300 123 4567", " Ana@Example.COM ", null, null));

        verify(usuarios).actualizarCelular(USUARIO, "+573001234567", AHORA);
        verify(usuarios).actualizarCorreo(USUARIO, "ana@example.com", AHORA);
        verify(cierre, never()).cerrarSesiones(any(), any());
        assertCodigo("VALIDATION_ERROR", () ->
                administrar.editar(ADMIN, USUARIO, new Cambios(null, null, "sin-arroba", null, null)));
    }

    @Test
    void elAdministradorEliminaConAdvertenciaYForzar() {
        when(pendientes.contar(USUARIO)).thenReturn(new OperacionesPendientes(0, 1, 0, true));

        assertCodigo("HAS_PENDING_OPERATIONS", () -> administrar.eliminar(ADMIN, USUARIO, false));
        administrar.eliminar(ADMIN, USUARIO, true);

        verify(usuarios).eliminarEmprendimientoActivo(USUARIO, AHORA);
        verify(usuarios).marcarEliminada(USUARIO, AHORA);
        verify(cierre).cerrarSesiones(USUARIO, AHORA);
    }

    @Test
    void elAdministradorNoSeEliminaASiMismoNiAUnaCuentaYaEliminada() {
        when(usuarios.bloquear(ADMIN)).thenReturn(Optional.of(new RolEstado("ADMINISTRADOR", "ACTIVA")));
        assertCodigo("BUSINESS_RULE", () -> administrar.eliminar(ADMIN, ADMIN, true));
        when(usuarios.bloquear(USUARIO)).thenReturn(Optional.of(new RolEstado("CLIENTE", "ELIMINADA")));
        assertCodigo("INVALID_STATE", () -> administrar.eliminar(ADMIN, USUARIO, true));
        assertCodigo("NOT_FOUND", () -> administrar.consultar(UUID.randomUUID()));
    }

    private static DomainException assertCodigo(String codigo, Executable accion) {
        DomainException e = assertThrows(DomainException.class, accion);
        assertEquals(codigo, e.code());
        return e;
    }
}
