package co.edu.napa.domicilios.application.usecase;

import co.edu.napa.domicilios.application.ports.in.ConsultarCodigoConfirmacionPort.CodigoConfirmacion;
import co.edu.napa.domicilios.application.ports.in.ConsultarTrazabilidadPort.Trazabilidad;
import co.edu.napa.domicilios.application.ports.in.EjecutarDomicilioPort.Transicion;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import co.edu.napa.domicilios.application.ports.out.IntentosCodigoEntregaPort;
import co.edu.napa.domicilios.domain.event.DomicilioEnCaminoEvent;
import co.edu.napa.domicilios.domain.event.DomicilioEntregadoEvent;
import co.edu.napa.domicilios.domain.event.DomicilioRecogidoEvent;
import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.domicilios.domain.model.EstadoDomicilio;
import co.edu.napa.domicilios.domain.model.EventoTrazabilidad;
import co.edu.napa.pedidos.application.ports.in.PedidoParaDomicilioPort;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainEvent;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EjecucionDomicilioUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-13T15:00:00Z");
    private static final UUID DOMICILIARIO = UUID.randomUUID();
    private static final String CODIGO = "048213";

    private DomicilioRepositoryPort domicilios;
    private PedidoParaDomicilioPort pedidos;
    private IntentosCodigoEntregaPort intentos;
    private EventPublisherPort eventos;
    private EjecucionDomicilioUseCase useCase;
    private Domicilio asignado;

    @BeforeEach
    void setUp() {
        domicilios = mock(DomicilioRepositoryPort.class);
        pedidos = mock(PedidoParaDomicilioPort.class);
        intentos = mock(IntentosCodigoEntregaPort.class);
        eventos = mock(EventPublisherPort.class);
        useCase = new EjecucionDomicilioUseCase(domicilios, pedidos, intentos, eventos,
                Clock.fixed(NOW, ZoneOffset.UTC));
        asignado = Domicilio.publicar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 4_500,
                        UUID.randomUUID(), UUID.randomUUID(), NOW.minusSeconds(1_200))
                .asignar(DOMICILIARIO, CODIGO, NOW.minusSeconds(600));
    }

    @Test
    void elDomiciliarioConfirmaLaRecogida() {
        when(domicilios.bloquear(asignado.id())).thenReturn(Optional.of(asignado));

        Transicion transicion = useCase.confirmarRecogida(DOMICILIARIO, asignado.id());

        assertEquals("RECOGIDO", transicion.estado());
        assertEquals(NOW, transicion.updatedAt());
        ArgumentCaptor<EventoTrazabilidad> traza = ArgumentCaptor.forClass(EventoTrazabilidad.class);
        verify(domicilios).registrarEvento(traza.capture());
        assertEquals(EstadoDomicilio.RECOGIDO, traza.getValue().estado());
        assertEquals(DOMICILIARIO, traza.getValue().usuarioId());
        assertInstanceOf(DomicilioRecogidoEvent.class, publicado());
    }

    @Test
    void otroDomiciliarioNoPuedeConfirmarLaRecogida() {
        when(domicilios.bloquear(asignado.id())).thenReturn(Optional.of(asignado));

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.confirmarRecogida(UUID.randomUUID(), asignado.id()));

        assertEquals("NOT_FOUND", error.code());
        verify(domicilios, never()).actualizar(any());
    }

    @Test
    void laSalidaExigeQueAntesSeConfirmeLaRecogida() {
        when(domicilios.bloquear(asignado.id())).thenReturn(Optional.of(asignado));

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.confirmarSalida(asignado.vendedorId(), asignado.id()));

        assertEquals("INVALID_STATE", error.code());
        assertEquals("Primero el domiciliario debe confirmar la recogida.", error.getMessage());
        verify(pedidos, never()).ponerEnCaminoPorDomicilio(any());
    }

    @Test
    void elVendedorConfirmaLaSalidaYElPedidoVaEnCamino() {
        Domicilio recogido = asignado.confirmarRecogida(NOW.minusSeconds(60));
        when(domicilios.bloquear(recogido.id())).thenReturn(Optional.of(recogido));

        Transicion transicion = useCase.confirmarSalida(recogido.vendedorId(), recogido.id());

        assertEquals("EN_CAMINO", transicion.estado());
        verify(pedidos).ponerEnCaminoPorDomicilio(recogido.pedidoId());
        assertInstanceOf(DomicilioEnCaminoEvent.class, publicado());
    }

    @Test
    void otroVendedorNoPuedeConfirmarLaSalida() {
        Domicilio recogido = asignado.confirmarRecogida(NOW.minusSeconds(60));
        when(domicilios.bloquear(recogido.id())).thenReturn(Optional.of(recogido));

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.confirmarSalida(UUID.randomUUID(), recogido.id()));

        assertEquals("NOT_FOUND", error.code());
    }

    @Test
    void conElCodigoCorrectoSeEntregaElDomicilioYElPedido() {
        Domicilio enCamino = enCamino();
        when(domicilios.bloquear(enCamino.id())).thenReturn(Optional.of(enCamino));

        Transicion transicion = useCase.confirmarEntrega(DOMICILIARIO, enCamino.id(), CODIGO);

        assertEquals("ENTREGADO", transicion.estado());
        verify(pedidos).entregarPorDomicilio(enCamino.pedidoId(), enCamino.id(), DOMICILIARIO);
        DomicilioEntregadoEvent evento = assertInstanceOf(DomicilioEntregadoEvent.class, publicado());
        assertEquals(4_500L, evento.payload().get("tarifa"));
        verify(intentos).limpiar(enCamino.id());
        verify(intentos, never()).registrarFallo(any());
    }

    @Test
    void unCodigoIncorrectoCuentaUnIntentoYDiceCuantosQuedan() {
        Domicilio enCamino = enCamino();
        when(domicilios.bloquear(enCamino.id())).thenReturn(Optional.of(enCamino));
        when(intentos.registrarFallo(enCamino.id())).thenReturn(3);

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.confirmarEntrega(DOMICILIARIO, enCamino.id(), "111111"));

        assertEquals("CODE_INVALID", error.code());
        assertEquals(3, error.details().get("intentosRestantes"));
        verify(domicilios, never()).actualizar(any());
        verify(pedidos, never()).entregarPorDomicilio(any(), any(), any());
    }

    @Test
    void elQuintoFalloBloqueaQuinceMinutos() {
        Domicilio enCamino = enCamino();
        when(domicilios.bloquear(enCamino.id())).thenReturn(Optional.of(enCamino));
        when(intentos.registrarFallo(enCamino.id())).thenReturn(0);

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.confirmarEntrega(DOMICILIARIO, enCamino.id(), "111111"));

        assertEquals("CODE_ATTEMPTS_LOCKED", error.code());
        assertEquals(900L, error.details().get("retryAfter"));
    }

    @Test
    void duranteElBloqueoNiElCodigoCorrectoEntra() {
        Domicilio enCamino = enCamino();
        when(domicilios.bloquear(enCamino.id())).thenReturn(Optional.of(enCamino));
        when(intentos.segundosBloqueado(enCamino.id())).thenReturn(420L);

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.confirmarEntrega(DOMICILIARIO, enCamino.id(), CODIGO));

        assertEquals("CODE_ATTEMPTS_LOCKED", error.code());
        assertEquals(420L, error.details().get("retryAfter"));
        verify(pedidos, never()).entregarPorDomicilio(any(), any(), any());
    }

    @Test
    void unCodigoMalFormadoNoCuentaComoIntento() {
        DomainException error = assertThrows(DomainException.class,
                () -> useCase.confirmarEntrega(DOMICILIARIO, UUID.randomUUID(), "12ab"));

        assertEquals("VALIDATION_ERROR", error.code());
        verify(intentos, never()).registrarFallo(any());
    }

    @Test
    void laEntregaNoSeConfirmaDosVeces() {
        Domicilio entregado = enCamino().entregar(NOW.minusSeconds(10));
        when(domicilios.bloquear(entregado.id())).thenReturn(Optional.of(entregado));

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.confirmarEntrega(DOMICILIARIO, entregado.id(), CODIGO));

        assertEquals("INVALID_STATE", error.code());
        verify(intentos, never()).registrarFallo(any());
    }

    @Test
    void elClienteVeSuCodigoMientrasElDomicilioEstaEnCurso() {
        when(domicilios.buscar(asignado.id())).thenReturn(Optional.of(asignado));

        CodigoConfirmacion codigo = useCase.consultarCodigo(asignado.compradorId(), asignado.id());

        assertEquals(CODIGO, codigo.codigo());
        assertEquals("ASIGNADO", codigo.estado());
    }

    @Test
    void elCodigoNoExisteAntesDeQueAlguienAcepte() {
        Domicilio disponible = Domicilio.publicar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 4_500,
                UUID.randomUUID(), UUID.randomUUID(), NOW);
        when(domicilios.buscar(disponible.id())).thenReturn(Optional.of(disponible));

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.consultarCodigo(disponible.compradorId(), disponible.id()));

        assertEquals("INVALID_STATE", error.code());
        assertEquals("Tu código estará disponible cuando un domiciliario acepte el domicilio.", error.getMessage());
    }

    @Test
    void elVendedorNoVeElCodigoDelCliente() {
        when(domicilios.buscar(asignado.id())).thenReturn(Optional.of(asignado));

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.consultarCodigo(asignado.vendedorId(), asignado.id()));

        assertEquals("NOT_FOUND", error.code());
    }

    @Test
    void laTrazabilidadMuestraLaCronologiaSinAutores() {
        when(domicilios.buscar(asignado.id())).thenReturn(Optional.of(asignado));
        when(domicilios.listarEventos(asignado.id())).thenReturn(List.of(
                new EventoTrazabilidad(UUID.randomUUID(), asignado.id(), EstadoDomicilio.DISPONIBLE,
                        asignado.vendedorId(), NOW.minusSeconds(1_200)),
                new EventoTrazabilidad(UUID.randomUUID(), asignado.id(), EstadoDomicilio.ASIGNADO,
                        DOMICILIARIO, NOW.minusSeconds(600))));

        Trazabilidad cliente = useCase.consultarTrazabilidad(asignado.compradorId(), asignado.id());
        Trazabilidad vendedor = useCase.consultarTrazabilidad(asignado.vendedorId(), asignado.id());

        assertEquals(List.of("DISPONIBLE", "ASIGNADO"),
                cliente.eventos().stream().map(evento -> evento.estado()).toList());
        assertEquals("ASIGNADO", vendedor.estado());
        assertNull(cliente.ubicacionDomiciliario());
    }

    @Test
    void elDomiciliarioNoConsultaLaTrazabilidadDelCliente() {
        when(domicilios.buscar(asignado.id())).thenReturn(Optional.of(asignado));

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.consultarTrazabilidad(DOMICILIARIO, asignado.id()));

        assertEquals("NOT_FOUND", error.code());
    }

    private Domicilio enCamino() {
        return asignado.confirmarRecogida(NOW.minusSeconds(300)).confirmarSalida(NOW.minusSeconds(240));
    }

    private DomainEvent publicado() {
        ArgumentCaptor<DomainEvent> evento = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventos).publish(evento.capture());
        return evento.getValue();
    }
}
