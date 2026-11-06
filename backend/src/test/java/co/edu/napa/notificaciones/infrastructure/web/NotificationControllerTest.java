package co.edu.napa.notificaciones.infrastructure.web;

import co.edu.napa.notificaciones.application.ports.out.NotificationPage;
import co.edu.napa.notificaciones.application.usecase.NotificationUseCase;
import co.edu.napa.notificaciones.domain.model.Notificacion;
import co.edu.napa.notificaciones.domain.model.TipoNotificacion;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {
    private static final UUID USER_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

    @Mock private NotificationUseCase notifications;

    private NotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(notifications);
    }

    @Test
    void listUsesTheAuthenticatedUserAndReturnsPrivatePaginatedContent() {
        Notificacion notification = new Notificacion(
                UUID.randomUUID(),
                USER_ID,
                TipoNotificacion.PEDIDO_CREADO.eventType(),
                "Nuevo pedido",
                "Tienes un nuevo pedido.",
                null,
                Instant.parse("2026-10-21T12:00:00Z")
        );
        when(notifications.listar(USER_ID, null, 0, 20, "createdAt,desc"))
                .thenReturn(new NotificationPage(List.of(notification), 0, 20, 1));

        var response = controller.list(
                jwt(),
                0,
                20,
                "createdAt,desc",
                null,
                new MockHttpServletRequest()
        );

        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals(1, response.getBody().totalElements());
        assertEquals(notification.id(), response.getBody().content().getFirst().id());
        assertFalse(response.getBody().content().getFirst().leida());
    }

    @Test
    void listRejectsUnknownQueryParameters() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("usuarioId", USER_ID.toString());

        assertEquals(
                "VALIDATION_ERROR",
                assertThrows(
                        DomainException.class,
                        () -> controller.list(jwt(), 0, 20, "createdAt,desc", null, request)
                ).code()
        );
    }

    @Test
    void preferencePatchPassesOnlyRequestedTypesAndReturnsNoStore() {
        List<NotificationUseCase.PreferenceStatus> state = List.of(
                new NotificationUseCase.PreferenceStatus(TipoNotificacion.PEDIDO_CREADO.eventType(), false)
        );
        when(notifications.actualizarPreferencias(
                USER_ID,
                List.of(new NotificationUseCase.PreferenceUpdate(
                        TipoNotificacion.PEDIDO_CREADO.eventType(),
                        false
                ))
        )).thenReturn(state);

        var response = controller.updatePreferences(
                jwt(),
                new NotificationController.PreferencesRequest(List.of(
                        new NotificationController.PreferenceRequest(
                                TipoNotificacion.PEDIDO_CREADO.eventType(),
                                false
                        )
                )),
                new MockHttpServletRequest()
        );

        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals(state, response.getBody().preferencias());
        verify(notifications).actualizarPreferencias(
                USER_ID,
                List.of(new NotificationUseCase.PreferenceUpdate(
                        TipoNotificacion.PEDIDO_CREADO.eventType(),
                        false
                ))
        );
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(USER_ID.toString())
                .build();
    }
}
