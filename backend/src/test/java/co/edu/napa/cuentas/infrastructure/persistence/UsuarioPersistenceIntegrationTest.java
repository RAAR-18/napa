package co.edu.napa.cuentas.infrastructure.persistence;

import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.cuentas.domain.model.Usuario;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class UsuarioPersistenceIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres")
    );

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("napa.outbox.enabled", () -> "false");
    }

    @Autowired
    private UsuarioRepositoryPort usuarios;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentInsertsWithTheSamePhoneReturnOneSpecificConflict() throws Exception {
        String phone = phone();

        assertConcurrentConflict(user(phone, null), user(phone, null), "PHONE_ALREADY_REGISTERED");

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM usuario WHERE celular = ?", Integer.class, phone));
    }

    @Test
    void concurrentInsertsWithCaseInsensitiveEmailsReturnOneSpecificConflict() throws Exception {
        String email = "Ana." + UUID.randomUUID() + "@Example.com";

        assertConcurrentConflict(
                user(phone(), email), user(phone(), email.toLowerCase(Locale.ROOT)), "EMAIL_ALREADY_REGISTERED"
        );

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM usuario WHERE lower(correo) = lower(?)", Integer.class, email));
    }

    @Test
    void unrelatedIntegrityErrorsAreNotReportedAsAnExistingAccount() {
        assertThrows(DataIntegrityViolationException.class, () -> usuarios.save(user("invalid-phone", null)));
    }

    private void assertConcurrentConflict(Usuario first, Usuario second, String expectedCode) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<String> firstResult = workers.submit(() -> insert(first, ready, start));
            Future<String> secondResult = workers.submit(() -> insert(second, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "Both independent transactions must be ready");
            start.countDown();
            List<String> outcomes = List.of(
                    firstResult.get(20, TimeUnit.SECONDS), secondResult.get(20, TimeUnit.SECONDS)
            );
            assertEquals(List.of("CREATED", expectedCode), outcomes.stream().sorted().toList());
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private String insert(Usuario user, CountDownLatch ready, CountDownLatch start) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                ready.countDown();
                try {
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent insert was not released");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Concurrent insert was interrupted", exception);
                }
                usuarios.save(user);
            });
            return "CREATED";
        } catch (DomainException exception) {
            return exception.code();
        }
    }

    private Usuario user(String phone, String email) {
        Instant now = Instant.now();
        return new Usuario(UUID.randomUUID(), "Concurrent Registration", phone, email, "test-hash",
                RolUsuario.CLIENTE, EstadoUsuario.PENDIENTE_VERIFICACION, null, now, now);
    }

    private String phone() {
        return "+573" + ThreadLocalRandom.current().nextLong(100_000_000, 1_000_000_000);
    }
}
