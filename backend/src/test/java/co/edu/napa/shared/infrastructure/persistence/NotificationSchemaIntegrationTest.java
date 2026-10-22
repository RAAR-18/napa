package co.edu.napa.shared.infrastructure.persistence;

import co.edu.napa.notificaciones.application.ports.out.NotificationPage;
import co.edu.napa.notificaciones.domain.model.Notificacion;
import co.edu.napa.notificaciones.domain.model.TipoNotificacion;
import co.edu.napa.notificaciones.infrastructure.persistence.NotificationPersistenceAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class NotificationSchemaIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres")
    );

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("napa.outbox.enabled", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NotificationPersistenceAdapter notifications;

    @Test
    void flywayCreatesNotificationTablesAndUserFeedIndex() {
        assertNotNull(jdbcTemplate.queryForObject("SELECT PostGIS_Version()", String.class));
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name = 'notificacion'
                        """,
                        Integer.class
                )
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name = 'preferencia_notificacion'
                        """,
                        Integer.class
                )
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM pg_indexes
                        WHERE schemaname = 'public' AND indexname = 'ix_notificacion_usuario_fecha'
                        """,
                        Integer.class
                )
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM pg_constraint
                        WHERE conname = 'uq_notificacion_evento_usuario'
                        """,
                        Integer.class
                )
        );
    }

    @Test
    void persistenceSupportsOwnedReadsPreferencesDeduplicationAndRetention() {
        UUID userId = insertActiveUser("CLIENTE");
        UUID courierId = insertActiveUser("DOMICILIARIO");
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID firstId = UUID.randomUUID();
        UUID firstEventId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID secondEventId = UUID.randomUUID();
        UUID oldId = UUID.randomUUID();

        assertTrue(notifications.saveIfAbsent(firstEventId, new Notificacion(
                firstId, userId, TipoNotificacion.PEDIDO_CREADO.eventType(),
                "Pedido", "Nuevo pedido", null, now
        )));
        assertFalse(notifications.saveIfAbsent(firstEventId, new Notificacion(
                UUID.randomUUID(), userId, TipoNotificacion.PEDIDO_CREADO.eventType(),
                "Pedido repetido", "Duplicado", null, now
        )));
        assertTrue(notifications.saveIfAbsent(secondEventId, new Notificacion(
                secondId, userId, TipoNotificacion.PEDIDO_ACEPTADO.eventType(),
                "Aceptado", "Pedido aceptado", null, now.plusSeconds(1)
        )));
        assertTrue(notifications.saveIfAbsent(UUID.randomUUID(), new Notificacion(
                oldId, userId, TipoNotificacion.PEDIDO_RECHAZADO.eventType(),
                "Antigua", "Notificación antigua", null, now.minus(31, ChronoUnit.DAYS)
        )));

        NotificationPage page = notifications.findByUsuario(userId, false, 0, 20, "createdAt,desc");
        assertEquals(3, page.totalElements());
        assertEquals(secondId, page.content().getFirst().id());
        assertEquals(0, notifications.findByUsuario(courierId, null, 0, 20, "createdAt,desc").totalElements());

        Instant openedAt = now.plusSeconds(20);
        assertEquals(openedAt, notifications.openForUsuario(firstId, userId, openedAt).orElseThrow().leidaAt());
        assertEquals(
                openedAt,
                notifications.openForUsuario(firstId, userId, openedAt.plusSeconds(10)).orElseThrow().leidaAt()
        );
        assertTrue(notifications.openForUsuario(firstId, courierId, openedAt).isEmpty());
        assertEquals(1, notifications.findByUsuario(userId, true, 0, 20, "createdAt,desc").totalElements());

        notifications.save(userId, TipoNotificacion.PEDIDO_CREADO, false);
        assertEquals(
                Map.of(TipoNotificacion.PEDIDO_CREADO, false),
                notifications.findByUsuario(userId)
        );
        assertTrue(notifications.isActiveUser(userId));
        assertTrue(notifications.findActiveCouriers().contains(courierId));
        assertEquals(1, notifications.deleteCreatedBefore(now.minus(30, ChronoUnit.DAYS)));
        assertEquals(2, notifications.deleteByUsuario(userId));
    }

    private UUID insertActiveUser(String role) {
        UUID userId = UUID.randomUUID();
        String suffix = userId.toString().replace("-", "").substring(0, 10);
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO usuario
                    (id, nombre, celular, password_hash, rol, estado,
                     celular_verificado_at, created_at, updated_at)
                VALUES (?, 'Integration Test', ?, 'test-hash', ?, 'ACTIVA', ?, ?, ?)
                """,
                userId,
                "+573" + suffix,
                role,
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now)
        );
        return userId;
    }
}
