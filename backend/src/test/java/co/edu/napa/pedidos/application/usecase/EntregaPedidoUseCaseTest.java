package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.emprendimientos.application.ports.in.ConsultarEmprendimientoParaPedidoPort;
import co.edu.napa.pagos.application.ports.in.ConfirmarPagoPedidoPort;
import co.edu.napa.pedidos.application.ports.out.PedidoRepositoryPort;
import co.edu.napa.pedidos.domain.event.EntregaDirectaIniciadaEvent;
import co.edu.napa.pedidos.domain.event.PedidoEntregadoEvent;
import co.edu.napa.pedidos.domain.event.PedidoListoParaRecogerEvent;
import co.edu.napa.pedidos.domain.model.EstadoPedido;
import co.edu.napa.pedidos.domain.model.ItemPedido;
import co.edu.napa.pedidos.domain.model.MedioPagoPedido;
import co.edu.napa.pedidos.domain.model.ModalidadEntrega;
import co.edu.napa.pedidos.domain.model.Pedido;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainEvent;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EntregaPedidoUseCaseTest {
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID SELLER_ID = UUID.randomUUID();
    private static final UUID BUYER_ID = UUID.randomUUID();
    private static final UUID BUSINESS_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-13T15:00:00Z");

    private final PedidoRepositoryPort orders = mock(PedidoRepositoryPort.class);
    private final ConfirmarPagoPedidoPort payments = mock(ConfirmarPagoPedidoPort.class);
    private final EventPublisherPort events = mock(EventPublisherPort.class);
    private final ConsultarEmprendimientoParaPedidoPort businesses = id -> Optional.of(
            new ConsultarEmprendimientoParaPedidoPort.Referencia(BUSINESS_ID, SELLER_ID, "Prueba", null));
    private final EntregaPedidoUseCase useCase = new EntregaPedidoUseCase(orders, businesses, payments, events,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void startsADirectDeliveryAndNotifiesTheBuyer() {
        when(orders.bloquearPorId(ORDER_ID)).thenReturn(Optional.of(
                order(ModalidadEntrega.ENTREGA_DIRECTA, EstadoPedido.ACEPTADO, null)));

        var result = useCase.iniciarEntregaDirecta(ORDER_ID, SELLER_ID);

        assertEquals("EN_CAMINO", result.estado());
        assertEquals(NOW, result.updatedAt());
        ArgumentCaptor<Pedido> saved = ArgumentCaptor.forClass(Pedido.class);
        verify(orders).actualizar(saved.capture());
        assertEquals(EstadoPedido.EN_CAMINO, saved.getValue().estado());
        DomainEvent event = published();
        assertInstanceOf(EntregaDirectaIniciadaEvent.class, event);
        assertEquals("entregas.entrega-directa-iniciada", event.type());
        assertEquals(BUYER_ID, event.payload().get("compradorId"));
    }

    @Test
    void aReservationCanStartFrom15MinutesBeforeItsTime() {
        when(orders.bloquearPorId(ORDER_ID)).thenReturn(Optional.of(
                order(ModalidadEntrega.ENTREGA_DIRECTA, EstadoPedido.ACEPTADO, NOW.plusSeconds(15 * 60))));

        assertEquals("EN_CAMINO", useCase.iniciarEntregaDirecta(ORDER_ID, SELLER_ID).estado());
    }

    @Test
    void anEarlyReservationIsRejectedWithTheTimeItBecomesAvailable() {
        Instant reservation = NOW.plusSeconds(15 * 60 + 1);
        when(orders.bloquearPorId(ORDER_ID)).thenReturn(Optional.of(
                order(ModalidadEntrega.RECOGIDA, EstadoPedido.ACEPTADO, reservation)));

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.marcarListoParaRecoger(ORDER_ID, SELLER_ID));

        assertEquals("RESERVATION_NOT_DUE", error.code());
        assertEquals(NOW.plusSeconds(1).toString(), error.details().get("disponibleDesde"));
        verify(orders, never()).actualizar(any());
        verifyNoInteractions(events);
    }

    @Test
    void marksAPickupOrderAsReady() {
        when(orders.bloquearPorId(ORDER_ID)).thenReturn(Optional.of(
                order(ModalidadEntrega.RECOGIDA, EstadoPedido.ACEPTADO, null)));

        var result = useCase.marcarListoParaRecoger(ORDER_ID, SELLER_ID);

        assertEquals("LISTO_PARA_RECOGER", result.estado());
        DomainEvent event = published();
        assertInstanceOf(PedidoListoParaRecogerEvent.class, event);
        assertEquals("pedidos.pedido-listo-para-recoger", event.type());
    }

    @Test
    void confirmsTheDirectDeliveryTogetherWithThePayment() {
        Pedido order = order(ModalidadEntrega.ENTREGA_DIRECTA, EstadoPedido.EN_CAMINO, null);
        when(orders.bloquearPorId(ORDER_ID)).thenReturn(Optional.of(order));
        when(payments.confirmar(ORDER_ID, BUYER_ID, SELLER_ID, "ENTREGA_DIRECTA"))
                .thenReturn(new ConfirmarPagoPedidoPort.PagoConfirmado(UUID.randomUUID(), "CONFIRMADO", "EFECTIVO",
                        order.total(), NOW));

        var result = useCase.confirmarEntregaDirecta(ORDER_ID, SELLER_ID, true);

        assertEquals("ENTREGADO", result.estado());
        assertEquals("EFECTIVO", result.pago().medio());
        assertEquals(order.total(), result.pago().monto());
        PedidoEntregadoEvent event = assertInstanceOf(PedidoEntregadoEvent.class, published());
        assertNull(event.domicilioId(), "sin domicilio, notificaciones avisa al comprador (E07)");
        assertEquals(SELLER_ID, event.actorUserId());
    }

    @Test
    void confirmsThePickupOnlyWhenTheOrderIsReady() {
        when(orders.bloquearPorId(ORDER_ID)).thenReturn(Optional.of(
                order(ModalidadEntrega.RECOGIDA, EstadoPedido.ACEPTADO, null)));

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.confirmarRetiro(ORDER_ID, SELLER_ID, true));

        assertEquals("INVALID_STATE", error.code());
        assertEquals("ACEPTADO", error.details().get("estadoActual"));
        verifyNoInteractions(payments, events);
    }

    @Test
    void aSecondConfirmationIsRejected() {
        when(orders.bloquearPorId(ORDER_ID)).thenReturn(Optional.of(
                order(ModalidadEntrega.ENTREGA_DIRECTA, EstadoPedido.ENTREGADO, null)));

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.confirmarEntregaDirecta(ORDER_ID, SELLER_ID, true));

        assertEquals("INVALID_STATE", error.code());
        assertEquals("ENTREGADO", error.details().get("estadoActual"));
        verifyNoInteractions(payments);
    }

    @Test
    void requiresTheSellerToConfirmThePayment() {
        DomainException error = assertThrows(DomainException.class,
                () -> useCase.confirmarRetiro(ORDER_ID, SELLER_ID, false));

        assertEquals("VALIDATION_ERROR", error.code());
        verifyNoInteractions(orders, payments, events);
    }

    @Test
    void rejectsOrdersOfAnotherModality() {
        when(orders.bloquearPorId(ORDER_ID)).thenReturn(Optional.of(
                order(ModalidadEntrega.RECOGIDA, EstadoPedido.ACEPTADO, null)));

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.iniciarEntregaDirecta(ORDER_ID, SELLER_ID));

        assertEquals("BUSINESS_RULE", error.code());
        assertEquals("Este pedido no es de entrega directa.", error.getMessage());
    }

    @Test
    void hidesOrdersOfAnotherSeller() {
        when(orders.bloquearPorId(ORDER_ID)).thenReturn(Optional.of(
                order(ModalidadEntrega.ENTREGA_DIRECTA, EstadoPedido.ACEPTADO, null)));

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.iniciarEntregaDirecta(ORDER_ID, UUID.randomUUID()));

        assertEquals("NOT_FOUND", error.code());
        verify(orders, never()).actualizar(any());
    }

    private DomainEvent published() {
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(events).publish(captor.capture());
        return captor.getValue();
    }

    private static Pedido order(ModalidadEntrega modality, EstadoPedido state, Instant reservation) {
        UUID location = modality == ModalidadEntrega.RECOGIDA ? null : UUID.randomUUID();
        return new Pedido(ORDER_ID, BUYER_ID, BUSINESS_ID, modality, state, reservation, MedioPagoPedido.EFECTIVO,
                null, 2_000L, location,
                List.of(new ItemPedido(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, 2_000)),
                NOW.minusSeconds(3_600), NOW.minusSeconds(3_600));
    }
}
