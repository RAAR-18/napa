package co.edu.napa.cuentas.infrastructure.adapters.in.web;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("otp-console")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=file:src/main/resources/application.yml",
        "napa.auth.jwt-secret=console-integration-test-secret-at-least-32-bytes",
        "napa.auth.admin-bootstrap.enabled=false",
        "napa.outbox.enabled=false",
        "NAPA_OTP_CONSOLE_CODE=654321"
})
class ConsoleIdentityIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres")
    );

    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterAll
    static void closeClient() {
        client.close();
    }

    @Test
    void documentedProfileSupportsRegistrationVerificationLoginAndPasswordRotation() throws Exception {
        String phone = phone();
        String device = UUID.randomUUID().toString();
        register(phone, device);

        HttpResponse<String> wrongCode = post("/verify-phone/confirm", Map.of(
                "celular", phone, "codigo", "123456"
        ), device, null);
        assertEquals(400, wrongCode.statusCode(), wrongCode.body());
        assertEquals("OTP_INVALID", body(wrongCode).get("code"));

        HttpResponse<String> verified = post("/verify-phone/confirm", Map.of(
                "celular", phone, "codigo", "654321"
        ), device, null);
        assertEquals(200, verified.statusCode(), verified.body());
        Map<String, Object> verifiedTokens = body(verified);
        assertEquals("CLIENTE", ((Map<?, ?>) verifiedTokens.get("usuario")).get("rol"));

        HttpResponse<String> replay = post("/verify-phone/confirm", Map.of(
                "celular", phone, "codigo", "654321"
        ), device, null);
        assertEquals(400, replay.statusCode(), replay.body());
        assertEquals("OTP_EXPIRED", body(replay).get("code"));

        HttpResponse<String> loggedIn = post("/login", Map.of(
                "identificador", phone, "password", "initial-password"
        ), device, null);
        assertEquals(200, loggedIn.statusCode(), loggedIn.body());
        Map<String, Object> loginTokens = body(loggedIn);

        HttpResponse<String> changed = post("/password/change", Map.of(
                "passwordActual", "initial-password", "passwordNueva", "updated-password"
        ), device, (String) loginTokens.get("accessToken"));
        assertEquals(200, changed.statusCode(), changed.body());
        Map<String, Object> newTokens = body(changed);
        assertNotEquals(loginTokens.get("refreshToken"), newTokens.get("refreshToken"));

        // La sesión creada por la verificación fue revocada al cambiar la contraseña.
        HttpResponse<String> revoked = post("/refresh", Map.of(
                "refreshToken", verifiedTokens.get("refreshToken")
        ), device, null);
        assertEquals(401, revoked.statusCode(), revoked.body());

        HttpResponse<String> oldRefresh = post("/refresh", Map.of(
                "refreshToken", loginTokens.get("refreshToken")
        ), device, null);
        assertEquals(401, oldRefresh.statusCode(), oldRefresh.body());

        HttpResponse<String> renewed = post("/refresh", Map.of(
                "refreshToken", newTokens.get("refreshToken")
        ), device, null);
        assertEquals(200, renewed.statusCode(), renewed.body());

        HttpResponse<String> oldPassword = post("/login", Map.of(
                "identificador", phone, "password", "initial-password"
        ), device, null);
        assertEquals(401, oldPassword.statusCode(), oldPassword.body());

        HttpResponse<String> newPassword = post("/login", Map.of(
                "identificador", phone, "password", "updated-password"
        ), device, null);
        assertEquals(200, newPassword.statusCode(), newPassword.body());
    }

    @Test
    void fiveWrongAttemptsPreventActivationEvenWithTheCorrectCode() throws Exception {
        String phone = phone();
        String device = UUID.randomUUID().toString();
        register(phone, device);

        for (int attempt = 1; attempt <= 5; attempt++) {
            HttpResponse<String> response = post("/verify-phone/confirm", Map.of(
                    "celular", phone, "codigo", "000000"
            ), device, null);
            assertEquals(attempt < 5 ? 400 : 409, response.statusCode(), response.body());
            assertEquals(attempt < 5 ? "OTP_INVALID" : "OTP_ATTEMPTS_EXHAUSTED", body(response).get("code"));
        }

        HttpResponse<String> correctAfterExhaustion = post("/verify-phone/confirm", Map.of(
                "celular", phone, "codigo", "654321"
        ), device, null);
        assertNotEquals(200, correctAfterExhaustion.statusCode(), correctAfterExhaustion.body());

        HttpResponse<String> pendingAccount = post("/login", Map.of(
                "identificador", phone, "password", "initial-password"
        ), device, null);
        assertEquals(403, pendingAccount.statusCode(), pendingAccount.body());
        assertEquals("ACCOUNT_NOT_VERIFIED", body(pendingAccount).get("code"));
    }

    @Test
    void simultaneousRegistrationsWithTheSameNormalizedPhoneReturnCreatedAndConflict() throws Exception {
        String phone = phone();
        assertRegistrationConflict(
                registration(phone), registration(phone.substring(3)), "PHONE_ALREADY_REGISTERED"
        );
    }

    @Test
    void simultaneousRegistrationsWithCaseInsensitiveEmailsReturnCreatedAndConflict() throws Exception {
        String email = "Ana." + UUID.randomUUID() + "@Example.com";
        Map<String, Object> first = new java.util.HashMap<>(registration(phone()));
        Map<String, Object> second = new java.util.HashMap<>(registration(phone()));
        first.put("correo", email);
        second.put("correo", email.toLowerCase(Locale.ROOT));

        assertRegistrationConflict(first, second, "EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void browserRegistrationPreflightAllowsTheFrontendHeadersWithoutAuthentication() throws Exception {
        HttpResponse<String> response = preflight("/api/v1/auth/register", "http://localhost:8081",
                "content-type,x-device-id,idempotency-key");
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("http://localhost:8081", response.headers()
                .firstValue("Access-Control-Allow-Origin").orElseThrow());
        String allowed = response.headers().firstValue("Access-Control-Allow-Headers")
                .orElseThrow().toLowerCase(Locale.ROOT);
        assertTrue(allowed.contains("x-device-id"));
        assertTrue(allowed.contains("idempotency-key"));
        assertTrue(response.headers().firstValue("Access-Control-Allow-Credentials").isEmpty());
    }

    @Test
    void browserProtectedPreflightAllowsAuthorizationWithoutBypassingAuthentication() throws Exception {
        HttpResponse<String> preflight = preflight("/api/v1/auth/password/change", "http://127.0.0.1:8081",
                "content-type,authorization,idempotency-key");
        assertEquals(200, preflight.statusCode(), preflight.body());
        assertEquals("http://127.0.0.1:8081", preflight.headers()
                .firstValue("Access-Control-Allow-Origin").orElseThrow());

        HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/emprendimientos"))
                .timeout(Duration.ofSeconds(10)).header("Origin", "http://localhost:8081")
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode(), response.body());
        assertEquals("UNAUTHORIZED", body(response).get("code"));
        assertEquals("http://localhost:8081", response.headers()
                .firstValue("Access-Control-Allow-Origin").orElseThrow());
    }

    @Test
    void browserOriginsOutsideTheConfiguredListAreRejected() throws Exception {
        HttpResponse<String> response = preflight("/api/v1/auth/register", "https://untrusted.example",
                "content-type");
        assertEquals(403, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    @Test
    void browserPreflightDoesNotAuthorizeUnexpectedHeaders() throws Exception {
        HttpResponse<String> response = preflight("/api/v1/auth/register", "http://localhost:8081",
                "content-type,x-unexpected-header");
        assertEquals(200, response.statusCode(), response.body());
        String allowed = response.headers().firstValue("Access-Control-Allow-Headers")
                .orElseThrow().toLowerCase(Locale.ROOT);
        assertTrue(allowed.contains("content-type"));
        assertTrue(!allowed.contains("x-unexpected-header"));
    }

    @Test
    void nativeRequestsWithoutOriginStillRequireAuthentication() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/emprendimientos"))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode(), response.body());
        assertEquals("UNAUTHORIZED", body(response).get("code"));
    }

    private HttpResponse<String> preflight(String path, String origin, String headers) throws Exception {
        return client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("Origin", origin).header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", headers)
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void assertRegistrationConflict(Map<String, ?> first, Map<String, ?> second, String expectedCode)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<HttpResponse<String>> firstResult = workers.submit(() -> concurrentRegistration(first, ready, start));
            Future<HttpResponse<String>> secondResult = workers.submit(() -> concurrentRegistration(second, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<HttpResponse<String>> results = List.of(
                    firstResult.get(20, TimeUnit.SECONDS), secondResult.get(20, TimeUnit.SECONDS)
            );
            assertEquals(List.of(201, 409), results.stream().map(HttpResponse::statusCode).sorted().toList(),
                    () -> results.stream().map(HttpResponse::body).toList().toString());
            HttpResponse<String> conflict = results.stream()
                    .filter(result -> result.statusCode() == 409).findFirst().orElseThrow();
            assertEquals(expectedCode, body(conflict).get("code"));
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private HttpResponse<String> concurrentRegistration(Map<String, ?> payload, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        return post("/register", payload, UUID.randomUUID().toString(), null);
    }

    private Map<String, Object> registration(String phone) {
        return Map.of(
                "nombre", "Console Integration", "celular", phone,
                "password", "initial-password", "confirmPassword", "initial-password", "rol", "CLIENTE"
        );
    }

    private void register(String phone, String device) throws Exception {
        HttpResponse<String> response = post("/register", registration(phone), device, null);
        assertEquals(201, response.statusCode(), response.body());
        assertEquals("PENDIENTE_VERIFICACION", body(response).get("estado"));
        assertEquals("ENVIADO", ((Map<?, ?>) body(response).get("verificacion")).get("estado"));
    }

    private HttpResponse<String> post(String path, Map<String, ?> payload, String device, String accessToken)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/auth" + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-Device-Id", device)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, Object> body(HttpResponse<String> response) {
        return objectMapper.readValue(response.body(), new TypeReference<>() {});
    }

    private String phone() {
        return "+573" + ThreadLocalRandom.current().nextLong(100_000_000, 1_000_000_000);
    }
}
