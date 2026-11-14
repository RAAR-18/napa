package co.edu.napa.domicilios.application.usecase;

import co.edu.napa.domicilios.application.ports.in.CancelarDomicilioAdminPort.Cancelacion;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import co.edu.napa.domicilios.application.ports.out.IntentosCodigoEntregaPort;
import co.edu.napa.domicilios.domain.event.DomicilioCanceladoEvent;
import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.domicilios.domain.model.EstadoDomicilio;
import co.edu.napa.domicilios.domain.model.EventoTrazabilidad;
import co.edu.napa.domicilios.domain.model.MotivoCancelacion;
import co.edu.napa.pedidos.application.ports.in.PedidoParaDomicilioPort;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CancelarDomicilioAdminUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-13T18:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID DOMICILIARIO = UUID.randomUUID();
    private static final String MOTIVO = "El cliente reportó que el domiciliario no llegó";

    private DomicilioRepositoryPort domicilios;
    private PedidoParaDomicilioPort pedidos;
    private IntentosCodigoEntregaPort intentos;
    private EventPublisherPort eventos;
    private CancelarDomicilioAdminUseCase useCase;

    @BeforeEach
    void setUp() {
        domicilios = mock(DomicilioRepositoryPort.class);
        pedidos = mock(PedidoParaDomicilioPort.class);
        intentos = mock(IntentosCodigoEntregaPort.class);
        eventos = mock(EventPublisherPort.class);
        useCase = new CancelarDomicilioAdminUseCase(domicilios, pedidos, intentos, eventos,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Domicilio disponible() {
        return Domicilio.publicar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 5_000,
                UUID.randomUUID(), UUID.randomUUID(), NOW.minusSeconds(3_600));
    }

    private static Domicilio enCamino() {
        return disponible().asignar(DOMICILIARIO, "048213", NOW.minusSeconds(3_000))
                .confirmarRecogida(NOW.minusSeconds(2_400))
                .confirmarSalida(NOW.minusSeconds(2_000));
    }

    @Test
    void cancelaElDomicilioEnCaminoYAvisaATodasLasPartes() {
        Domicilio domicilio = enCamino();
        when(domicilios.bloquear(domicilio.id())).thenReturn(Optional.of(domicilio));

        Cancelacion cancelacion = useCase.cancelar(ADMIN, domicilio.id(), "  " + MOTIVO + "  ");

        assertEquals("CANCELADO", cancelacion.estado());
        assertEquals(MOTIVO, cancelacion.motivoCancelacion());
        assertEquals("CANCELADO", cancelacion.pedido().estado());
        ArgumentCaptor<Domicilio> actualizado = ArgumentCaptor.forClass(Domicilio.class);
        verify(domicilios).actualizar(actualizado.capture());
        assertEquals(EstadoDomicilio.CANCELADO, actualizado.getValue().estado());
        assertEquals(MotivoCancelacion.ADMINISTRADOR, actualizado.getValue().motivoCancelacion());
        assertNull(actualizado.getValue().codigoConfirmacion());
        assertEquals(DOMICILIARIO, actualizado.getValue().domiciliarioId());

        ArgumentCaptor<EventoTrazabilidad> evento = ArgumentCaptor.forClass(EventoTrazabilidad.class);
        verify(domicilios).registrarEvento(evento.capture());
        assertEquals(ADMIN, evento.getValue().usuarioId());
        assertEquals(EstadoDomicilio.CANCELADO, evento.getValue().estado());
        verify(pedidos).cancelarPorDomicilio(domicilio.pedidoId());

        ArgumentCaptor<DomicilioCanceladoEvent> publicado = ArgumentCaptor.forClass(DomicilioCanceladoEvent.class);
        verify(eventos).publish(publicado.capture());
        assertEquals(DOMICILIARIO, publicado.getValue().payload().get("domiciliarioId"));
        assertEquals("ADMINISTRADOR", publicado.getValue().payload().get("motivo"));
        assertEquals(ADMIN, publicado.getValue().actorUserId());
        verify(intentos).limpiar(domicilio.id());
    }

    @Test
    void unDomicilioSinDomiciliarioNoAvisaADomiciliario() {
        Domicilio domicilio = disponible();
        when(domicilios.bloquear(domicilio.id())).thenReturn(Optional.of(domicilio));

        useCase.cancelar(ADMIN, domicilio.id(), MOTIVO);

        ArgumentCaptor<DomicilioCanceladoEvent> publicado = ArgumentCaptor.forClass(DomicilioCanceladoEvent.class);
        verify(eventos).publish(publicado.capture());
        assertEquals(false, publicado.getValue().payload().containsKey("domiciliarioId"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "corto", "123456789"})
    void exigeUnMotivoDeDiezCaracteresAntesDeTocarLaBase(String motivo) {
        DomainException error = assertThrows(DomainException.class,
                () -> useCase.cancelar(ADMIN, UUID.randomUUID(), motivo));

        assertEquals("VALIDATION_ERROR", error.code());
        verifyNoInteractions(domicilios, pedidos, eventos);
    }

    @Test
    void rechazaUnMotivoDeMasDeTrescientosCaracteres() {
        DomainException error = assertThrows(DomainException.class,
                () -> useCase.cancelar(ADMIN, UUID.randomUUID(), "x".repeat(301)));

        assertEquals("VALIDATION_ERROR", error.code());
    }

    @Test
    void noCancelaUnDomicilioEntregado() {
        Domicilio entregado = enCamino().entregar(NOW.minusSeconds(60));
        when(domicilios.bloquear(entregado.id())).thenReturn(Optional.of(entregado));

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.cancelar(ADMIN, entregado.id(), MOTIVO));

        assertEquals("INVALID_STATE", error.code());
        assertEquals(Map.of("estadoActual", "ENTREGADO"), error.details());
        verify(domicilios, never()).actualizar(any());
        verify(pedidos, never()).cancelarPorDomicilio(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void noCancelaDosVeces() {
        Domicilio cancelado = disponible().cancelarPorAdministrador(MOTIVO, NOW.minusSeconds(60));
        when(domicilios.bloquear(cancelado.id())).thenReturn(Optional.of(cancelado));

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.cancelar(ADMIN, cancelado.id(), MOTIVO));

        assertEquals("INVALID_STATE", error.code());
        assertEquals(Map.of("estadoActual", "CANCELADO"), error.details());
    }

    @Test
    void unDomicilioInexistenteDaNotFound() {
        UUID id = UUID.randomUUID();
        when(domicilios.bloquear(id)).thenReturn(Optional.empty());

        DomainException error = assertThrows(DomainException.class, () -> useCase.cancelar(ADMIN, id, MOTIVO));

        assertEquals("NOT_FOUND", error.code());
    }

    @Test
    void unFalloAlLimpiarLosIntentosNoImpideLaCancelacion() {
        Domicilio domicilio = enCamino();
        when(domicilios.bloquear(domicilio.id())).thenReturn(Optional.of(domicilio));
        doThrow(new IllegalStateException("redis caído")).when(intentos).limpiar(domicilio.id());

        Cancelacion cancelacion = useCase.cancelar(ADMIN, domicilio.id(), MOTIVO);

        assertEquals("CANCELADO", cancelacion.estado());
    }
}
