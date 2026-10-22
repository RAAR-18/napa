package co.edu.napa.notificaciones.application.usecase;

import co.edu.napa.notificaciones.application.ports.in.NotificationEvent;
import co.edu.napa.notificaciones.application.ports.out.NotificationAudiencePort;
import co.edu.napa.notificaciones.application.ports.out.NotificationPage;
import co.edu.napa.notificaciones.application.ports.out.NotificacionRepositoryPort;
import co.edu.napa.notificaciones.application.ports.out.PreferenciaNotificacionRepositoryPort;
import co.edu.napa.notificaciones.application.ports.out.PushNotificationDispatch;
import co.edu.napa.notificaciones.domain.model.Notificacion;
import co.edu.napa.notificaciones.domain.model.TipoNotificacion;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID EVENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Mock private NotificacionRepositoryPort notifications;
    @Mock private PreferenciaNotificacionRepositoryPort preferences;
    @Mock private NotificationAudiencePort audience;

    private NotificationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new NotificationUseCase(
                notifications,
                preferences,
                audience,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void listsOnlyTheAuthenticatedUsersNotificationsWithValidatedPagination() {
        NotificationPage page = new NotificationPage(List.of(), 0, 20, 0);
        when(audience.isActiveUser(USER_ID)).thenReturn(true);
        when(notifications.findByUsuario(USER_ID, false, 0, 20, "createdAt,desc")).thenReturn(page);

        assertEquals(page, useCase.listar(USER_ID, false, 0, 20, "createdAt,desc"));

        verify(notifications).findByUsuario(USER_ID, false, 0, 20, "createdAt,desc");
        assertEquals(
                "VALIDATION_ERROR",
                assertThrows(
                        DomainException.class,
                        () -> useCase.listar(USER_ID, null, 0, 101, "createdAt,desc")
                ).code()
        );
    }

    @Test
    void openingNotificationMarksItReadAndHidesNotificationsOwnedBySomeoneElse() {
        Notificacion notification = new Notificacion(
                UUID.randomUUID(),
                USER_ID,
                TipoNotificacion.PEDIDO_CREADO.eventType(),
                "Nuevo pedido",
                "Tienes un nuevo pedido.",
                NOW,
                NOW
        );
        when(audience.isActiveUser(USER_ID)).thenReturn(true);
        when(notifications.openForUsuario(notification.id(), USER_ID, NOW)).thenReturn(Optional.of(notification));

        assertEquals(notification, useCase.abrir(USER_ID, notification.id()));
        verify(notifications).openForUsuario(notification.id(), USER_ID, NOW);

        when(notifications.openForUsuario(notification.id(), OTHER_USER_ID, NOW)).thenReturn(Optional.empty());
        when(audience.isActiveUser(OTHER_USER_ID)).thenReturn(true);
        assertEquals(
                "NOT_FOUND",
                assertThrows(
                        DomainException.class,
                        () -> useCase.abrir(OTHER_USER_ID, notification.id())
                ).code()
        );
    }

    @Test
    void preferencesAreEnabledByDefaultAndPartialUpdatesPreserveOtherTypes() {
        when(audience.isActiveUser(USER_ID)).thenReturn(true);
        when(preferences.findByUsuario(USER_ID))
                .thenReturn(Map.of())
                .thenReturn(Map.of(TipoNotificacion.PEDIDO_CREADO, false));

        List<NotificationUseCase.PreferenceStatus> initial = useCase.listarPreferencias(USER_ID);
        assertEquals(TipoNotificacion.values().length, initial.size());
        assertTrue(initial.stream().allMatch(NotificationUseCase.PreferenceStatus::habilitada));

        List<NotificationUseCase.PreferenceStatus> updated = useCase.actualizarPreferencias(
                USER_ID,
                List.of(new NotificationUseCase.PreferenceUpdate(
                        TipoNotificacion.PEDIDO_CREADO.eventType(),
                        false
                ))
        );
        assertFalse(updated.stream()
                .filter(status -> status.tipo().equals(TipoNotificacion.PEDIDO_CREADO.eventType()))
                .findFirst()
                .orElseThrow()
                .habilitada());
        assertTrue(updated.stream()
                .filter(status -> status.tipo().equals(TipoNotificacion.PEDIDO_ACEPTADO.eventType()))
                .findFirst()
                .orElseThrow()
                .habilitada());
        verify(preferences).save(USER_ID, TipoNotificacion.PEDIDO_CREADO, false);
    }

    @Test
    void respectsDisabledPreferenceAndDeduplicatesRedeliveredEventsBeforePush() {
        NotificationEvent event = new NotificationEvent(
                EVENT_ID,
                TipoNotificacion.PEDIDO_CREADO.eventType(),
                Map.of("vendedorId", USER_ID)
        );
        when(audience.isActiveUser(USER_ID)).thenReturn(true);
        when(preferences.findByUsuario(USER_ID))
                .thenReturn(Map.of(TipoNotificacion.PEDIDO_CREADO, false))
                .thenReturn(Map.of())
                .thenReturn(Map.of());
        when(notifications.saveIfAbsent(eq(EVENT_ID), any())).thenReturn(false).thenReturn(true);

        assertTrue(useCase.process(event).isEmpty());
        verify(notifications, never()).saveIfAbsent(any(), any());
        verify(audience, never()).findPushTokens(USER_ID);

        assertTrue(useCase.process(event).isEmpty());
        verify(notifications).saveIfAbsent(eq(EVENT_ID), any());
        verify(audience, never()).findPushTokens(USER_ID);

        when(audience.findPushTokens(USER_ID)).thenReturn(List.of("device-token"));
        List<PushNotificationDispatch> dispatches = useCase.process(event);
        assertEquals(1, dispatches.size());
        assertEquals(List.of("device-token"), dispatches.getFirst().tokens());
        verify(notifications, times(2)).saveIfAbsent(eq(EVENT_ID), any());
    }

    @Test
    void allConfiguredNotificationEventsCreateNotificationsForTheirSpecifiedRecipients() {
        UUID buyerId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        UUID vendorId = UUID.fromString("30000000-0000-0000-0000-000000000002");
        UUID courierId = UUID.fromString("30000000-0000-0000-0000-000000000003");
        UUID authorId = UUID.fromString("30000000-0000-0000-0000-000000000004");
        List<NotificationEvent> events = List.of(
                event("pedidos.pedido-creado", Map.of("vendedorId", vendorId)),
                event("pedidos.pedido-aceptado", Map.of("compradorId", buyerId, "requierePagoDigital", true)),
                event("pedidos.pedido-rechazado", Map.of("compradorId", buyerId)),
                event("pagos.pago-pedido-confirmado", Map.of("compradorId", buyerId)),
                event("entregas.entrega-directa-iniciada", Map.of("compradorId", buyerId)),
                event("pedidos.pedido-listo-para-recoger", Map.of("compradorId", buyerId)),
                event("domicilios.domicilio-publicado", Map.of()),
                event("domicilios.domicilio-asignado", Map.of("compradorId", buyerId, "vendedorId", vendorId)),
                event("domicilios.domicilio-recogido", Map.of("vendedorId", vendorId)),
                event("domicilios.domicilio-en-camino", Map.of("compradorId", buyerId)),
                event("domicilios.domicilio-entregado", Map.of(
                        "compradorId", buyerId,
                        "vendedorId", vendorId,
                        "domiciliarioId", courierId
                )),
                event("pagos.pago-tarifa-registrado", Map.of("domiciliarioId", courierId)),
                event("domicilios.domicilio-cancelado", Map.of(
                        "compradorId", buyerId,
                        "vendedorId", vendorId,
                        "domiciliarioId", courierId
                )),
                event("reportes.reporte-resuelto", Map.of("autorId", authorId)),
                event("calificaciones.comentario-moderado", Map.of("autorId", authorId))
        );
        when(audience.isActiveUser(any())).thenReturn(true);
        when(audience.findActiveCouriers()).thenReturn(List.of(courierId));
        when(preferences.findByUsuario(any())).thenReturn(Map.of());
        when(notifications.saveIfAbsent(any(), any())).thenReturn(true);

        events.forEach(useCase::process);

        ArgumentCaptor<Notificacion> saved = ArgumentCaptor.forClass(Notificacion.class);
        verify(notifications, times(19)).saveIfAbsent(any(), saved.capture());
        assertTrue(saved.getAllValues().stream()
                .filter(notification -> notification.tipo().equals(TipoNotificacion.PEDIDO_ACEPTADO.eventType()))
                .allMatch(notification -> notification.cuerpo().contains("Transfiere el total")));
    }

    @Test
    void accountDeletionRemovesNotificationsAndMalformedEventsFailExplicitly() {
        when(notifications.deleteByUsuario(USER_ID)).thenReturn(3);

        assertTrue(useCase.process(new NotificationEvent(
                EVENT_ID,
                "cuentas.cuenta-eliminada",
                Map.of("usuarioId", USER_ID)
        )).isEmpty());
        verify(notifications).deleteByUsuario(USER_ID);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.process(event("pedidos.pedido-creado", Map.of()))
        );
        assertEquals("BUSINESS_RULE", exception.code());
    }

    private NotificationEvent event(String type, Map<String, Object> payload) {
        return new NotificationEvent(UUID.randomUUID(), type, payload);
    }
}
