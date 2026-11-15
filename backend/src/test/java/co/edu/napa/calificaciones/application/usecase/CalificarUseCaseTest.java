package co.edu.napa.calificaciones.application.usecase;

import co.edu.napa.calificaciones.application.ports.in.CalificarPort.NuevaCalificacion;
import co.edu.napa.calificaciones.application.ports.out.CalificacionRepositoryPort;
import co.edu.napa.calificaciones.application.ports.out.InteraccionesPort;
import co.edu.napa.calificaciones.application.ports.out.InteraccionesPort.ParticipantesPedido;
import co.edu.napa.calificaciones.domain.model.Calificacion;
import co.edu.napa.calificaciones.domain.model.EstadoCalificacion;
import co.edu.napa.calificaciones.domain.model.RolUsuario;
import co.edu.napa.calificaciones.domain.model.TipoEntidad;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalificarUseCaseTest {
    private static final Instant AHORA = Instant.parse("2026-11-13T15:00:00Z");
    private static final UUID PEDIDO = UUID.randomUUID();
    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID VENDEDOR = UUID.randomUUID();
    private static final UUID EMPRENDIMIENTO = UUID.randomUUID();
    private static final UUID PRODUCTO = UUID.randomUUID();
    private static final UUID DOMICILIARIO = UUID.randomUUID();

    private CalificacionRepositoryPort repositorio;
    private InteraccionesPort interacciones;
    private CalificarUseCase useCase;

    @BeforeEach
    void setUp() {
        repositorio = mock(CalificacionRepositoryPort.class);
        interacciones = mock(InteraccionesPort.class);
        useCase = new CalificarUseCase(repositorio, interacciones, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private void pedido(String estado, UUID domiciliario) {
        when(interacciones.participantes(PEDIDO)).thenReturn(Optional.of(new ParticipantesPedido(
                PEDIDO, CLIENTE, VENDEDOR, EMPRENDIMIENTO, estado, Set.of(PRODUCTO), domiciliario)));
    }

    private static NuevaCalificacion datos(TipoEntidad tipo, UUID entidad) {
        return new NuevaCalificacion(PEDIDO, tipo, entidad, 5, "  Muy frescas  ");
    }

    @Test
    void elClienteCalificaElEmprendimientoDeUnPedidoEntregado() {
        pedido("ENTREGADO", null);

        Calificacion c = useCase.calificar(CLIENTE, RolUsuario.CLIENTE, datos(TipoEntidad.EMPRENDIMIENTO, EMPRENDIMIENTO));

        assertEquals(EstadoCalificacion.ACTIVA, c.estado());
        assertEquals("Muy frescas", c.comentario());
        assertEquals(AHORA, c.createdAt());
        verify(repositorio).insertar(c);
    }

    @Test
    void elClienteCalificaUnProductoDelPedidoYAlDomiciliario() {
        pedido("ENTREGADO", DOMICILIARIO);

        useCase.calificar(CLIENTE, RolUsuario.CLIENTE, datos(TipoEntidad.PRODUCTO, PRODUCTO));
        useCase.calificar(CLIENTE, RolUsuario.CLIENTE, datos(TipoEntidad.USUARIO, DOMICILIARIO));
    }

    @Test
    void elComentarioEsOpcional() {
        pedido("ENTREGADO", null);

        Calificacion c = useCase.calificar(CLIENTE, RolUsuario.CLIENTE,
                new NuevaCalificacion(PEDIDO, TipoEntidad.EMPRENDIMIENTO, EMPRENDIMIENTO, 4, "   "));

        assertNull(c.comentario());
    }

    @Test
    void elVendedorCalificaAlClienteYElDePuntoFijoAlDomiciliario() {
        pedido("ENTREGADO", DOMICILIARIO);

        useCase.calificar(VENDEDOR, RolUsuario.VENDEDOR_AMBULANTE, datos(TipoEntidad.USUARIO, CLIENTE));
        useCase.calificar(VENDEDOR, RolUsuario.VENDEDOR_PUNTO_FIJO, datos(TipoEntidad.USUARIO, DOMICILIARIO));
        assertCodigo("NOT_FOUND", () ->
                useCase.calificar(VENDEDOR, RolUsuario.VENDEDOR_AMBULANTE, datos(TipoEntidad.USUARIO, DOMICILIARIO)));
    }

    @Test
    void elDomiciliarioCalificaEmprendimientoYClientePeroNoProductos() {
        pedido("ENTREGADO", DOMICILIARIO);

        useCase.calificar(DOMICILIARIO, RolUsuario.DOMICILIARIO, datos(TipoEntidad.EMPRENDIMIENTO, EMPRENDIMIENTO));
        useCase.calificar(DOMICILIARIO, RolUsuario.DOMICILIARIO, datos(TipoEntidad.USUARIO, CLIENTE));
        assertCodigo("NOT_FOUND", () ->
                useCase.calificar(DOMICILIARIO, RolUsuario.DOMICILIARIO, datos(TipoEntidad.PRODUCTO, PRODUCTO)));
    }

    @Test
    void unExtranoNoDescubreElEstadoDelPedido() {
        pedido("PENDIENTE", null);

        assertCodigo("NOT_FOUND", () -> useCase.calificar(UUID.randomUUID(), RolUsuario.CLIENTE,
                datos(TipoEntidad.EMPRENDIMIENTO, EMPRENDIMIENTO)));
    }

    @Test
    void unPedidoNoEntregadoRespondeInvalidState() {
        pedido("EN_CAMINO", null);

        DomainException e = assertCodigo("INVALID_STATE", () ->
                useCase.calificar(CLIENTE, RolUsuario.CLIENTE, datos(TipoEntidad.EMPRENDIMIENTO, EMPRENDIMIENTO)));
        assertEquals("EN_CAMINO", e.details().get("estadoActual"));
    }

    @Test
    void unDestinoFueraDelPedidoONadieSeCalificaASiMismo() {
        pedido("ENTREGADO", CLIENTE);

        assertCodigo("NOT_FOUND", () -> useCase.calificar(CLIENTE, RolUsuario.CLIENTE,
                datos(TipoEntidad.PRODUCTO, UUID.randomUUID())));
        assertCodigo("NOT_FOUND", () -> useCase.calificar(CLIENTE, RolUsuario.CLIENTE,
                datos(TipoEntidad.USUARIO, CLIENTE)));
    }

    @Test
    void laCalificacionRepetidaRespondeConflict() {
        pedido("ENTREGADO", null);
        when(repositorio.existe(CLIENTE, PEDIDO, TipoEntidad.EMPRENDIMIENTO, EMPRENDIMIENTO)).thenReturn(true);

        assertCodigo("CONFLICT", () ->
                useCase.calificar(CLIENTE, RolUsuario.CLIENTE, datos(TipoEntidad.EMPRENDIMIENTO, EMPRENDIMIENTO)));
        verify(repositorio, never()).insertar(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, -1})
    void elPuntajeFueraDeEscalaSeRechazaAntesDeLeerElPedido(int puntaje) {
        assertCodigo("VALIDATION_ERROR", () -> useCase.calificar(CLIENTE, RolUsuario.CLIENTE,
                new NuevaCalificacion(PEDIDO, TipoEntidad.EMPRENDIMIENTO, EMPRENDIMIENTO, puntaje, null)));
        verify(interacciones, never()).participantes(any());
    }

    @Test
    void elComentarioDeMasDe300SeRechaza() {
        assertCodigo("VALIDATION_ERROR", () -> useCase.calificar(CLIENTE, RolUsuario.CLIENTE,
                new NuevaCalificacion(PEDIDO, TipoEntidad.EMPRENDIMIENTO, EMPRENDIMIENTO, 3, "x".repeat(301))));
    }

    @Test
    void elAdministradorNoCalifica() {
        assertCodigo("FORBIDDEN", () -> useCase.calificar(UUID.randomUUID(), RolUsuario.ADMINISTRADOR,
                datos(TipoEntidad.EMPRENDIMIENTO, EMPRENDIMIENTO)));
    }

    private static DomainException assertCodigo(String codigo, org.junit.jupiter.api.function.Executable accion) {
        DomainException e = assertThrows(DomainException.class, accion);
        assertEquals(codigo, e.code());
        return e;
    }
}
