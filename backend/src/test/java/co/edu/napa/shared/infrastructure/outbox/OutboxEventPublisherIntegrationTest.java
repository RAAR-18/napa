package co.edu.napa.shared.infrastructure.outbox;

import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class OutboxEventPublisherIntegrationTest {
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
    private EventPublisherPort eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void flywayInstallsPostgisAndOutboxTables() {
        assertNotNull(jdbcTemplate.queryForObject("SELECT PostGIS_Version()", String.class));
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name = 'outbox_event'
                        """,
                        Integer.class
                )
        );
    }

    @Test
    void publishedEventSharesTheBusinessTransaction() {
        TestEvent event = new TestEvent(
                UUID.randomUUID(),
                Instant.parse("2026-10-20T12:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Map.of("pedidoId", "pedido-test")
        );
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> eventPublisher.publish(event));

        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM outbox_event WHERE event_id = ?",
                        Integer.class,
                        event.eventId()
                )
        );
    }

    @Test
    void publishedEventRollsBackWithTheBusinessTransaction() {
        TestEvent event = new TestEvent(
                UUID.randomUUID(),
                Instant.parse("2026-10-20T12:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Map.of("pedidoId", "pedido-rollback")
        );
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            eventPublisher.publish(event);
            throw new IllegalStateException("rollback");
        }));

        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM outbox_event WHERE event_id = ?",
                        Integer.class,
                        event.eventId()
                )
        );
    }

    private record TestEvent(
            UUID eventId,
            Instant occurredAt,
            UUID aggregateId,
            UUID actorUserId,
            Map<String, Object> payload
    ) implements DomainEvent {
        @Override
        public String type() {
            return "test.event";
        }

        @Override
        public String aggregateType() {
            return "TestAggregate";
        }
    }
}
