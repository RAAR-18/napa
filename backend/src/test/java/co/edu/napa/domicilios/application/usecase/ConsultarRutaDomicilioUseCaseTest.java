package co.edu.napa.domicilios.application.usecase;

import co.edu.napa.domicilios.application.ports.in.ConsultarRutaDomicilioPort.Ruta;
import co.edu.napa.domicilios.application.ports.in.ConsultarRutaDomicilioPort.TipoTramo;
import co.edu.napa.domicilios.application.ports.in.ConsultarRutaDomicilioPort.Tramo;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.shared.application.ports.out.RoutingPort;
import co.edu.napa.shared.application.ports.out.UbicacionRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.GeoPoint;
import co.edu.napa.shared.domain.geo.RouteResult;
import co.edu.napa.shared.domain.geo.Ubicacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsultarRutaDomicilioUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-13T15:00:00Z");
    private static final UUID DOMICILIARIO = UUID.randomUUID();
    private static final UUID ORIGEN = UUID.randomUUID();
    private static final UUID DESTINO = UUID.randomUUID();
    private static final GeoPoint A = new GeoPoint(11.2408, -74.2110);
    private static final GeoPoint B = new GeoPoint(11.2501, -74.2002);
    private static final GeoPoint AQUI = new GeoPoint(11.2350, -74.2150);

    private DomicilioRepositoryPort domicilios;
    private RoutingPort rutas;
    private ConsultarRutaDomicilioUseCase useCase;
    private Domicilio asignado;

    @BeforeEach
    void setUp() {
        domicilios = mock(DomicilioRepositoryPort.class);
        UbicacionRepositoryPort ubicaciones = mock(UbicacionRepositoryPort.class);
        rutas = mock(RoutingPort.class);
        useCase = new ConsultarRutaDomicilioUseCase(domicilios, ubicaciones, rutas, new BigDecimal("1.3"),
                Runnable::run);
        asignado = Domicilio.publicar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 4_500,
                        ORIGEN, DESTINO, NOW.minusSeconds(1_200))
                .asignar(DOMICILIARIO, "048213", NOW.minusSeconds(600));
        when(ubicaciones.findById(ORIGEN)).thenReturn(Optional.of(
                new Ubicacion(ORIGEN, A.latitude(), A.longitude(), "Carrera 5 # 10-20", null, NOW, NOW)));
        when(ubicaciones.findById(DESTINO)).thenReturn(Optional.of(new Ubicacion(DESTINO, B.latitude(),
                B.longitude(), "Calle 15 # 3-20", "Casa azul", "Pescaíto", NOW, NOW)));
        when(rutas.computeRoute(AQUI, A)).thenReturn(new RouteResult(620, Duration.ofSeconds(140), "hacia-a"));
        when(rutas.computeRoute(A, B)).thenReturn(new RouteResult(2_100, Duration.ofSeconds(420), "a-b"));
        when(rutas.computeRoute(AQUI, B)).thenReturn(new RouteResult(2_600, Duration.ofSeconds(560), "aqui-b"));
    }

    @Test
    void asignadoMuestraElTramoHaciaLaRecogidaYLuegoHaciaLaEntrega() {
        when(domicilios.buscar(asignado.id())).thenReturn(Optional.of(asignado));

        Ruta ruta = useCase.consultarRuta(DOMICILIARIO, asignado.id(), AQUI);

        assertEquals("ASIGNADO", ruta.estado());
        assertEquals(List.of(
                new Tramo(TipoTramo.HACIA_ORIGEN, 620, 140L, "hacia-a", false),
                new Tramo(TipoTramo.HACIA_DESTINO, 2_100, 420L, "a-b", false)), ruta.tramos());
        assertEquals("Calle 15 # 3-20", ruta.destino().direccion());
        assertEquals("Casa azul", ruta.destino().referencia());
        assertFalse(ruta.destino().aproximado());
    }

    @Test
    void sinPosicionSoloDevuelveElTramoDeAaB() {
        when(domicilios.buscar(asignado.id())).thenReturn(Optional.of(asignado));

        Ruta ruta = useCase.consultarRuta(DOMICILIARIO, asignado.id(), null);

        assertEquals(List.of(TipoTramo.HACIA_DESTINO), ruta.tramos().stream().map(Tramo::tipo).toList());
        assertEquals("a-b", ruta.tramos().getFirst().polilinea());
    }

    @Test
    void trasLaRecogidaElTramoParteDeLaPosicionHaciaLaEntrega() {
        Domicilio recogido = asignado.confirmarRecogida(NOW);
        when(domicilios.buscar(recogido.id())).thenReturn(Optional.of(recogido));

        Ruta ruta = useCase.consultarRuta(DOMICILIARIO, recogido.id(), AQUI);

        assertEquals(List.of(new Tramo(TipoTramo.HACIA_DESTINO, 2_600, 560L, "aqui-b", false)), ruta.tramos());
    }

    @Test
    void siElServicioDeRutasFallaElTramoEsAproximado() {
        when(domicilios.buscar(asignado.id())).thenReturn(Optional.of(asignado));
        when(rutas.computeRoute(A, B)).thenThrow(
                new DomainException("EXTERNAL_SERVICE_UNAVAILABLE", "No fue posible calcular la ruta."));

        Ruta ruta = useCase.consultarRuta(DOMICILIARIO, asignado.id(), null);

        Tramo tramo = ruta.tramos().getFirst();
        assertTrue(tramo.aproximado());
        assertNull(tramo.polilinea());
        assertNull(tramo.duracionSegundos());
        long lineaRecta = Math.round(ConsultarRutaDomicilioUseCase.lineaRectaMetros(A, B) * 1.3);
        assertEquals(lineaRecta, tramo.distanciaMetros());
        assertTrue(tramo.distanciaMetros() > 1_500 && tramo.distanciaMetros() < 2_500);
    }

    @Test
    void otroDomiciliarioNoTieneAcceso() {
        when(domicilios.buscar(asignado.id())).thenReturn(Optional.of(asignado));

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.consultarRuta(UUID.randomUUID(), asignado.id(), AQUI));

        assertEquals("FORBIDDEN", error.code());
        verify(rutas, never()).computeRoute(any(), any());
    }

    @Test
    void unDomicilioSinAsignarNoTieneRuta() {
        Domicilio disponible = Domicilio.publicar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 4_500,
                ORIGEN, DESTINO, NOW);
        when(domicilios.buscar(disponible.id())).thenReturn(Optional.of(disponible));

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.consultarRuta(DOMICILIARIO, disponible.id(), AQUI));

        assertEquals("FORBIDDEN", error.code());
    }

    @Test
    void unDomicilioEntregadoYaNoTieneRuta() {
        Domicilio entregado = asignado.confirmarRecogida(NOW).confirmarSalida(NOW).entregar(NOW);
        when(domicilios.buscar(entregado.id())).thenReturn(Optional.of(entregado));

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.consultarRuta(DOMICILIARIO, entregado.id(), AQUI));

        assertEquals("INVALID_STATE", error.code());
        assertEquals(Map.of("estadoActual", "ENTREGADO"), error.details());
    }

    @Test
    void unDomicilioInexistenteNoEstaDisponible() {
        UUID id = UUID.randomUUID();
        when(domicilios.buscar(id)).thenReturn(Optional.empty());

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.consultarRuta(DOMICILIARIO, id, AQUI));

        assertEquals("NOT_FOUND", error.code());
    }
}
