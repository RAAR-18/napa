package co.edu.napa.tarifas;

import co.edu.napa.tarifas.application.ports.out.TarifaMinimaRepositoryPort;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("otp-console")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=file:src/main/resources/application.yml",
        "napa.auth.jwt-secret=tariff-integration-secret-at-least-32-bytes",
        "napa.pagos.cifrado.clave-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "napa.auth.admin-bootstrap.enabled=true",
        "napa.auth.admin-bootstrap.name=Tarifa Admin",
        "napa.auth.admin-bootstrap.phone=+573001234567",
        "napa.auth.admin-bootstrap.email=tarifa-admin@example.test",
        "napa.auth.admin-bootstrap.password=StrongPassword123",
        "napa.tarifa.minima-defecto=3050",
        "napa.outbox.enabled=false",
        "napa.verification.console-code=654321"
})
class TarifaMinimaIntegrationTest {
    private static final String ADMIN_EMAIL = "tarifa-admin@example.test";
    private static final String ADMIN_PASSWORD = "StrongPassword123";

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres")
    );

    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TarifaMinimaRepositoryPort tarifas;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("spring.data.redis.host", redis::getHost);
        properties.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @AfterAll
    static void closeClient() {
        client.close();
    }

    @BeforeEach
    void clearHistory() {
        jdbc.update("DELETE FROM tarifa_minima");
    }

    @Test
    void configuredDefaultIsUsedUntilAnAdministratorChangesIt() throws Exception {
        String admin = adminToken();

        HttpResponse<String> initial = request("GET", "/tarifa-minima", admin, null, null);
        assertEquals(200, initial.statusCode(), initial.body());
        assertEquals(3050, number(body(initial), "valor"));
        assertNull(body(initial).get("createdAt"));
        assertTrue(initial.headers().firstValue("Cache-Control").orElse("").contains("no-store"));

        HttpResponse<String> changed = request(
                "POST", "/tarifa-minima", admin, Map.of("valor", 4500), key());
        assertEquals(200, changed.statusCode(), changed.body());
        assertEquals(4500, number(body(changed), "valor"));
        assertNotNull(body(changed).get("createdAt"));

        HttpResponse<String> current = request("GET", "/tarifa-minima", admin, null, null);
        assertEquals(4500, number(body(current), "valor"));
        assertEquals(1, countHistory());
        assertEquals(
                jdbc.queryForObject("SELECT id FROM usuario WHERE correo = ?", UUID.class, ADMIN_EMAIL),
                jdbc.queryForObject("SELECT administrador_id FROM tarifa_minima", UUID.class)
        );
    }

    @Test
    void validatesValuesAndDoesNotRecordNoOpUpdates() throws Exception {
        String admin = adminToken();

        for (String invalid : new String[]{"0", "-1", "3050.5", "9223372036854775808"}) {
            HttpResponse<String> response = request(
                    "POST", "/tarifa-minima", admin, "{\"valor\":" + invalid + "}", key());
            assertEquals(400, response.statusCode(), response.body());
            assertEquals("VALIDATION_ERROR", body(response).get("code"));
        }
        assertEquals(0, countHistory());

        HttpResponse<String> noOp = request(
                "POST", "/tarifa-minima", admin, Map.of("valor", 3050), key());
        assertEquals(200, noOp.statusCode(), noOp.body());
        assertEquals(3050, number(body(noOp), "valor"));
        assertNull(body(noOp).get("createdAt"));
        assertEquals(0, countHistory());
    }

    @Test
    void repositoryRejectsChangesOutsideATransaction() {
        InvalidDataAccessApiUsageException failure = assertThrows(
                InvalidDataAccessApiUsageException.class,
                () -> tarifas.registrarSiCambio(4500, UUID.randomUUID(), 3050));
        assertEquals(IllegalStateException.class, failure.getCause().getClass());
        assertEquals(0, countHistory());
    }

    @Test
    void restrictsBothEndpointsToAdministrators() throws Exception {
        String clientToken = clientToken();
        assertEquals(403, request("GET", "/tarifa-minima", clientToken, null, null).statusCode());
        assertEquals(403, request(
                "POST", "/tarifa-minima", clientToken, Map.of("valor", 5000), key()).statusCode());
        assertEquals(0, countHistory());
        assertEquals(401, request("GET", "/tarifa-minima", null, null, null).statusCode());
    }

    @Test
    void concurrentChangesAreSerializedAndHistoryOrderMatchesTheEffectiveValue() throws Exception {
        String admin = adminToken();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> updateWhenReady(start, admin, 5200));
            var second = executor.submit(() -> updateWhenReady(start, admin, 5300));
            start.countDown();

            HttpResponse<String> firstResponse = first.get(20, TimeUnit.SECONDS);
            HttpResponse<String> secondResponse = second.get(20, TimeUnit.SECONDS);
            assertEquals(200, firstResponse.statusCode(), firstResponse.body());
            assertEquals(200, secondResponse.statusCode(), secondResponse.body());
        }

        assertEquals(2, countHistory());
        long latestHistoryValue = jdbc.queryForObject(
                "SELECT valor FROM tarifa_minima ORDER BY id DESC LIMIT 1", Long.class);
        HttpResponse<String> current = request("GET", "/tarifa-minima", admin, null, null);
        assertEquals(200, current.statusCode(), current.body());
        assertEquals(latestHistoryValue, number(body(current), "valor"));
        assertTrue(latestHistoryValue == 5200 || latestHistoryValue == 5300);
    }

    private HttpResponse<String> updateWhenReady(CountDownLatch start, String admin, long value) throws Exception {
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent update was not released");
        }
        return request("POST", "/tarifa-minima", admin, Map.of("valor", value), key());
    }

    private String adminToken() throws Exception {
        HttpResponse<String> response = request(
                "POST",
                "/auth/login",
                null,
                Map.of("identificador", ADMIN_EMAIL, "password", ADMIN_PASSWORD),
                null
        );
        assertEquals(200, response.statusCode(), response.body());
        return body(response).get("accessToken").toString();
    }

    private String clientToken() throws Exception {
        String phone = "+573" + (System.nanoTime() % 900_000_000 + 100_000_000);
        String password = "client-password";
        HttpResponse<String> registered = request("POST", "/auth/register", null, Map.of(
                "nombre", "Tarifa Test",
                "celular", phone,
                "rol", "CLIENTE",
                "password", password,
                "confirmPassword", password
        ), key());
        assertEquals(201, registered.statusCode(), registered.body());

        HttpResponse<String> verified = request("POST", "/auth/verify-phone/confirm", null, Map.of(
                "celular", phone,
                "codigo", "654321"
        ), null);
        assertEquals(200, verified.statusCode(), verified.body());
        return body(verified).get("accessToken").toString();
    }

    private HttpResponse<String> request(
            String method,
            String path,
            String token,
            Object value,
            String idempotencyKey
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1" + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-Device-Id", "tarifa-minima-test");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        String requestBody = value instanceof String text ? text
                : value == null ? "" : json.writeValueAsString(value);
        builder.method(method, requestBody.isEmpty()
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(requestBody));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, Object> body(HttpResponse<String> response) throws Exception {
        return json.readValue(response.body(), new TypeReference<>() {});
    }

    private long number(Map<String, Object> response, String name) {
        return ((Number) response.get(name)).longValue();
    }

    private int countHistory() {
        return jdbc.queryForObject("SELECT count(*) FROM tarifa_minima", Integer.class);
    }

    private String key() {
        return UUID.randomUUID().toString();
    }
}
