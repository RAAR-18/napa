package co.edu.napa.notificaciones.infrastructure.event;

import co.edu.napa.notificaciones.application.ports.in.ProcessNotificationEventPort;
import co.edu.napa.notificaciones.application.ports.in.NotificationEvent;
import co.edu.napa.notificaciones.application.ports.out.PushDeliveryResult;
import co.edu.napa.notificaciones.application.ports.out.PushNotificationDispatch;
import co.edu.napa.notificaciones.application.ports.out.PushNotificationPort;
import co.edu.napa.shared.infrastructure.outbox.OutboxEnvelope;
import co.edu.napa.shared.infrastructure.outbox.OutboxEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class NotificationOutboxEventHandler implements OutboxEventHandler {
    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxEventHandler.class);
    private static final Set<String> EVENT_TYPES = Set.of(
            "pedidos.pedido-creado",
            "pedidos.pedido-aceptado",
            "pedidos.pedido-rechazado",
            "pagos.pago-pedido-confirmado",
            "entregas.entrega-directa-iniciada",
            "pedidos.pedido-listo-para-recoger",
            "domicilios.domicilio-publicado",
            "domicilios.domicilio-asignado",
            "domicilios.domicilio-recogido",
            "domicilios.domicilio-en-camino",
            "domicilios.domicilio-entregado",
            "pagos.pago-tarifa-registrado",
            "domicilios.domicilio-cancelado",
            "reportes.reporte-resuelto",
            "calificaciones.comentario-moderado",
            "cuentas.cuenta-eliminada"
    );

    private final ProcessNotificationEventPort processNotification;
    private final PushNotificationPort pushNotifications;

    public NotificationOutboxEventHandler(
            ProcessNotificationEventPort processNotification,
            PushNotificationPort pushNotifications
    ) {
        this.processNotification = processNotification;
        this.pushNotifications = pushNotifications;
    }

    @Override
    public String eventType() {
        return "pedidos.pedido-creado";
    }

    @Override
    public Set<String> eventTypes() {
        return EVENT_TYPES;
    }

    @Override
    public void handle(OutboxEnvelope event) {
        List<PushNotificationDispatch> dispatches = processNotification.process(
                new NotificationEvent(event.eventId(), event.type(), event.payload())
        );
        for (PushNotificationDispatch dispatch : dispatches) {
            PushDeliveryResult result = pushNotifications.send(
                    dispatch.tokens(),
                    dispatch.title(),
                    dispatch.body()
            );
            if (result.failedCount() > 0) {
                log.warn(
                        "Push delivery incomplete for event {}: {}/{} device(s) failed; in-app notification is stored",
                        event.eventId(),
                        result.failedCount(),
                        result.targetCount()
                );
            }
        }
    }
}
