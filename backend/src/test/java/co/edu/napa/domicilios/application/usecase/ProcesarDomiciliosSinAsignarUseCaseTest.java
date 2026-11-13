package co.edu.napa.domicilios.application.usecase;

import co.edu.napa.domicilios.application.PlazosDomicilio;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import co.edu.napa.domicilios.domain.event.Actores;
import co.edu.napa.domicilios.domain.event.DomicilioCanceladoEvent;
import co.edu.napa.domicilios.domain.event.DomicilioSinAsignarEvent;
import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.domicilios.domain.model.EstadoDomicilio;
import co.edu.napa.domicilios.domain.model.EventoTrazabilidad;
import co.edu.napa.domicilios.domain.model.MotivoCancelacion;
import co.edu.napa.pedidos.application.ports.in.PedidoParaDomicilioPort;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcesarDomiciliosSinAsignarUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-13T18:00:00Z");

    private final DomicilioRepositoryPort domicilios = mock(DomicilioRepositoryPort.class);
    private final PedidoParaDomicilioPort pedidos = mock(PedidoParaDomicilioPort.class);
    private final EventPublisherPort eventos = mock(EventPublisherPort.class);
    private final ProcesarDomiciliosSinAsignarUseCase useCase = new ProcesarDomiciliosSinAsignarUseCase(
            domicilios, pedidos, eventos, PlazosDomicilio.POR_DEFECTO, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void avisaAlVendedorYAlClienteALos30Minutos() {
        Domicilio disponible = publicadoHace(Duration.ofMinutes(31));
        when(domicilios.bloquearDisponiblesPublicadosAntesDe(NOW.minus(Duration.ofHours(2)), 50))
                .thenReturn(List.of());
        when(domicilios.bloquearSinAvisoPublicadosAntesDe(NOW.minus(Duration.ofMinutes(30)), 50))
                .thenReturn(List.of(disponible));

        var resultado = useCase.procesar();

        assertEquals(1, resultado.avisados());
        assertEquals(0, resultado.cancelados());
        ArgumentCaptor<Domicilio> actualizado = ArgumentCaptor.forClass(Domicilio.class);
        verify(domicilios).actualizar(actualizado.capture());
        assertEquals(NOW, actualizado.getValue().avisoSinAsignarAt());
        assertEquals(EstadoDomicilio.DISPONIBLE, actualizado.getValue().estado());

        ArgumentCaptor<DomainEvent> evento = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventos).publish(evento.capture());
        DomicilioSinAsignarEvent aviso = assertInstanceOf(DomicilioSinAsignarEvent.class, evento.getValue());
        assertEquals(disponible.compradorId(), aviso.payload().get("compradorId"));
        assertEquals(disponible.vendedorId(), aviso.payload().get("vendedorId"));
        assertEquals(Actores.SISTEMA, aviso.actorUserId());
        verify(pedidos, never()).cancelarPorDomicilio(any());
    }

    @Test
    void cancelaElDomicilioYElPedidoALas2Horas() {
        Domicilio disponible = publicadoHace(Duration.ofHours(2));
        when(domicilios.bloquearDisponiblesPublicadosAntesDe(NOW.minus(Duration.ofHours(2)), 50))
                .thenReturn(List.of(disponible));
        when(domicilios.bloquearSinAvisoPublicadosAntesDe(any(), anyInt())).thenReturn(List.of());

        var resultado = useCase.procesar();

        assertEquals(1, resultado.cancelados());
        ArgumentCaptor<Domicilio> actualizado = ArgumentCaptor.forClass(Domicilio.class);
        verify(domicilios).actualizar(actualizado.capture());
        assertEquals(EstadoDomicilio.CANCELADO, actualizado.getValue().estado());
        assertEquals(MotivoCancelacion.SIN_ASIGNAR, actualizado.getValue().motivoCancelacion());

        ArgumentCaptor<EventoTrazabilidad> traza = ArgumentCaptor.forClass(EventoTrazabilidad.class);
        verify(domicilios).registrarEvento(traza.capture());
        assertEquals(EstadoDomicilio.CANCELADO, traza.getValue().estado());
        assertNull(traza.getValue().usuarioId(), "lo canceló el sistema");

        verify(pedidos).cancelarPorDomicilio(disponible.pedidoId());

        ArgumentCaptor<DomainEvent> evento = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventos).publish(evento.capture());
        DomicilioCanceladoEvent cancelado = assertInstanceOf(DomicilioCanceladoEvent.class, evento.getValue());
        assertEquals("SIN_ASIGNAR", cancelado.payload().get("motivo"));
        assertEquals(disponible.pedidoId(), cancelado.payload().get("pedidoId"));
    }

    @Test
    void sinDomiciliosPendientesNoHaceNada() {
        when(domicilios.bloquearDisponiblesPublicadosAntesDe(any(), anyInt())).thenReturn(List.of());
        when(domicilios.bloquearSinAvisoPublicadosAntesDe(any(), anyInt())).thenReturn(List.of());

        var resultado = useCase.procesar();

        assertEquals(0, resultado.avisados());
        assertEquals(0, resultado.cancelados());
        verify(eventos, never()).publish(any());
    }

    private static Domicilio publicadoHace(Duration duracion) {
        return Domicilio.publicar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 4_500,
                UUID.randomUUID(), UUID.randomUUID(), NOW.minus(duracion));
    }
}
