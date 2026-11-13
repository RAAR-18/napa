package co.edu.napa.pagos.application.usecase;

import co.edu.napa.pagos.application.ports.out.PagoRepositoryPort;
import co.edu.napa.pagos.domain.event.PagoPedidoConfirmadoEvent;
import co.edu.napa.pagos.domain.model.MedioPago;
import co.edu.napa.pagos.domain.model.Pago;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfirmarPagoPedidoUseCaseTest {
    @Test
    void confirmsTheLockedPaymentAndPublishesThePaymentEvent() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-12T12:00:00Z");
        PagoRepositoryPort payments = mock(PagoRepositoryPort.class);
        EventPublisherPort events = mock(EventPublisherPort.class);
        Pago pending = Pago.pedidoPendiente(orderId, buyerId, sellerId, 12_500,
                MedioPago.CUENTA_BANCARIA, now.minusSeconds(30));
        when(payments.bloquearPorPedido(orderId)).thenReturn(Optional.of(pending));
        ConfirmarPagoPedidoUseCase useCase = new ConfirmarPagoPedidoUseCase(
                payments, events, Clock.fixed(now, ZoneOffset.UTC));

        var result = useCase.confirmar(orderId, buyerId, sellerId, "DOMICILIO");

        assertEquals("CONFIRMADO", result.estado());
        assertEquals(12_500, result.monto());
        assertEquals(now, result.confirmadoAt());
        ArgumentCaptor<Pago> paymentCaptor = ArgumentCaptor.forClass(Pago.class);
        verify(payments).actualizar(paymentCaptor.capture());
        assertEquals("CONFIRMADO", paymentCaptor.getValue().estado().name());
        ArgumentCaptor<PagoPedidoConfirmadoEvent> eventCaptor =
                ArgumentCaptor.forClass(PagoPedidoConfirmadoEvent.class);
        verify(events).publish(eventCaptor.capture());
        assertEquals("pagos.pago-pedido-confirmado", eventCaptor.getValue().type());
        assertEquals(result.id(), eventCaptor.getValue().aggregateId());
        assertEquals(orderId, eventCaptor.getValue().payload().get("pedidoId"));
        assertEquals("DOMICILIO", eventCaptor.getValue().payload().get("modalidad"));
    }
}
