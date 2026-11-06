package co.edu.napa.notificaciones.infrastructure.persistence;

import co.edu.napa.notificaciones.application.ports.out.NotificationAudiencePort;
import co.edu.napa.notificaciones.application.ports.out.NotificationPage;
import co.edu.napa.notificaciones.application.ports.out.NotificacionRepositoryPort;
import co.edu.napa.notificaciones.application.ports.out.PreferenciaNotificacionRepositoryPort;
import co.edu.napa.notificaciones.domain.model.Notificacion;
import co.edu.napa.notificaciones.domain.model.TipoNotificacion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class NotificationPersistenceAdapter implements
        NotificacionRepositoryPort,
        PreferenciaNotificacionRepositoryPort,
        NotificationAudiencePort {
    private static final RowMapper<Notificacion> NOTIFICATION_ROW_MAPPER = (result, rowNumber) ->
            new Notificacion(
                    result.getObject("id", UUID.class),
                    result.getObject("usuario_id", UUID.class),
                    result.getString("tipo"),
                    result.getString("titulo"),
                    result.getString("cuerpo"),
                    instantOrNull(result.getTimestamp("leida_at")),
                    result.getTimestamp("created_at").toInstant()
            );

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public NotificationPersistenceAdapter(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean saveIfAbsent(UUID eventId, Notificacion notification) {
        return jdbcTemplate.update(
                """
                INSERT INTO notificacion
                    (id, usuario_id, tipo, titulo, cuerpo, leida_at, created_at, event_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id, usuario_id) DO NOTHING
                """,
                notification.id(),
                notification.usuarioId(),
                notification.tipo(),
                notification.titulo(),
                notification.cuerpo(),
                timestampOrNull(notification.leidaAt()),
                Timestamp.from(notification.createdAt()),
                eventId
        ) == 1;
    }

    @Override
    public NotificationPage findByUsuario(
            UUID usuarioId,
            Boolean leida,
            int page,
            int size,
            String order
    ) {
        String readFilter = leida == null ? "" : " AND (leida_at IS NOT NULL) = ?";
        String direction = order.endsWith(",asc") ? "ASC" : "DESC";
        String baseQuery = " FROM notificacion WHERE usuario_id = ?" + readFilter;
        List<Object> parameters = new ArrayList<>();
        parameters.add(usuarioId);
        if (leida != null) {
            parameters.add(leida);
        }
        Long total = Objects.requireNonNull(
                jdbcTemplate.queryForObject(
                        "SELECT count(*)" + baseQuery,
                        Long.class,
                        parameters.toArray()
                ),
                "Notification count query must return a row"
        );

        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);
        List<Notificacion> content = jdbcTemplate.query(
                "SELECT id, usuario_id, tipo, titulo, cuerpo, leida_at, created_at"
                        + baseQuery + " ORDER BY created_at " + direction
                        + ", id " + direction + " LIMIT ? OFFSET ?",
                NOTIFICATION_ROW_MAPPER,
                pageParameters.toArray()
        );
        return new NotificationPage(content, page, size, total);
    }

    @Override
    public Optional<Notificacion> openForUsuario(UUID notificationId, UUID usuarioId, Instant openedAt) {
        jdbcTemplate.update(
                """
                UPDATE notificacion
                SET leida_at = COALESCE(leida_at, ?)
                WHERE id = ? AND usuario_id = ?
                """,
                Timestamp.from(openedAt),
                notificationId,
                usuarioId
        );
        return jdbcTemplate.query(
                """
                SELECT id, usuario_id, tipo, titulo, cuerpo, leida_at, created_at
                FROM notificacion
                WHERE id = ? AND usuario_id = ?
                """,
                NOTIFICATION_ROW_MAPPER,
                notificationId,
                usuarioId
        ).stream().findFirst();
    }

    @Override
    public int deleteByUsuario(UUID usuarioId) {
        return jdbcTemplate.update("DELETE FROM notificacion WHERE usuario_id = ?", usuarioId);
    }

    @Override
    public int deleteCreatedBefore(Instant cutoff) {
        return jdbcTemplate.update(
                "DELETE FROM notificacion WHERE created_at < ?",
                Timestamp.from(cutoff)
        );
    }

    @Override
    public Map<TipoNotificacion, Boolean> findByUsuario(UUID usuarioId) {
        Map<TipoNotificacion, Boolean> result = new EnumMap<>(TipoNotificacion.class);
        jdbcTemplate.query(
                "SELECT tipo, habilitada FROM preferencia_notificacion WHERE usuario_id = ?",
                (RowCallbackHandler) row -> result.put(
                        TipoNotificacion.fromEventType(row.getString("tipo")),
                        row.getBoolean("habilitada")
                ),
                usuarioId
        );
        return Map.copyOf(result);
    }

    @Override
    public void save(UUID usuarioId, TipoNotificacion tipo, boolean habilitada) {
        Timestamp now = Timestamp.from(Instant.now(clock));
        jdbcTemplate.update(
                """
                INSERT INTO preferencia_notificacion
                    (usuario_id, tipo, habilitada, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (usuario_id, tipo)
                DO UPDATE SET habilitada = EXCLUDED.habilitada, updated_at = EXCLUDED.updated_at
                """,
                usuarioId,
                tipo.eventType(),
                habilitada,
                now,
                now
        );
    }

    @Override
    public boolean isActiveUser(UUID usuarioId) {
        return Objects.requireNonNull(
                jdbcTemplate.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM usuario WHERE id = ? AND estado = 'ACTIVA')",
                        Boolean.class,
                        usuarioId
                ),
                "Active user query must return a row"
        );
    }

    @Override
    public List<UUID> findActiveCouriers() {
        return jdbcTemplate.query(
                """
                SELECT id FROM usuario
                WHERE rol = 'DOMICILIARIO' AND estado = 'ACTIVA'
                ORDER BY id
                """,
                (result, rowNumber) -> result.getObject("id", UUID.class)
        );
    }

    @Override
    public List<String> findPushTokens(UUID usuarioId) {
        return jdbcTemplate.query(
                """
                SELECT token FROM dispositivo_token
                WHERE usuario_id = ?
                ORDER BY ultima_conexion_at DESC
                """,
                (result, rowNumber) -> result.getString("token"),
                usuarioId
        );
    }

    private static Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp timestampOrNull(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
