package co.edu.napa.notificaciones.application.usecase;

import co.edu.napa.notificaciones.application.ports.in.NotificationPort;
import co.edu.napa.notificaciones.application.ports.in.ProcessNotificationEventPort;
import co.edu.napa.notificaciones.application.ports.in.NotificationEvent;
import co.edu.napa.notificaciones.application.ports.out.NotificationAudiencePort;
import co.edu.napa.notificaciones.application.ports.out.NotificationPage;
import co.edu.napa.notificaciones.application.ports.out.NotificacionRepositoryPort;
import co.edu.napa.notificaciones.application.ports.out.PreferenciaNotificacionRepositoryPort;
import co.edu.napa.notificaciones.application.ports.out.PushNotificationDispatch;
import co.edu.napa.notificaciones.domain.model.Notificacion;
import co.edu.napa.notificaciones.domain.model.TipoNotificacion;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class NotificationUseCase implements ProcessNotificationEventPort, NotificationPort {
    private static final String ACCOUNT_DELETED_EVENT = "cuentas.cuenta-eliminada";
    private static final String DEFAULT_ORDER = "createdAt,desc";

    private final NotificacionRepositoryPort notifications;
    private final PreferenciaNotificacionRepositoryPort preferences;
    private final NotificationAudiencePort audience;
    private final Clock clock;

    public NotificationUseCase(
            NotificacionRepositoryPort notifications,
            PreferenciaNotificacionRepositoryPort preferences,
            NotificationAudiencePort audience,
            Clock clock
    ) {
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.audience = Objects.requireNonNull(audience, "audience");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public NotificationPage listar(UUID usuarioId, Boolean leida, int page, int size, String order) {
        requireActiveUser(usuarioId);
        if (page < 0 || size < 1 || size > 100) {
            throw new DomainException("VALIDATION_ERROR", "La paginación solicitada no es válida.");
        }
        if (!validOrder(order)) {
            throw new DomainException("VALIDATION_ERROR", "El orden debe usar createdAt,asc o createdAt,desc.");
        }
        return notifications.findByUsuario(usuarioId, leida, page, size, order);
    }

    @Transactional
    public Notificacion abrir(UUID usuarioId, UUID notificacionId) {
        requireActiveUser(usuarioId);
        return notifications.openForUsuario(notificacionId, usuarioId, Instant.now(clock))
                .orElseThrow(() -> new DomainException(
                        "NOT_FOUND",
                        "No se encontró la notificación solicitada."
                ));
    }

    public long contarNoLeidas(UUID usuarioId) {
        requireActiveUser(usuarioId);
        return notifications.countUnread(usuarioId);
    }

    public List<PreferenceStatus> listarPreferencias(UUID usuarioId) {
        requireActiveUser(usuarioId);
        Map<TipoNotificacion, Boolean> configured = preferences.findByUsuario(usuarioId);
        return List.of(TipoNotificacion.values()).stream()
                .map(tipo -> new PreferenceStatus(tipo.eventType(), configured.getOrDefault(tipo, true)))
                .toList();
    }

    @Transactional
    public List<PreferenceStatus> actualizarPreferencias(
            UUID usuarioId,
            List<PreferenceUpdate> updates
    ) {
        requireActiveUser(usuarioId);
        if (updates == null) {
            throw new DomainException("VALIDATION_ERROR", "La lista de preferencias es obligatoria.");
        }
        Map<TipoNotificacion, Boolean> unique = new LinkedHashMap<>();
        for (PreferenceUpdate update : updates) {
            if (update == null || update.tipo() == null || update.habilitada() == null) {
                throw new DomainException("VALIDATION_ERROR", "Cada preferencia requiere tipo y estado.");
            }
            TipoNotificacion tipo = parseType(update.tipo());
            if (unique.putIfAbsent(tipo, update.habilitada()) != null) {
                throw new DomainException("VALIDATION_ERROR", "No repitas un tipo de notificación.");
            }
        }
        unique.forEach((tipo, enabled) -> preferences.save(usuarioId, tipo, enabled));
        return listarPreferencias(usuarioId);
    }

    @Override
    @Transactional
    public List<PushNotificationDispatch> process(NotificationEvent event) {
        if (event.type().equals(ACCOUNT_DELETED_EVENT)) {
            UUID deletedUserId = payloadUuid(event, "usuarioId");
            notifications.deleteByUsuario(deletedUserId);
            return List.of();
        }

        TipoNotificacion tipo;
        try {
            tipo = TipoNotificacion.fromEventType(event.type());
        } catch (IllegalArgumentException exception) {
            throw new DomainException(
                    "BUSINESS_RULE",
                    "El evento no pertenece al catálogo de notificaciones."
            );
        }

        Map<UUID, NotificationContent> recipients = recipients(event, tipo);
        List<PushNotificationDispatch> dispatches = new ArrayList<>();
        Instant now = Instant.now(clock);
        for (Map.Entry<UUID, NotificationContent> recipient : recipients.entrySet()) {
            UUID userId = recipient.getKey();
            if (!audience.isActiveUser(userId)
                    || !preferences.findByUsuario(userId).getOrDefault(tipo, true)) {
                continue;
            }

            NotificationContent content = recipient.getValue();
            Notificacion notification = new Notificacion(
                    UUID.randomUUID(),
                    userId,
                    tipo.eventType(),
                    content.title(),
                    content.body(),
                    null,
                    now,
                    content.referencia()
            );
            if (notifications.saveIfAbsent(event.eventId(), notification)) {
                List<String> tokens = audience.findPushTokens(userId);
                if (!tokens.isEmpty()) {
                    dispatches.add(new PushNotificationDispatch(tokens, content.title(), content.body()));
                }
            }
        }
        return List.copyOf(dispatches);
    }

    private Map<UUID, NotificationContent> recipients(NotificationEvent event, TipoNotificacion tipo) {
        Map<UUID, NotificationContent> recipients = new LinkedHashMap<>();
        switch (tipo) {
            case PEDIDO_CREADO ->
                    add(recipients, payloadUuid(event, "vendedorId"), "Nuevo pedido", "Tienes un nuevo pedido.", pedido(event));
            case PEDIDO_ACEPTADO -> {
                boolean needsDigitalPayment = Boolean.TRUE.equals(event.payload().get("requierePagoDigital"));
                add(
                        recipients,
                        payloadUuid(event, "compradorId"),
                        "Pedido aceptado",
                        needsDigitalPayment
                                ? "El vendedor aceptó tu pedido. Transfiere el total para continuar."
                                : "El vendedor aceptó tu pedido.",
                        pedido(event)
                );
            }
            case PEDIDO_RECHAZADO ->
                    add(recipients, payloadUuid(event, "compradorId"), "Pedido rechazado", "El vendedor rechazó tu pedido.", pedido(event));
            case PAGO_PEDIDO_CONFIRMADO -> {
                if ("DOMICILIO".equals(event.payload().get("modalidad"))) {
                    add(recipients, payloadUuid(event, "compradorId"),
                            "Pago confirmado", "El vendedor confirmó el pago de tu pedido.", pedido(event));
                }
            }
            case ENTREGA_DIRECTA_INICIADA ->
                    add(recipients, payloadUuid(event, "compradorId"), "Entrega en camino", "El vendedor inició la entrega directa.", pedido(event));
            case PEDIDO_LISTO_PARA_RECOGER ->
                    add(recipients, payloadUuid(event, "compradorId"), "Pedido listo", "Tu pedido está listo para recoger.", pedido(event));
            case PEDIDO_ENTREGADO -> {
                if (optionalPayloadUuid(event, "domicilioId") == null) {
                    add(
                            recipients,
                            payloadUuid(event, "compradorId"),
                            "Pedido entregado",
                            "Tu pedido fue entregado. ¡Califica tu experiencia!",
                            pedido(event)
                    );
                }
            }
            case DOMICILIO_PUBLICADO -> audience.findActiveCouriers().forEach(userId ->
                    add(recipients, userId, "Nuevo domicilio disponible", "Hay un nuevo domicilio disponible para aceptar.", domicilio(event)));
            case DOMICILIO_ASIGNADO -> {
                add(recipients, payloadUuid(event, "compradorId"), "Domicilio asignado", "Un domiciliario aceptó tu domicilio.", pedido(event));
                add(recipients, payloadUuid(event, "vendedorId"), "Domicilio asignado", "Un domiciliario aceptó el domicilio de tu pedido.", pedido(event));
            }
            case DOMICILIO_RECOGIDO ->
                    add(recipients, payloadUuid(event, "vendedorId"), "Pedido recogido", "El domiciliario recogió el pedido.", pedido(event));
            case DOMICILIO_EN_CAMINO ->
                    add(recipients, payloadUuid(event, "compradorId"), "Domicilio en camino", "Tu pedido va en camino.", pedido(event));
            case DOMICILIO_ENTREGADO -> {
                add(
                        recipients,
                        payloadUuid(event, "compradorId"),
                        "Pedido entregado",
                        "Tu pedido fue entregado. ¡Califica tu experiencia!",
                        pedido(event)
                );
                add(recipients, payloadUuid(event, "vendedorId"), "Pedido entregado", "El pedido fue entregado. Registra el pago de la tarifa al domiciliario.", pedido(event));
            }
            case PAGO_TARIFA_REGISTRADO ->
                    add(recipients, payloadUuid(event, "domiciliarioId"), "Tarifa pagada", "El vendedor registró el pago de la tarifa.", domicilio(event));
            case DOMICILIO_CANCELADO -> {
                if ("SIN_ASIGNAR".equals(event.payload().get("motivo"))) {
                    add(recipients, payloadUuid(event, "compradorId"), "Pedido cancelado",
                            "Ningún domiciliario tomó tu domicilio a tiempo y el pedido fue cancelado.", pedido(event));
                    add(recipients, payloadUuid(event, "vendedorId"), "Pedido cancelado",
                            "Ningún domiciliario tomó el domicilio en 2 horas y el pedido fue cancelado.", pedido(event));
                } else {
                    add(recipients, payloadUuid(event, "compradorId"), "Domicilio cancelado", "El administrador canceló el domicilio.", pedido(event));
                    add(recipients, payloadUuid(event, "vendedorId"), "Domicilio cancelado", "El administrador canceló el domicilio de tu pedido.", pedido(event));
                    UUID courierId = optionalPayloadUuid(event, "domiciliarioId");
                    if (courierId != null) {
                        add(recipients, courierId, "Domicilio cancelado", "El administrador canceló el domicilio asignado.", domicilio(event));
                    }
                }
            }
            case DOMICILIO_SIN_ASIGNAR -> {
                add(recipients, payloadUuid(event, "compradorId"), "Buscando domiciliario",
                        "Tu domicilio aún no tiene domiciliario. Seguimos buscando.", pedido(event));
                add(recipients, payloadUuid(event, "vendedorId"), "Domicilio sin domiciliario",
                        "Tu domicilio lleva 30 minutos sin que un domiciliario lo tome.", pedido(event));
            }
            case REPORTE_RESUELTO ->
                    add(recipients, payloadUuid(event, "autorId"), "Reporte resuelto", "El administrador resolvió tu reporte.");
            case COMENTARIO_MODERADO ->
                    add(recipients, payloadUuid(event, "autorId"), "Comentario moderado", "El administrador moderó uno de tus comentarios.");
            case REPORTE_CREADO -> audience.findActiveAdministrators().forEach(userId ->
                    add(recipients, userId, "Nuevo reporte", "Hay un nuevo reporte pendiente para revisar."));
        }
        return recipients;
    }

    private void add(
            Map<UUID, NotificationContent> recipients,
            UUID userId,
            String title,
            String body
    ) {
        add(recipients, userId, title, body, null);
    }

    private void add(
            Map<UUID, NotificationContent> recipients,
            UUID userId,
            String title,
            String body,
            Notificacion.Referencia referencia
    ) {
        recipients.putIfAbsent(userId, new NotificationContent(title, body, referencia));
    }

    /** Cliente y vendedor abren el pedido. */
    private Notificacion.Referencia pedido(NotificationEvent event) {
        UUID pedidoId = optionalPayloadUuid(event, "pedidoId");
        return pedidoId == null ? null : new Notificacion.Referencia(Notificacion.Referencia.PEDIDO, pedidoId);
    }

    /** El domiciliario abre el domicilio. */
    private Notificacion.Referencia domicilio(NotificationEvent event) {
        UUID domicilioId = optionalPayloadUuid(event, "domicilioId");
        return domicilioId == null ? null
                : new Notificacion.Referencia(Notificacion.Referencia.DOMICILIO, domicilioId);
    }

    private UUID payloadUuid(NotificationEvent event, String key) {
        UUID id = optionalPayloadUuid(event, key);
        if (id == null) {
            throw new DomainException(
                    "BUSINESS_RULE",
                    "El evento no contiene el identificador requerido para generar la notificación."
            );
        }
        return id;
    }

    private UUID optionalPayloadUuid(NotificationEvent event, String key) {
        Object value = event.payload().get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof UUID id) {
            return id;
        }
        if (value instanceof String stringValue) {
            try {
                return UUID.fromString(stringValue);
            } catch (IllegalArgumentException exception) {
                throw new DomainException(
                        "BUSINESS_RULE",
                        "El evento contiene un identificador inválido para generar la notificación."
                );
            }
        }
        throw new DomainException(
                "BUSINESS_RULE",
                "El evento contiene un identificador inválido para generar la notificación."
        );
    }

    private TipoNotificacion parseType(String value) {
        try {
            return TipoNotificacion.fromEventType(value);
        } catch (IllegalArgumentException exception) {
            throw new DomainException("VALIDATION_ERROR", "El tipo de notificación no es válido.");
        }
    }

    private boolean validOrder(String order) {
        return DEFAULT_ORDER.equals(order) || "createdAt,asc".equals(order);
    }

    private void requireActiveUser(UUID userId) {
        if (userId == null || !audience.isActiveUser(userId)) {
            throw new DomainException("NOT_FOUND", "No se encontró la cuenta solicitada.");
        }
    }

    private record NotificationContent(String title, String body, Notificacion.Referencia referencia) {
    }
}
