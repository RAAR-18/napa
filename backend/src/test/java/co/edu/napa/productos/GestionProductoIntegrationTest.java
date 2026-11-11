package co.edu.napa.productos;

import co.edu.napa.productos.application.ports.out.PedidosPendientesProductoPort;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("otp-console")
@Import(GestionProductoIntegrationTest.PedidosPrueba.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=file:src/main/resources/application.yml",
        "napa.auth.jwt-secret=product-management-tests-secret-at-least-32-bytes",
        "napa.auth.admin-bootstrap.enabled=false", "napa.outbox.enabled=false", "NAPA_OTP_CONSOLE_CODE=654321"
})
class GestionProductoIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));
    @Container static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    static final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PedidosPendientesProductoPort pedidos;

    @DynamicPropertySource static void services(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @BeforeEach void noPendingOrders() { reset(pedidos); when(pedidos.contar(any())).thenReturn(0L); }
    @AfterAll static void close() { client.close(); }

    @TestConfiguration(proxyBeanMethods = false)
    static class PedidosPrueba {
        @Bean @Primary PedidosPendientesProductoPort pedidosPrueba() { return mock(PedidosPendientesProductoPort.class); }
    }

    @Test
    void browserCanPreflightVersionedEditing() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/productos/" + UUID.randomUUID()))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:8081")
                .header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "authorization,content-type,if-match").build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("http://localhost:8081", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertTrue(response.headers().firstValue("Access-Control-Allow-Headers").orElseThrow().toLowerCase().contains("if-match"));
        assertTrue(response.headers().firstValue("Access-Control-Expose-Headers").orElseThrow().toLowerCase().contains("etag"));
    }

    @Test
    void editsPartialAndMultipleFieldsAndImmediatelyUpdatesBothListings() throws Exception {
        Actor seller = seller();
        String customer = account("CLIENTE");
        String id = create(seller.token(), "Mango");
        var before = ok("GET", "/productos/" + id, seller.token(), null, null);
        var response = ok("PATCH", "/productos/" + id, seller.token(), Map.of("precio", 4500), etag(before));
        assertEquals(4500, ((Number) body(response).get("precio")).intValue());
        assertEquals("Mango", body(response).get("nombre"));
        assertEquals("Del día", body(response).get("descripcion"));
        assertNotEquals(etag(before), etag(response));
        response = ok("PATCH", "/productos/" + id, seller.token(),
                Map.of("nombre", "  Mango dulce  ", "precio", 5000, "descripcion", "Maduro"), etag(response));
        assertEquals("Mango dulce", body(response).get("nombre"));
        assertEquals("KG", body(response).get("unidad"));
        assertEquals(new BigDecimal("2.50"), jdbc.queryForObject("SELECT cantidad_disponible FROM producto WHERE id = ?",
                BigDecimal.class, UUID.fromString(id)));
        for (String path : List.of("/productos", "/emprendimientos/" + seller.business() + "/productos")) {
            var item = items(ok("GET", path, path.equals("/productos") ? seller.token() : customer, null, null)).getFirst();
            assertEquals("Mango dulce", item.get("nombre"));
            assertEquals(5000, ((Number) item.get("precio")).intValue());
        }
        var clear = new HashMap<String, Object>(); clear.put("descripcion", null);
        assertNull(body(ok("PATCH", "/productos/" + id, seller.token(), clear, etag(response))).get("descripcion"));
    }

    @Test
    void rejectsInvalidOrNonEditableFieldsWithoutApplyingAnyPart() throws Exception {
        Actor seller = seller(); String id = create(seller.token(), "Original");
        var before = ok("GET", "/productos/" + id, seller.token(), null, null);
        for (Object invalid : List.of(Map.of(), Map.of("nombre", ""), Map.of("nombre", "x".repeat(101)),
                Map.of("nombre", "Otro", "precio", -1), Map.of("precio", 0),
                Map.of("precio", new BigDecimal("3500.0000000000001")),
                Map.of("precio", new BigDecimal("9007199254740992")), Map.of("precio", "4500"),
                Map.of("descripcion", "x".repeat(501)), Map.of("descripcion", 123),
                Map.of("unidad", "LIBRA"), Map.of("cantidadDisponible", 20), Map.of("estado", "AGOTADO"),
                Map.of("emprendimientoId", UUID.randomUUID()))) {
            var response = request("PATCH", "/productos/" + id, seller.token(), invalid, null, etag(before));
            assertEquals(400, response.statusCode(), response.body());
        }
        var nullName = new HashMap<String, Object>(); nullName.put("nombre", null);
        assertEquals(400, request("PATCH", "/productos/" + id, seller.token(), nullName, null, null).statusCode());
        var after = ok("GET", "/productos/" + id, seller.token(), null, null);
        assertEquals(body(before), body(after));
        assertEquals(etag(before), etag(after));
    }

    @Test
    void rejectsDuplicateNamesAtomicallyAndUsesCurrentStockWhenEditingExhaustedProducts() throws Exception {
        Actor seller = seller(); create(seller.token(), "Mango"); String id = create(seller.token(), "Papaya");
        var conflict = request("PATCH", "/productos/" + id, seller.token(),
                Map.of("nombre", " MANGO ", "precio", 9500), null, null);
        assertEquals(409, conflict.statusCode(), conflict.body());
        assertEquals("CONFLICT", body(conflict).get("code"));
        var unchanged = body(ok("GET", "/productos/" + id, seller.token(), null, null));
        assertEquals("Papaya", unchanged.get("nombre"));
        assertEquals(3500, ((Number) unchanged.get("precio")).intValue());
        ok("POST", "/productos/" + id + "/inventario", seller.token(), Map.of("cantidadDisponible", 0), null);
        var updated = body(ok("PATCH", "/productos/" + id, seller.token(), Map.of("precio", 4000), null));
        assertEquals("AGOTADO", updated.get("estado")); assertEquals(true, updated.get("soloReserva"));
        assertEquals(0, ((Number) updated.get("cantidadDisponible")).intValue());
    }

    @Test
    void onlyOwnerCanReadEditOrDeleteAndConfirmationMustBeAnExplicitBoolean() throws Exception {
        Actor owner = seller(); String id = create(owner.token(), "Privado");
        Actor other = seller(); String customer = account("CLIENTE");
        for (String token : List.of(other.token(), customer)) {
            assertEquals(403, request("GET", "/productos/" + id, token, null, null, null).statusCode());
            assertEquals(403, request("PATCH", "/productos/" + id, token, Map.of("precio", 2000), null, null).statusCode());
            assertEquals(403, request("POST", "/productos/" + id + "/eliminar", token,
                    Map.of("confirmar", true), key(), null).statusCode());
        }
        assertEquals(401, request("PATCH", "/productos/" + id, null, Map.of("precio", 2000), null, null).statusCode());
        for (Object confirmation : List.of(Map.of(), Map.of("confirmar", false), Map.of("confirmar", "true"),
                Map.of("confirmar", true, "extra", true))) {
            assertEquals(400, request("POST", "/productos/" + id + "/eliminar", owner.token(), confirmation, key(), null).statusCode());
        }
        assertEquals(400, request("POST", "/productos/" + id + "/eliminar", owner.token(), Map.of("confirmar", true), null, null).statusCode());
        assertEquals(404, request("GET", "/productos/" + key(), owner.token(), null, null, null).statusCode());
        assertEquals(1, items(ok("GET", "/productos", owner.token(), null, null)).size());
    }

    @Test
    void softDeletionHidesBothListingsFreesTheNameAndReplaysTheSameRequest() throws Exception {
        Actor seller = seller(); String customer = account("CLIENTE"); String id = create(seller.token(), "Mango");
        String deletionKey = key();
        var response = request("POST", "/productos/" + id + "/eliminar", seller.token(), Map.of("confirmar", true), deletionKey, null);
        assertEquals(204, response.statusCode(), response.body()); assertTrue(response.body().isEmpty());
        assertEquals(204, request("POST", "/productos/" + id + "/eliminar", seller.token(), Map.of("confirmar", true), deletionKey, null).statusCode());
        assertEquals(409, request("POST", "/productos/" + id + "/eliminar", seller.token(), Map.of("confirmar", true), key(), null).statusCode());
        assertEquals("ELIMINADO", jdbc.queryForObject("SELECT estado FROM producto WHERE id = ?", String.class, UUID.fromString(id)));
        assertTrue(items(ok("GET", "/productos", seller.token(), null, null)).isEmpty());
        assertTrue(items(ok("GET", "/emprendimientos/" + seller.business() + "/productos", customer, null, null)).isEmpty());
        assertEquals(409, request("PATCH", "/productos/" + id, seller.token(), Map.of("precio", 4000), null, null).statusCode());
        assertNotEquals(id, create(seller.token(), "MANGO"));
    }

    @Test
    void pendingOrdersAndVerificationFailuresPreventDeletion() throws Exception {
        Actor seller = seller(); String id = create(seller.token(), "Con pedidos"); UUID productId = UUID.fromString(id);
        when(pedidos.contar(productId)).thenReturn(2L);
        var response = request("POST", "/productos/" + id + "/eliminar", seller.token(), Map.of("confirmar", true), key(), null);
        assertEquals(409, response.statusCode(), response.body()); assertEquals("HAS_PENDING_ORDERS", body(response).get("code"));
        assertEquals(2, ((Number) ((Map<?, ?>) body(response).get("details")).get("pedidosPendientes")).intValue());
        when(pedidos.contar(productId)).thenReturn(-1L);
        assertEquals(503, request("POST", "/productos/" + id + "/eliminar", seller.token(), Map.of("confirmar", true), key(), null).statusCode());
        assertEquals("DISPONIBLE", body(ok("GET", "/productos/" + id, seller.token(), null, null)).get("estado"));
    }

    @Test
    void twoEditorsOfTheSameVersionCannotSilentlyOverwriteEachOther() throws Exception {
        Actor seller = seller(); String id = create(seller.token(), "Concurrente");
        String version = etag(ok("GET", "/productos/" + id, seller.token(), null, null));
        var results = concurrent(
                () -> request("PATCH", "/productos/" + id, seller.token(), Map.of("nombre", "Primero"), null, version),
                () -> request("PATCH", "/productos/" + id, seller.token(), Map.of("nombre", "Segundo"), null, version));
        assertEquals(List.of(200, 409), results.stream().map(HttpResponse::statusCode).sorted().toList());
        assertEquals(409, request("PATCH", "/productos/" + id, seller.token(), Map.of("precio", 7000), null, version).statusCode());
        assertEquals(400, request("PATCH", "/productos/" + id, seller.token(), Map.of("precio", 7000), null, "bad").statusCode());
    }

    @Test
    void editAndInventoryRaceDoNotRestoreOldStock() throws Exception {
        Actor seller = seller(); String id = create(seller.token(), "Concurrente");
        String version = etag(ok("GET", "/productos/" + id, seller.token(), null, null));
        var results = concurrent(
                () -> request("PATCH", "/productos/" + id, seller.token(), Map.of("precio", 5500), null, version),
                () -> request("POST", "/productos/" + id + "/inventario", seller.token(), Map.of("cantidadDisponible", 0), key(), null));
        assertEquals(200, results.get(1).statusCode(), results.get(1).body());
        assertTrue(List.of(200, 409).contains(results.getFirst().statusCode()));
        var product = body(ok("GET", "/productos/" + id, seller.token(), null, null));
        assertEquals("AGOTADO", product.get("estado")); assertEquals(0, ((Number) product.get("cantidadDisponible")).intValue());
    }

    @Test
    void deleteAndEditRaceCannotResurrectTheProduct() throws Exception {
        Actor seller = seller(); String id = create(seller.token(), "Retirado");
        String version = etag(ok("GET", "/productos/" + id, seller.token(), null, null));
        var results = concurrent(
                () -> request("PATCH", "/productos/" + id, seller.token(), Map.of("precio", 5500), null, version),
                () -> request("POST", "/productos/" + id + "/eliminar", seller.token(), Map.of("confirmar", true), key(), null));
        assertEquals(204, results.get(1).statusCode(), results.get(1).body());
        assertTrue(List.of(200, 409).contains(results.getFirst().statusCode()));
        assertEquals("ELIMINADO", jdbc.queryForObject("SELECT estado FROM producto WHERE id = ?", String.class, UUID.fromString(id)));
    }

    private List<HttpResponse<String>> concurrent(java.util.concurrent.Callable<HttpResponse<String>> first,
                                                  java.util.concurrent.Callable<HttpResponse<String>> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> { ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS)); return first.call(); });
            var b = workers.submit(() -> { ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS)); return second.call(); });
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally { start.countDown(); }
    }

    private Actor seller() throws Exception {
        String token = account("VENDEDOR_AMBULANTE");
        var result = request("POST", "/emprendimientos", token, Map.of("nombre", "Gestión de frutas", "categoria", "FRUTAS_VERDURAS",
                "descripcion", "Fruta local", "horaApertura", "06:00", "horaCierre", "18:00",
                "ubicacion", Map.of("latitud", 4.71, "longitud", -74.07)), key(), null);
        assertEquals(201, result.statusCode(), result.body()); return new Actor(token, body(result).get("id").toString());
    }
    private String account(String role) throws Exception {
        String phone = "+573" + ThreadLocalRandom.current().nextLong(100_000_000, 1_000_000_000);
        var registered = request("POST", "/auth/register", null, Map.of("nombre", "Gestión Test", "celular", phone,
                "rol", role, "password", "test-password", "confirmPassword", "test-password"), key(), null);
        assertEquals(201, registered.statusCode(), registered.body());
        return body(ok("POST", "/auth/verify-phone/confirm", null, Map.of("celular", phone, "codigo", "654321"), null)).get("accessToken").toString();
    }
    private String create(String token, String name) throws Exception {
        var result = request("POST", "/productos", token, Map.of("nombre", name, "precio", 3500, "unidad", "KG",
                "descripcion", "Del día", "cantidadDisponible", new BigDecimal("2.50")), key(), null);
        assertEquals(201, result.statusCode(), result.body()); return body(result).get("id").toString();
    }
    private HttpResponse<String> ok(String method, String path, String token, Object value, String version) throws Exception {
        var result = request(method, path, token, value, method.equals("POST") ? key() : null, version);
        assertEquals(200, result.statusCode(), result.body()); return result;
    }
    private HttpResponse<String> request(String method, String path, String token, Object value, String idempotency, String version) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .timeout(Duration.ofSeconds(15)).header("Origin", "http://localhost:8081")
                .header("Content-Type", "application/json").header("X-Device-Id", "management-test");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (idempotency != null) builder.header("Idempotency-Key", idempotency);
        if (version != null) builder.header("If-Match", version);
        return client.send(builder.method(method, value == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(value))).build(), HttpResponse.BodyHandlers.ofString());
    }
    private Map<String, Object> body(HttpResponse<String> response) { return json.readValue(response.body(), new TypeReference<>() {}); }
    @SuppressWarnings("unchecked") private List<Map<String, Object>> items(HttpResponse<String> response) {
        return (List<Map<String, Object>>) body(response).get("content");
    }
    private String etag(HttpResponse<String> response) { return response.headers().firstValue("ETag").orElseThrow(); }
    private String key() { return UUID.randomUUID().toString(); }
    record Actor(String token, String business) {}
}
