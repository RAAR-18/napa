package co.edu.napa.emprendimientos.infrastructure.persistence;

import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoRepositoryPort;
import co.edu.napa.emprendimientos.domain.model.CategoriaEmprendimiento;
import co.edu.napa.emprendimientos.domain.model.Emprendimiento;
import co.edu.napa.emprendimientos.domain.model.EstadoEmprendimiento;
import co.edu.napa.shared.application.ports.out.UbicacionRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.Ubicacion;
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
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class EmprendimientoPersistenceIntegrationTest {
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
    private EmprendimientoRepositoryPort emprendimientos;

    @Autowired
    private UbicacionRepositoryPort ubicaciones;

    @Test
    void migrationAllowsAmbulantLocationsWithoutAddressAndPersistsAnActiveBusiness() {
        UUID userId = insertActiveSeller();
        Instant now = Instant.now();
        Ubicacion location = ubicaciones.save(new Ubicacion(
                UUID.randomUUID(),
                11.2408,
                -74.211,
                null,
                "Frente al parque",
                now,
                now
        ));

        Emprendimiento saved = emprendimientos.save(business(userId, location.id()));

        assertEquals(EstadoEmprendimiento.ACTIVO, saved.estado());
        assertTrue(emprendimientos.existsActiveByUsuarioId(userId));
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'ubicacion'
                          AND column_name = 'direccion'
                          AND is_nullable = 'YES'
                        """,
                        Integer.class
                )
        );
        assertEquals(
                "ACTIVO",
                jdbcTemplate.queryForObject(
                        "SELECT estado FROM emprendimiento WHERE id = ?",
                        String.class,
                        saved.id()
                )
        );
    }

    @Test
    void uniqueActiveBusinessIndexMapsSecondActiveBusinessToDomainConflict() {
        UUID userId = insertActiveSeller();
        Instant now = Instant.now();
        Ubicacion firstLocation = saveLocation(now);
        Ubicacion secondLocation = saveLocation(now);
        emprendimientos.save(business(userId, firstLocation.id()));

        DomainException exception = assertThrows(
                DomainException.class,
                () -> emprendimientos.save(business(userId, secondLocation.id()))
        );

        assertEquals("CONFLICT", exception.code());
    }

    @Test
    void uniqueActiveBusinessIndexAllowsOnlyOneConcurrentCreation() throws Exception {
        UUID userId = insertActiveSeller();
        Instant now = Instant.now();
        Ubicacion firstLocation = saveLocation(now);
        Ubicacion secondLocation = saveLocation(now);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> tryCreate(userId, firstLocation, ready, start));
            Future<Boolean> second = executor.submit(() -> tryCreate(userId, secondLocation, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            assertTrue(first.get(10, TimeUnit.SECONDS) ^ second.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private boolean tryCreate(
            UUID userId,
            Ubicacion location,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test did not start.");
        }
        try {
            emprendimientos.save(business(userId, location.id()));
            return true;
        } catch (DomainException exception) {
            if ("CONFLICT".equals(exception.code())) {
                return false;
            }
            throw exception;
        }
    }

    private UUID insertActiveSeller() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        String celular = "+573" + String.format(
                "%09d",
                ThreadLocalRandom.current().nextInt(1_000_000_000)
        );
        jdbcTemplate.update(
                """
                INSERT INTO usuario (
                    id, nombre, celular, password_hash, rol, estado,
                    celular_verificado_at, created_at, updated_at
                ) VALUES (?, 'Vendedor', ?, 'hash', 'VENDEDOR_AMBULANTE', 'ACTIVA', ?, ?, ?)
                """,
                userId,
                celular,
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now)
        );
        return userId;
    }

    private Ubicacion saveLocation(Instant now) {
        return ubicaciones.save(new Ubicacion(
                UUID.randomUUID(),
                11.2408,
                -74.211,
                null,
                "Zona de venta",
                now,
                now
        ));
    }

    private Emprendimiento business(UUID userId, UUID locationId) {
        Instant now = Instant.now();
        return new Emprendimiento(
                UUID.randomUUID(),
                userId,
                locationId,
                "Frutas Doña Rosa",
                CategoriaEmprendimiento.FRUTAS_VERDURAS,
                "Frutas frescas del día",
                LocalTime.of(6, 0),
                LocalTime.of(18, 0),
                EstadoEmprendimiento.ACTIVO,
                now,
                now
        );
    }
}
