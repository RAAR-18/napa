package co.edu.napa.domicilios.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarContactoUsuarioPort;
import co.edu.napa.cuentas.application.ports.in.ConsultarContactoUsuarioPort.Contacto;
import co.edu.napa.domicilios.application.ports.in.ListarDomiciliosDisponiblesPort.Orden;
import co.edu.napa.domicilios.application.ports.out.DomicilioDisponibleProjection;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort.OrdenDisponibles;
import co.edu.napa.domicilios.domain.event.DomicilioAsignadoEvent;
import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.domicilios.domain.model.EstadoDomicilio;
import co.edu.napa.domicilios.domain.model.EventoTrazabilidad;
import co.edu.napa.pedidos.application.ports.in.PedidoParaDomicilioPort;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.application.ports.out.UbicacionRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.GeoPoint;
import co.edu.napa.shared.domain.geo.Ubicacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DomiciliarioUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-13T15:00:00Z");
    private static final UUID YO = UUID.randomUUID();
    private static final UUID ORIGEN = UUID.randomUUID();
    private static final UUID DESTINO = UUID.randomUUID();
    private static final long RADIO = 5_000;
    private static final GeoPoint AQUI = new GeoPoint(11.24, -74.21);

    private DomicilioRepositoryPort domicilios;
    private UbicacionRepositoryPort ubicaciones;
    private PedidoParaDomicilioPort pedidos;
    private ConsultarContactoUsuarioPort usuarios;
    private EventPublisherPort eventos;
    private DomiciliarioUseCase useCase;
    private Domicilio disponible;

    @BeforeEach
    void setUp() {
        domicilios = mock(DomicilioRepositoryPort.class);
        ubicaciones = mock(UbicacionRepositoryPort.class);
        pedidos = mock(PedidoParaDomicilioPort.class);
        usuarios = mock(ConsultarContactoUsuarioPort.class);
        eventos = mock(EventPublisherPort.class);
        useCase = new DomiciliarioUseCase(domicilios, ubicaciones, pedidos, usuarios, eventos,
                Clock.fixed(NOW, ZoneOffset.UTC), () -> "048213", RADIO);
        disponible = Domicilio.publicar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 4_500,
                ORIGEN, DESTINO, NOW.minusSeconds(600));
        when(ubicaciones.findById(ORIGEN)).thenReturn(Optional.of(
                new Ubicacion(ORIGEN, 11.2408, -74.2110, "Carrera 5 # 10-20", null, NOW, NOW)));
        when(ubicaciones.findById(DESTINO)).thenReturn(Optional.of(
                new Ubicacion(DESTINO, 11.250149, -74.200231, "Calle 15 # 3-20", "Casa azul", "Pescaíto", NOW, NOW)));
        when(usuarios.consultar(disponible.compradorId()))
                .thenReturn(Optional.of(new Contacto("Ana Pérez", "+573001112233")));
        when(pedidos.cantidadItems(disponible.pedidoId())).thenReturn(3);
        when(domicilios.distanciaMetros(ORIGEN, DESTINO)).thenReturn(1_850L);
        when(domicilios.listarEnCurso(YO)).thenReturn(List.of());
    }

    @Test
    void laListaOcultaLaDireccionExactaDelDestino() {
        Ubicacion origen = ubicaciones.findById(ORIGEN).orElseThrow();
        Ubicacion destino = ubicaciones.findById(DESTINO).orElseThrow();
        when(domicilios.listarDisponibles(eq(YO), eq(OrdenDisponibles.GANANCIA), eq(AQUI), eq(RADIO), eq(0L),
                eq(20))).thenReturn(List.of(new DomicilioDisponibleProjection(disponible.id(), 4_500, NOW,
                        origen, destino, 1_850, 900L)));
        when(domicilios.contarDisponibles(YO, AQUI, RADIO)).thenReturn(1L);

        var pagina = useCase.listar(YO, Orden.GANANCIA, AQUI, 0, 20);

        var item = pagina.content().getFirst();
        assertEquals(4_500, item.ganancia());
        assertEquals("Carrera 5 # 10-20", item.origen().direccion());
        assertNull(item.destino().direccion());
        assertNull(item.destino().referencia());
        assertTrue(item.destino().aproximado());
        assertEquals(11.25, item.destino().latitud());
        assertEquals(-74.2, item.destino().longitud());
        assertEquals("Pescaíto", item.destino().barrio());
        assertEquals(RADIO, pagina.radioMetros());
    }

    @Test
    void laListaExigeLaPosicionYFiltraPorElRadio() {
        DomainException error = assertThrows(DomainException.class,
                () -> useCase.listar(YO, Orden.RECIENTES, null, 0, 20));
        assertEquals("VALIDATION_ERROR", error.code());

        useCase.listar(YO, Orden.CERCANIA, AQUI, 0, 20);
        verify(domicilios).listarDisponibles(YO, OrdenDisponibles.CERCANIA, AQUI, RADIO, 0, 20);
        verify(domicilios).contarDisponibles(YO, AQUI, RADIO);
    }

    @Test
    void antesDeAceptarElDetalleNoRevelaDireccionNiTelefono() {
        when(domicilios.buscar(disponible.id())).thenReturn(Optional.of(disponible));

        var detalle = useCase.consultar(YO, disponible.id());

        assertFalse(detalle.asignadoAMi());
        assertFalse(detalle.rutaDisponible());
        assertEquals("Ana Pérez", detalle.receptor().nombre());
        assertNull(detalle.receptor().celular());
        assertNull(detalle.destino().direccion());
        assertEquals(3, detalle.resumenItems().cantidadItems());
        assertEquals(1_850, detalle.distanciaMetros());
    }

    @Test
    void elDomiciliarioAsignadoVeDireccionYTelefono() {
        Domicilio mio = disponible.asignar(YO, "048213", NOW);
        when(domicilios.buscar(mio.id())).thenReturn(Optional.of(mio));

        var detalle = useCase.consultar(YO, mio.id());

        assertTrue(detalle.asignadoAMi());
        assertTrue(detalle.rutaDisponible());
        assertEquals("+573001112233", detalle.receptor().celular());
        assertEquals("Calle 15 # 3-20", detalle.destino().direccion());
        assertEquals("Casa azul", detalle.destino().referencia());
    }

    @Test
    void unDomicilioTomadoPorOtroYaNoEstaDisponible() {
        Domicilio ajeno = disponible.asignar(UUID.randomUUID(), "111111", NOW);
        when(domicilios.buscar(ajeno.id())).thenReturn(Optional.of(ajeno));

        assertEquals("NOT_AVAILABLE",
                assertThrows(DomainException.class, () -> useCase.consultar(YO, ajeno.id())).code());
    }

    @Test
    void unDomicilioRechazadoNoSeVuelveAMostrar() {
        when(domicilios.buscar(disponible.id())).thenReturn(Optional.of(disponible));
        when(domicilios.fueRechazadoPor(YO, disponible.id())).thenReturn(true);

        assertEquals("NOT_AVAILABLE",
                assertThrows(DomainException.class, () -> useCase.consultar(YO, disponible.id())).code());
    }

    @Test
    void rechazaUnDomicilioDisponible() {
        when(domicilios.buscar(disponible.id())).thenReturn(Optional.of(disponible));

        useCase.rechazar(YO, disponible.id());

        verify(domicilios).registrarRechazo(YO, disponible.id(), NOW);
        verify(eventos, never()).publish(any());
    }

    @Test
    void noSeRechazaUnDomicilioQueYaNoEstaDisponible() {
        Domicilio ajeno = disponible.asignar(UUID.randomUUID(), "111111", NOW);
        when(domicilios.buscar(ajeno.id())).thenReturn(Optional.of(ajeno));

        assertEquals("NOT_AVAILABLE",
                assertThrows(DomainException.class, () -> useCase.rechazar(YO, ajeno.id())).code());
        verify(domicilios, never()).registrarRechazo(any(), any(), any());
    }

    @Test
    void aceptaAsignaGeneraCodigoTrazaYAvisa() {
        when(domicilios.bloquear(disponible.id())).thenReturn(Optional.of(disponible));

        var aceptado = useCase.aceptar(YO, disponible.id());

        assertEquals("ASIGNADO", aceptado.estado());
        assertEquals(4_500, aceptado.ganancia());
        assertTrue(aceptado.rutaDisponible());

        InOrder orden = inOrder(domicilios);
        orden.verify(domicilios).bloquearDomiciliario(YO);
        orden.verify(domicilios).bloquear(disponible.id());

        ArgumentCaptor<Domicilio> guardado = ArgumentCaptor.forClass(Domicilio.class);
        verify(domicilios).actualizar(guardado.capture());
        assertEquals(EstadoDomicilio.ASIGNADO, guardado.getValue().estado());
        assertEquals(YO, guardado.getValue().domiciliarioId());
        assertEquals("048213", guardado.getValue().codigoConfirmacion());

        ArgumentCaptor<EventoTrazabilidad> traza = ArgumentCaptor.forClass(EventoTrazabilidad.class);
        verify(domicilios).registrarEvento(traza.capture());
        assertEquals(EstadoDomicilio.ASIGNADO, traza.getValue().estado());
        assertEquals(YO, traza.getValue().usuarioId());

        ArgumentCaptor<DomicilioAsignadoEvent> evento = ArgumentCaptor.forClass(DomicilioAsignadoEvent.class);
        verify(eventos).publish(evento.capture());
        assertEquals(disponible.compradorId(), evento.getValue().payload().get("compradorId"));
        assertEquals(YO, evento.getValue().payload().get("domiciliarioId"));
        assertFalse(evento.getValue().payload().containsKey("codigo"), "el código no viaja en el evento");
    }

    @Test
    void noAceptaUnCuartoDomicilioEnCurso() {
        when(domicilios.bloquear(disponible.id())).thenReturn(Optional.of(disponible));
        Domicilio enCurso = Domicilio.publicar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3_000,
                UUID.randomUUID(), UUID.randomUUID(), NOW).asignar(YO, "123456", NOW);
        when(domicilios.listarEnCurso(YO)).thenReturn(Collections.nCopies(3, enCurso));

        DomainException error = assertThrows(DomainException.class, () -> useCase.aceptar(YO, disponible.id()));

        assertEquals("MAX_ACTIVE_DELIVERIES", error.code());
        assertEquals(3, error.details().get("enCurso"));
        verify(domicilios, never()).actualizar(any());
        verify(eventos, never()).publish(any());
    }

    @Test
    void conDosEnCursoTodaviaPuedeAceptar() {
        when(domicilios.bloquear(disponible.id())).thenReturn(Optional.of(disponible));
        Domicilio enCurso = Domicilio.publicar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3_000,
                UUID.randomUUID(), UUID.randomUUID(), NOW).asignar(YO, "123456", NOW);
        when(domicilios.listarEnCurso(YO)).thenReturn(List.of(enCurso, enCurso));

        assertEquals("ASIGNADO", useCase.aceptar(YO, disponible.id()).estado());
    }

    @Test
    void noAceptaUnDomicilioYaTomadoORechazado() {
        Domicilio ajeno = disponible.asignar(UUID.randomUUID(), "111111", NOW);
        when(domicilios.bloquear(ajeno.id())).thenReturn(Optional.of(ajeno));
        assertEquals("NOT_AVAILABLE",
                assertThrows(DomainException.class, () -> useCase.aceptar(YO, ajeno.id())).code());

        Domicilio otro = Domicilio.publicar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3_000,
                ORIGEN, DESTINO, NOW);
        when(domicilios.bloquear(otro.id())).thenReturn(Optional.of(otro));
        when(domicilios.fueRechazadoPor(YO, otro.id())).thenReturn(true);
        assertEquals("NOT_AVAILABLE",
                assertThrows(DomainException.class, () -> useCase.aceptar(YO, otro.id())).code());
        verify(eventos, never()).publish(any());
    }

    @Test
    void listaSusDomiciliosEnCursoConDireccionExacta() {
        Domicilio mio = disponible.asignar(YO, "048213", NOW);
        when(domicilios.listarEnCurso(YO)).thenReturn(List.of(mio));

        var items = useCase.listar(YO);

        assertEquals(1, items.size());
        assertEquals("ASIGNADO", items.getFirst().estado());
        assertEquals("Calle 15 # 3-20", items.getFirst().destino().direccion());
        assertInstanceOf(UUID.class, items.getFirst().id());
    }
}
