package co.edu.napa.notificaciones.infrastructure.event;

import co.edu.napa.notificaciones.application.ports.in.NotificationEvent;
import co.edu.napa.notificaciones.application.ports.in.ProcessNotificationEventPort;
import co.edu.napa.notificaciones.application.ports.out.PushDeliveryResult;
import co.edu.napa.notificaciones.application.ports.out.PushNotificationDispatch;
import co.edu.napa.notificaciones.application.ports.out.PushNotificationPort;
import co.edu.napa.shared.infrastructure.outbox.OutboxEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxEventHandlerTest {
    @Mock private ProcessNotificationEventPort processNotification;
    @Mock private PushNotificationPort pushNotifications;

    @Test
    void routesCatalogEventsAndDispatchesPushOnlyAfterApplicationProcessing() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("vendedorId", UUID.randomUUID());
        OutboxEnvelope event = new OutboxEnvelope(
                eventId,
                "pedidos.pedido-creado",
                1,
                Instant.parse("2026-10-21T12:00:00Z"),
                "Pedido",
                aggregateId,
                actorId,
                payload,
                0
        );
        PushNotificationDispatch dispatch = new PushNotificationDispatch(
                List.of("device-token"),
                "Nuevo pedido",
                "Tienes un nuevo pedido."
        );
        when(processNotification.process(new NotificationEvent(eventId, event.type(), payload)))
                .thenReturn(List.of(dispatch));
        when(pushNotifications.send(dispatch.tokens(), dispatch.title(), dispatch.body()))
                .thenReturn(new PushDeliveryResult(1, 1, 0, true));

        NotificationOutboxEventHandler handler = new NotificationOutboxEventHandler(
                processNotification,
                pushNotifications
        );
        handler.handle(event);

        ArgumentCaptor<NotificationEvent> applicationEvent = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(processNotification).process(applicationEvent.capture());
        assertEquals(new NotificationEvent(eventId, event.type(), payload), applicationEvent.getValue());
        verify(pushNotifications).send(dispatch.tokens(), dispatch.title(), dispatch.body());
        assertEquals("pedidos.pedido-creado", handler.eventType());
        assertEquals(16, handler.eventTypes().size());
        assertTrue(handler.eventTypes().contains("cuentas.cuenta-eliminada"));
    }
}
