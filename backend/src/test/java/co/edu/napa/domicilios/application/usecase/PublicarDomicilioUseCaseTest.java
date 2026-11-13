package co.edu.napa.domicilios.application.usecase;

import co.edu.napa.domicilios.application.PlazosDomicilio;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import co.edu.napa.domicilios.domain.event.DomicilioPublicadoEvent;
import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.domicilios.domain.model.EstadoDomicilio;
import co.edu.napa.domicilios.domain.model.EventoTrazabilidad;
import co.edu.napa.emprendimientos.application.ports.in.ConsultarEmprendimientoParaPedidoPort;
import co.edu.napa.emprendimientos.application.ports.in.ConsultarEmprendimientoParaPedidoPort.Referencia;
import co.edu.napa.pagos.application.ports.in.ConsultarEstadoPagoPedidoPort;
import co.edu.napa.pedidos.application.ports.in.PedidoParaDomicilioPort;
import co.edu.napa.pedidos.application.ports.in.PedidoParaDomicilioPort.PedidoParaDomicilio;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.application.ports.out.UbicacionRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.GeoPoint;
import co.edu.napa.shared.domain.geo.Ubicacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicarDomicilioUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-13T15:00:00Z");
    private static final UUID PEDIDO = UUID.randomUUID();
    private static final UUID VENDEDOR = UUID.randomUUID();
    private static final UUID COMPRADOR = UUID.randomUUID();
    private static final UUID EMPRENDIMIENTO = UUID.randomUUID();
    private static final UUID PUNTO_FIJO = UUID.randomUUID();
    private static final UUID ENTREGA = UUID.randomUUID();

    private PedidoParaDomicilioPort pedidos;
    private ConsultarEstadoPagoPedidoPort pagos;
    private UbicacionRepositoryPort ubicaciones;
    private DomicilioRepositoryPort domicilios;
    private EventPublisherPort eventos;
    private PublicarDomicilioUseCase useCase;

    @BeforeEach
    void setUp() {
        pedidos = mock(PedidoParaDomicilioPort.class);
        pagos = mock(ConsultarEstadoPagoPedidoPort.class);
        ubicaciones = mock(UbicacionRepositoryPort.class);
        domicilios = mock(DomicilioRepositoryPort.class);
        eventos = mock(EventPublisherPort.class);
        ConsultarEmprendimientoParaPedidoPort emprendimientos = id -> Optional.of(
                new Referencia(EMPRENDIMIENTO, VENDEDOR, "Frutas Rosa", new GeoPoint(11.24, -74.21), PUNTO_FIJO));
        useCase = new PublicarDomicilioUseCase(pedidos, emprendimientos, pagos, ubicaciones, domicilios, eventos,
                PlazosDomicilio.POR_DEFECTO, Clock.fixed(NOW, ZoneOffset.UTC));

        when(pagos.consultar(PEDIDO)).thenReturn(Optional.of("CONFIRMADO"));
        when(domicilios.buscarActivoPorPedido(PEDIDO)).thenReturn(Optional.empty());
        when(ubicaciones.findById(PUNTO_FIJO)).thenReturn(Optional.of(
                new Ubicacion(PUNTO_FIJO, 11.2408, -74.2110, "Carrera 5 # 10-20", null, NOW, NOW)));
        when(ubicaciones.findById(ENTREGA)).thenReturn(Optional.of(
                new Ubicacion(ENTREGA, 11.2501, -74.2002, "Calle 15 # 3-20", "Casa azul", NOW, NOW)));
        when(ubicaciones.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void publicaUnPedidoDeDomicilioAceptadoYPagado() {
        pedido("DOMICILIO", "ACEPTADO", null);

        var publicado = useCase.publicar(PEDIDO, VENDEDOR);

        assertEquals("DISPONIBLE", publicado.estado());
        assertEquals(PEDIDO, publicado.pedidoId());
        assertEquals(4_500, publicado.ganancia());
        assertEquals("Carrera 5 # 10-20", publicado.origen().direccion());
        assertEquals("Casa azul", publicado.destino().referencia());

        ArgumentCaptor<Ubicacion> copia = ArgumentCaptor.forClass(Ubicacion.class);
        verify(ubicaciones).save(copia.capture());
        assertNotEquals(PUNTO_FIJO, copia.getValue().id(), "el punto A es una copia del punto fijo");
        assertEquals(11.2408, copia.getValue().latitud());

        ArgumentCaptor<Domicilio> guardado = ArgumentCaptor.forClass(Domicilio.class);
        verify(domicilios).guardar(guardado.capture());
        assertEquals(EstadoDomicilio.DISPONIBLE, guardado.getValue().estado());
        assertEquals(VENDEDOR, guardado.getValue().vendedorId());
        assertEquals(COMPRADOR, guardado.getValue().compradorId());
        assertEquals(copia.getValue().id(), guardado.getValue().ubicacionOrigenId());
        assertEquals(ENTREGA, guardado.getValue().ubicacionDestinoId());

        ArgumentCaptor<EventoTrazabilidad> traza = ArgumentCaptor.forClass(EventoTrazabilidad.class);
        verify(domicilios).registrarEvento(traza.capture());
        assertEquals(EstadoDomicilio.DISPONIBLE, traza.getValue().estado());

        ArgumentCaptor<DomicilioPublicadoEvent> evento = ArgumentCaptor.forClass(DomicilioPublicadoEvent.class);
        verify(eventos).publish(evento.capture());
        assertEquals("domicilios.domicilio-publicado", evento.getValue().type());
        assertEquals(VENDEDOR, evento.getValue().payload().get("vendedorId"));
        assertEquals(4_500L, evento.getValue().payload().get("tarifa"));
    }

    @Test
    void pedidoDeOtroVendedorNoExisteParaEsteVendedor() {
        pedido("DOMICILIO", "ACEPTADO", null);

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.publicar(PEDIDO, UUID.randomUUID()));

        assertEquals("NOT_FOUND", error.code());
        verifyNothingPublished();
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECOGIDA", "ENTREGA_DIRECTA"})
    void rechazaPedidosQueNoSonDeDomicilio(String modalidad) {
        pedido(modalidad, "ACEPTADO", null);

        assertEquals("BUSINESS_RULE",
                assertThrows(DomainException.class, () -> useCase.publicar(PEDIDO, VENDEDOR)).code());
        verifyNothingPublished();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDIENTE", "RECHAZADO", "CANCELADO", "ENTREGADO", "EN_CAMINO"})
    void rechazaPedidosQueNoEstanAceptados(String estado) {
        pedido("DOMICILIO", estado, null);

        DomainException error = assertThrows(DomainException.class, () -> useCase.publicar(PEDIDO, VENDEDOR));

        assertEquals("INVALID_STATE", error.code());
        assertEquals(estado, error.details().get("estadoActual"));
        verifyNothingPublished();
    }

    @Test
    void exigeElPagoConfirmado() {
        pedido("DOMICILIO", "ACEPTADO", null);
        when(pagos.consultar(PEDIDO)).thenReturn(Optional.of("PENDIENTE_CONFIRMACION"));

        assertEquals("PAYMENT_NOT_CONFIRMED",
                assertThrows(DomainException.class, () -> useCase.publicar(PEDIDO, VENDEDOR)).code());
        verifyNothingPublished();
    }

    @Test
    void noPublicaDosVecesElMismoPedido() {
        pedido("DOMICILIO", "ACEPTADO", null);
        when(domicilios.buscarActivoPorPedido(PEDIDO)).thenReturn(Optional.of(
                Domicilio.publicar(PEDIDO, VENDEDOR, COMPRADOR, 4_500, UUID.randomUUID(), ENTREGA, NOW)));

        assertEquals("ALREADY_PUBLISHED",
                assertThrows(DomainException.class, () -> useCase.publicar(PEDIDO, VENDEDOR)).code());
        verifyNothingPublished();
    }

    @Test
    void unaReservaSoloSePublicaDesde15MinutosAntes() {
        Instant reserva = NOW.plusSeconds(16 * 60);
        pedido("DOMICILIO", "ACEPTADO", reserva);

        DomainException error = assertThrows(DomainException.class, () -> useCase.publicar(PEDIDO, VENDEDOR));

        assertEquals("RESERVATION_NOT_DUE", error.code());
        assertEquals(reserva.minusSeconds(15 * 60).toString(), error.details().get("disponibleDesde"));
        verifyNothingPublished();
    }

    @Test
    void unaReservaDentroDeLaVentanaSePublica() {
        pedido("DOMICILIO", "ACEPTADO", NOW.plusSeconds(15 * 60));

        assertEquals("DISPONIBLE", useCase.publicar(PEDIDO, VENDEDOR).estado());
    }

    private void pedido(String modalidad, String estado, Instant reserva) {
        when(pedidos.bloquear(PEDIDO)).thenReturn(Optional.of(new PedidoParaDomicilio(
                PEDIDO, COMPRADOR, EMPRENDIMIENTO, modalidad, estado, 4_500L, ENTREGA, reserva)));
    }

    private void verifyNothingPublished() {
        verify(domicilios, never()).guardar(any());
        verify(eventos, never()).publish(any());
    }
}
