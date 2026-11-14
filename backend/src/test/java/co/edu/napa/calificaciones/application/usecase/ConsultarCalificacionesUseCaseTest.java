package co.edu.napa.calificaciones.application.usecase;

import co.edu.napa.calificaciones.application.ports.in.ConsultarCalificacionesPort.CalificacionPublica;
import co.edu.napa.calificaciones.application.ports.in.ConsultarCalificacionesPort.Consulta;
import co.edu.napa.calificaciones.application.ports.in.ConsultarCalificacionesPort.Pagina;
import co.edu.napa.calificaciones.application.ports.out.CalificacionRepositoryPort;
import co.edu.napa.calificaciones.application.ports.out.CalificacionRepositoryPort.CalificacionListada;
import co.edu.napa.calificaciones.application.ports.out.CalificacionRepositoryPort.Filtro;
import co.edu.napa.calificaciones.application.ports.out.InteraccionesPort;
import co.edu.napa.calificaciones.domain.model.EstadoCalificacion;
import co.edu.napa.calificaciones.domain.model.RolUsuario;
import co.edu.napa.calificaciones.domain.model.TipoEntidad;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsultarCalificacionesUseCaseTest {
    private static final UUID USUARIO = UUID.randomUUID();
    private static final UUID EMPRENDIMIENTO = UUID.randomUUID();
    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID DOMICILIARIO = UUID.randomUUID();

    private CalificacionRepositoryPort repositorio;
    private InteraccionesPort interacciones;
    private ConsultarCalificacionesUseCase useCase;

    @BeforeEach
    void setUp() {
        repositorio = mock(CalificacionRepositoryPort.class);
        interacciones = mock(InteraccionesPort.class);
        useCase = new ConsultarCalificacionesUseCase(repositorio, interacciones);
        when(repositorio.resumen(any(), any())).thenReturn(new CalificacionRepositoryPort.Resumen(4.56, 9));
        when(interacciones.duenioEmprendimiento(EMPRENDIMIENTO)).thenReturn(Optional.of(USUARIO));
        when(interacciones.rolUsuario(CLIENTE)).thenReturn(Optional.of("CLIENTE"));
        when(interacciones.rolUsuario(DOMICILIARIO)).thenReturn(Optional.of("DOMICILIARIO"));
    }

    private static Consulta consulta(TipoEntidad tipo, UUID id) {
        return new Consulta(tipo, id, null, 0, 20, null, false);
    }

    @Test
    void elClienteVeLasActivasConPromedioDeUnDecimalYMarcaLasSuyas() {
        UUID otro = UUID.randomUUID();
        when(repositorio.listar(eq(new Filtro(TipoEntidad.EMPRENDIMIENTO, EMPRENDIMIENTO, EstadoCalificacion.ACTIVA)),
                eq(0), eq(20), any())).thenReturn(List.of(
                listada(USUARIO), listada(otro)));
        when(repositorio.contar(any())).thenReturn(2L);

        Pagina<CalificacionPublica> pagina = useCase.consultar(USUARIO, RolUsuario.CLIENTE,
                consulta(TipoEntidad.EMPRENDIMIENTO, EMPRENDIMIENTO));

        assertEquals(2, pagina.totalElements());
        assertTrue(pagina.content().get(0).esMia());
        assertFalse(pagina.content().get(1).esMia());
        assertEquals(4.6, pagina.resumen().promedio());
        assertEquals(9, pagina.resumen().total());
    }

    @Test
    void sinCalificacionesElPromedioEsNulo() {
        when(repositorio.resumen(TipoEntidad.USUARIO, DOMICILIARIO))
                .thenReturn(new CalificacionRepositoryPort.Resumen(null, 0));

        assertNull(useCase.resumen(TipoEntidad.USUARIO, DOMICILIARIO).promedio());
    }

    @Test
    void elVendedorSoloVeSuEmprendimientoYClientesQueLeCompraron() {
        assertCodigo("FORBIDDEN", () -> useCase.consultar(UUID.randomUUID(), RolUsuario.VENDEDOR_AMBULANTE,
                consulta(TipoEntidad.EMPRENDIMIENTO, EMPRENDIMIENTO)));
        assertCodigo("FORBIDDEN", () -> useCase.consultar(USUARIO, RolUsuario.VENDEDOR_PUNTO_FIJO,
                consulta(TipoEntidad.USUARIO, CLIENTE)));
        when(interacciones.clienteComproAVendedor(USUARIO, CLIENTE)).thenReturn(true);
        useCase.consultar(USUARIO, RolUsuario.VENDEDOR_PUNTO_FIJO, consulta(TipoEntidad.USUARIO, CLIENTE));
    }

    @Test
    void elVendedorAmbulanteNoVeDomiciliariosYElDePuntoFijoSoloLosSuyos() {
        assertCodigo("FORBIDDEN", () -> useCase.consultar(USUARIO, RolUsuario.VENDEDOR_AMBULANTE,
                consulta(TipoEntidad.USUARIO, DOMICILIARIO)));
        assertCodigo("FORBIDDEN", () -> useCase.consultar(USUARIO, RolUsuario.VENDEDOR_PUNTO_FIJO,
                consulta(TipoEntidad.USUARIO, DOMICILIARIO)));
        when(interacciones.domiciliarioLlevoPedidoDeVendedor(USUARIO, DOMICILIARIO)).thenReturn(true);
        useCase.consultar(USUARIO, RolUsuario.VENDEDOR_PUNTO_FIJO, consulta(TipoEntidad.USUARIO, DOMICILIARIO));
        useCase.consultar(USUARIO, RolUsuario.DOMICILIARIO, consulta(TipoEntidad.USUARIO, DOMICILIARIO));
    }

    @Test
    void usuariosQueNoSonClientesNiDomiciliariosYEntidadesInexistentesRespondenNotFound() {
        UUID vendedor = UUID.randomUUID();
        when(interacciones.rolUsuario(vendedor)).thenReturn(Optional.of("VENDEDOR_AMBULANTE"));

        assertCodigo("NOT_FOUND", () -> useCase.consultar(USUARIO, RolUsuario.CLIENTE,
                consulta(TipoEntidad.USUARIO, vendedor)));
        assertCodigo("NOT_FOUND", () -> useCase.consultar(USUARIO, RolUsuario.CLIENTE,
                consulta(TipoEntidad.PRODUCTO, UUID.randomUUID())));
        assertCodigo("VALIDATION_ERROR", () -> useCase.consultar(USUARIO, RolUsuario.CLIENTE,
                consulta(null, null)));
    }

    @Test
    void elAdministradorSupervisaSinEntidadYPorDefectoSoloActivas() {
        when(repositorio.listar(any(), anyInt(), anyInt(), any())).thenReturn(List.of(listada(CLIENTE)));

        var pagina = useCase.supervisar(new Consulta(null, null, null, 0, 20, null, false));

        assertNull(pagina.resumen());
        assertEquals("CLIENTE", pagina.content().getFirst().autor().rol());
        verify(repositorio).contar(new Filtro(null, null, EstadoCalificacion.ACTIVA));
        assertCodigo("VALIDATION_ERROR", () -> useCase.supervisar(
                new Consulta(null, UUID.randomUUID(), null, 0, 20, null, false)));
    }

    private static CalificacionListada listada(UUID autor) {
        Instant fecha = Instant.parse("2026-11-12T10:00:00Z");
        return new CalificacionListada(UUID.randomUUID(), 5, "Excelente", EstadoCalificacion.ACTIVA, false,
                TipoEntidad.EMPRENDIMIENTO, EMPRENDIMIENTO, "Frutas Ana", UUID.randomUUID(), autor, "María",
                "CLIENTE", fecha, fecha);
    }

    private static void assertCodigo(String codigo, Executable accion) {
        assertEquals(codigo, assertThrows(DomainException.class, accion).code());
    }
}
