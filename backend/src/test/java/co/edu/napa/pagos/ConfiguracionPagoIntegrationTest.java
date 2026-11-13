package co.edu.napa.pagos;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("otp-console")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=file:src/main/resources/application.yml",
        "napa.auth.jwt-secret=payment-config-integration-secret-at-least-32-bytes",
        "napa.pagos.cifrado.clave-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "napa.auth.admin-bootstrap.enabled=false",
        "napa.outbox.enabled=false",
        "NAPA_OTP_CONSOLE_CODE=654321"
})
class ConfiguracionPagoIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres")
    );

    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

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
    void clearConfigurations() {
        jdbc.update("DELETE FROM configuracion_pago");
    }

    @Test
    void storesEncryptedAccountAndOnlyReturnsSellerEnabledMethods() throws Exception {
        String vendorToken = registerAndVerify("VENDEDOR_PUNTO_FIJO");
        HttpResponse<String> initial = request("GET", vendorToken, null);
        assertEquals(200, initial.statusCode(), initial.body());
        assertTrue(initial.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        assertEquals(false, body(initial).get("aceptaEfectivo"));
        assertTrue(((java.util.List<?>) body(initial).get("mediosHabilitados")).isEmpty());
        assertEquals(false, body(initial).get("domicilioDisponible"));

        HttpResponse<String> saved = request("PUT", vendorToken, Map.of(
                "aceptaEfectivo", true,
                "cuentaBancaria", Map.of(
                        "banco", " Banco Ejemplo ",
                        "tipoCuenta", "AHORROS",
                        "numeroCuenta", "0000123456"
                )
        ));
        assertEquals(200, saved.statusCode(), saved.body());
        Map<String, Object> response = body(saved);
        assertEquals(true, response.get("aceptaEfectivo"));
        assertEquals(true, response.get("domicilioDisponible"));
        assertEquals(java.util.List.of("EFECTIVO", "CUENTA_BANCARIA"), response.get("mediosHabilitados"));
        assertEquals("Banco Ejemplo", ((Map<?, ?>) response.get("cuentaBancaria")).get("banco"));
        assertEquals("0000123456", ((Map<?, ?>) response.get("cuentaBancaria")).get("numeroCuenta"));

        String storedAccount = jdbc.queryForObject(
                "SELECT numero_cuenta_cifrada FROM configuracion_pago", String.class);
        assertTrue(storedAccount.startsWith("v1."));
        assertNotEquals("0000123456", storedAccount);

        Map<String, Object> removeAccount = new HashMap<>();
        removeAccount.put("aceptaEfectivo", false);
        removeAccount.put("cuentaBancaria", null);
        HttpResponse<String> removed = request("PUT", vendorToken, removeAccount);
        assertEquals(200, removed.statusCode(), removed.body());
        assertEquals(false, body(removed).get("domicilioDisponible"));
        assertTrue(((java.util.List<?>) body(removed).get("mediosHabilitados")).isEmpty());
        assertFalse(jdbc.queryForObject(
                "SELECT acepta_efectivo FROM configuracion_pago", Boolean.class));
    }

    @Test
    void rejectsInvalidAccountsAndUnknownFieldsWithoutPersistingThem() throws Exception {
        String vendorToken = registerAndVerify("VENDEDOR_AMBULANTE");
        Map<String, Object> invalidAccount = Map.of(
                "aceptaEfectivo", true,
                "cuentaBancaria", Map.of(
                        "banco", "Banco",
                        "tipoCuenta", "AHORROS",
                        "numeroCuenta", "12AB",
                        "numeroTarjeta", "4111111111111111"
                )
        );

        HttpResponse<String> invalid = request("PUT", vendorToken, invalidAccount);

        assertEquals(400, invalid.statusCode(), invalid.body());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM configuracion_pago", Integer.class));
    }

    @Test
    void restrictsConfigurationToSellers() throws Exception {
        String clientToken = registerAndVerify("CLIENTE");

        assertEquals(403, request("GET", clientToken, null).statusCode());
        assertEquals(401, request("GET", null, null).statusCode());
    }

    private String registerAndVerify(String role) throws Exception {
        String phone = "+573" + ThreadLocalRandom.current().nextLong(100_000_000, 1_000_000_000);
        String deviceId = UUID.randomUUID().toString();
        HttpResponse<String> registration = requestAuth("POST", "/register", null, Map.of(
                "nombre", "Payment Config Test",
                "celular", phone,
                "password", "initial-password",
                "confirmPassword", "initial-password",
                "rol", role
        ), deviceId);
        assertEquals(201, registration.statusCode(), registration.body());

        HttpResponse<String> verified = requestAuth("POST", "/verify-phone/confirm", null, Map.of(
                "celular", phone,
                "codigo", "654321"
        ), deviceId);
        assertEquals(200, verified.statusCode(), verified.body());
        return body(verified).get("accessToken").toString();
    }

    private HttpResponse<String> request(String method, String token, Object value) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/vendedores/me/configuracion-pago"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-Device-Id", "payment-config-test");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        String requestBody = value == null ? "" : json.writeValueAsString(value);
        builder.method(method, requestBody.isEmpty()
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(requestBody));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> requestAuth(
            String method,
            String path,
            String token,
            Object value,
            String deviceId
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/auth" + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-Device-Id", deviceId)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(value)));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, Object> body(HttpResponse<String> response) throws Exception {
        return json.readValue(response.body(), new TypeReference<>() {});
    }
}
