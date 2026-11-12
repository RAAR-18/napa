package co.edu.napa.productos;

import co.edu.napa.productos.application.ports.in.ConsultarDisponibilidadPort;
import org.junit.jupiter.api.AfterAll;
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

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("otp-console")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=file:src/main/resources/application.yml",
        "napa.auth.jwt-secret=products-integration-secret-at-least-32-bytes",
        "napa.pagos.cifrado.clave-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "napa.auth.admin-bootstrap.enabled=false", "napa.outbox.enabled=false", "NAPA_OTP_CONSOLE_CODE=654321"
})
class ProductosIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    static final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConsultarDisponibilidadPort disponibilidad;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("spring.data.redis.host", redis::getHost);
        properties.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @AfterAll static void close() { client.close(); }

    @Test
    void sellerPublishesDecimalStockAndClientSeesExhaustionAndRestocking() throws Exception {
        String seller = account("VENDEDOR_PUNTO_FIJO");
        String business = business(seller);
        String customer = account("CLIENTE");
        var created = request("POST", "/productos", seller, product("Mango", "12.5"), key());
        assertEquals(201, created.statusCode(), created.body());
        assertEquals("http://localhost:8081", created.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        String id = body(created).get("id").toString();
        assertTrue(created.headers().firstValue("Location").orElseThrow().endsWith(id));
        var own = body(ok("GET", "/productos", seller, null));
        assertEquals(1, ((Number) own.get("totalElements")).intValue());
        var item = items(ok("GET", "/emprendimientos/" + business + "/productos", customer, null)).getFirst();
        assertEquals("Mango", item.get("nombre"));
        assertEquals("KG", item.get("unidad"));
        assertEquals(0, new BigDecimal(item.get("cantidadDisponible").toString()).compareTo(new BigDecimal("12.5")));
        assertEquals(3500, ((Number) item.get("precio")).intValue());
        assertEquals(false, item.get("soloReserva"));

        ok("POST", "/productos/" + id + "/inventario", seller, Map.of("cantidadDisponible", 0));
        item = items(ok("GET", "/emprendimientos/" + business + "/productos", customer, null)).getFirst();
        assertEquals("AGOTADO", item.get("estado"));
        assertEquals(true, item.get("soloReserva"));
        ok("POST", "/productos/" + id + "/inventario", seller, Map.of("cantidadDisponible", new BigDecimal("2.25")));
        item = items(ok("GET", "/emprendimientos/" + business + "/productos", customer, null)).getFirst();
        assertEquals("DISPONIBLE", item.get("estado"));
        assertEquals(false, item.get("soloReserva"));
    }

    @Test
    void availabilityLookupReturnsCurrentStockForPublishedProductsOfOnlyRequestedBusiness() throws Exception {
        String seller = account("VENDEDOR_PUNTO_FIJO");
        String business = business(seller);
        String otherSeller = account("VENDEDOR_AMBULANTE");
        String otherBusiness = business(otherSeller);
        String activeId = body(request("POST", "/productos", seller, product("Mango", "12.5"), key()))
                .get("id").toString();
        String exhaustedId = body(request("POST", "/productos", seller, product("Papaya", "1"), key()))
                .get("id").toString();
        String deletedId = body(request("POST", "/productos", seller, product("Guayaba", "3"), key()))
                .get("id").toString();
        String foreignId = body(request("POST", "/productos", otherSeller, product("Banano", "4"), key()))
                .get("id").toString();
        assertEquals(200, request("POST", "/productos/" + exhaustedId + "/inventario", seller,
                Map.of("cantidadDisponible", 0), key()).statusCode());
        jdbc.update("UPDATE producto SET estado = 'ELIMINADO' WHERE id = ?", UUID.fromString(deletedId));

        var result = disponibilidad.consultar(UUID.fromString(business), List.of(
                UUID.fromString(activeId), UUID.fromString(exhaustedId), UUID.fromString(deletedId),
                UUID.fromString(foreignId), UUID.randomUUID(), UUID.fromString(activeId)));

        assertEquals(2, result.size());
        var active = result.stream().filter(item -> item.productoId().equals(UUID.fromString(activeId)))
                .findFirst().orElseThrow();
        assertEquals(3500, active.precio());
        assertEquals(0, new BigDecimal("12.5").compareTo(active.cantidadDisponible()));
        assertEquals("DISPONIBLE", active.estadoProducto());
        var exhausted = result.stream().filter(item -> item.productoId().equals(UUID.fromString(exhaustedId)))
                .findFirst().orElseThrow();
        assertEquals(3500, exhausted.precio());
        assertEquals(0, BigDecimal.ZERO.compareTo(exhausted.cantidadDisponible()));
        assertEquals("AGOTADO", exhausted.estadoProducto());
        assertTrue(disponibilidad.consultar(UUID.fromString(otherBusiness), List.of()).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void clientCanQuoteImmediateDirectDeliveryUsingCurrentProductPriceAndStock() throws Exception {
        String seller = account("VENDEDOR_AMBULANTE");
        String business = business(seller);
        String customer = account("CLIENTE");
        String productId = body(request("POST", "/productos", seller, product("Mango", "12.5"), key()))
                .get("id").toString();
        Map<String, Object> quoteRequest = Map.of(
                "emprendimientoId", business,
                "modalidad", "ENTREGA_DIRECTA",
                "items", List.of(Map.of("productoId", productId, "cantidad", new BigDecimal("2.5"))),
                "ubicacionEntrega", Map.of(
                        "latitud", 4.72, "longitud", -74.06, "direccion", "Calle de prueba"));
        assertEquals(403, request("POST", "/pedidos/cotizacion", seller, quoteRequest, null).statusCode());
        assertEquals(401, request("POST", "/pedidos/cotizacion", null, quoteRequest, null).statusCode());
        var quote = request("POST", "/pedidos/cotizacion", customer, quoteRequest, null);

        assertEquals(200, quote.statusCode(), quote.body());
        var result = body(quote);
        assertEquals(8750, ((Number) result.get("subtotal")).longValue());
        assertEquals(8750, ((Number) result.get("total")).longValue());
        assertNull(result.get("tarifaDomicilio"));
        assertEquals(List.of("ENTREGA_DIRECTA"), result.get("modalidadesDisponibles"));
        assertEquals(List.of(), result.get("mediosPagoDisponibles"));
        assertNull(result.get("pagoDomicilio"));
        var items = (List<Map<String, Object>>) result.get("items");
        assertEquals(1, items.size());
        assertEquals("Mango", items.getFirst().get("nombre"));
        assertEquals("KG", items.getFirst().get("unidad"));
        assertEquals("DISPONIBLE", items.getFirst().get("estadoProducto"));
        assertEquals(8750, ((Number) items.getFirst().get("subtotal")).longValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void clientCreatesOrderOnceAndPendingStatesBlockProductDeletion() throws Exception {
        String seller = account("VENDEDOR_AMBULANTE");
        String business = business(seller);
        ok("PUT", "/vendedores/me/configuracion-pago", seller, Map.of("aceptaEfectivo", true));
        String customer = account("CLIENTE");
        String productId = body(request("POST", "/productos", seller, product("Mango", "12.5"), key()))
                .get("id").toString();
        Map<String, Object> orderRequest = new HashMap<>(Map.of(
                "emprendimientoId", business,
                "modalidad", "ENTREGA_DIRECTA",
                "items", List.of(Map.of("productoId", productId, "cantidad", new BigDecimal("2.5"))),
                "ubicacionEntrega", Map.of(
                        "latitud", 4.72, "longitud", -74.06, "direccion", "Calle de prueba"),
                "medioPago", "EFECTIVO",
                "totalEsperado", 8750));
        String idempotencyKey = key();

        var response = request("POST", "/pedidos", customer, orderRequest, idempotencyKey);

        assertEquals(201, response.statusCode(), response.body());
        var result = body(response);
        UUID orderId = UUID.fromString(result.get("id").toString());
        assertEquals("PENDIENTE", result.get("estado"));
        assertEquals("EFECTIVO", result.get("medioPago"));
        assertEquals(8750, ((Number) result.get("total")).longValue());
        assertTrue(response.headers().firstValue("Location").orElseThrow()
                .endsWith("/api/v1/pedidos/" + orderId));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM pedido WHERE id = ?", Long.class, orderId));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM item_pedido WHERE pedido_id = ?", Long.class, orderId));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_id = ? "
                + "AND event_type = 'pedidos.pedido-creado'", Long.class, orderId));
        UUID sellerId = jdbc.queryForObject("SELECT usuario_id FROM emprendimiento WHERE id = ?",
                UUID.class, UUID.fromString(business));
        assertEquals(sellerId, jdbc.queryForObject(
                "SELECT (payload->>'vendedorId')::uuid FROM outbox_event WHERE aggregate_id = ?",
                UUID.class, orderId));
        assertEquals(0, new BigDecimal("12.5").compareTo(jdbc.queryForObject(
                "SELECT cantidad_disponible FROM producto WHERE id = ?", BigDecimal.class, UUID.fromString(productId))));

        var deletion = request("POST", "/productos/" + productId + "/eliminar", seller,
                Map.of("confirmar", true), key());
        assertEquals(409, deletion.statusCode(), deletion.body());
        assertEquals("HAS_PENDING_ORDERS", body(deletion).get("code"));
        assertEquals(1, ((Number) ((Map<?, ?>) body(deletion).get("details"))
                .get("pedidosPendientes")).intValue());
        assertEquals("DISPONIBLE", jdbc.queryForObject(
                "SELECT estado FROM producto WHERE id = ?", String.class, UUID.fromString(productId)));

        var repeated = request("POST", "/pedidos", customer, orderRequest, idempotencyKey);
        assertEquals(201, repeated.statusCode(), repeated.body());
        assertEquals(orderId, UUID.fromString(body(repeated).get("id").toString()));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM pedido WHERE id = ?", Long.class, orderId));

        List<String> openStates = List.of("PENDIENTE", "ACEPTADO", "LISTO_PARA_RECOGER", "EN_CAMINO");
        List<String> terminalStates = List.of("RECHAZADO", "ENTREGADO", "CANCELADO");

        for (String estado : openStates) {
            String openProductId = body(request("POST", "/productos", seller,
                    product("Producto " + estado, "2"), key())).get("id").toString();
            Map<String, Object> openOrderRequest = new HashMap<>(Map.of(
                    "emprendimientoId", business,
                    "modalidad", "ENTREGA_DIRECTA",
                    "items", List.of(Map.of("productoId", openProductId, "cantidad", BigDecimal.ONE)),
                    "ubicacionEntrega", Map.of(
                            "latitud", 4.72, "longitud", -74.06, "direccion", "Calle de prueba"),
                    "medioPago", "EFECTIVO",
                    "totalEsperado", 3500));
            if (estado.equals("PENDIENTE")) {
                openOrderRequest.put("reserva", java.time.Instant.now().plusSeconds(3600).toString());
            }
            var created = request("POST", "/pedidos", customer, openOrderRequest, key());
            assertEquals(201, created.statusCode(), created.body());
            UUID openOrderId = UUID.fromString(body(created).get("id").toString());
            jdbc.update("UPDATE pedido SET estado = ? WHERE id = ?", estado, openOrderId);

            var openDeletion = request("POST", "/productos/" + openProductId + "/eliminar", seller,
                    Map.of("confirmar", true), key());

            assertEquals(409, openDeletion.statusCode(), openDeletion.body());
            assertEquals("HAS_PENDING_ORDERS", body(openDeletion).get("code"));
            assertEquals(1, ((Number) ((Map<?, ?>) body(openDeletion).get("details"))
                    .get("pedidosPendientes")).intValue());
        }

        for (String estado : terminalStates) {
            String terminalProductId = body(request("POST", "/productos", seller,
                    product("Producto " + estado, "2"), key())).get("id").toString();
            Map<String, Object> terminalOrderRequest = Map.of(
                    "emprendimientoId", business,
                    "modalidad", "ENTREGA_DIRECTA",
                    "items", List.of(Map.of("productoId", terminalProductId, "cantidad", BigDecimal.ONE)),
                    "ubicacionEntrega", Map.of(
                            "latitud", 4.72, "longitud", -74.06, "direccion", "Calle de prueba"),
                    "medioPago", "EFECTIVO",
                    "totalEsperado", 3500);
            var created = request("POST", "/pedidos", customer, terminalOrderRequest, key());
            assertEquals(201, created.statusCode(), created.body());
            UUID terminalOrderId = UUID.fromString(body(created).get("id").toString());
            jdbc.update("UPDATE pedido SET estado = ? WHERE id = ?", estado, terminalOrderId);

            var terminalDeletion = request("POST", "/productos/" + terminalProductId + "/eliminar", seller,
                    Map.of("confirmar", true), key());

            assertEquals(204, terminalDeletion.statusCode(), terminalDeletion.body());
        }
    }

    @Test
    void changedExpectedTotalDoesNotPersistOrderOrEmitEvent() throws Exception {
        String seller = account("VENDEDOR_AMBULANTE");
        String business = business(seller);
        ok("PUT", "/vendedores/me/configuracion-pago", seller, Map.of("aceptaEfectivo", true));
        String customer = account("CLIENTE");
        String productId = body(request("POST", "/productos", seller, product("Mango", "12.5"), key()))
                .get("id").toString();
        Map<String, Object> orderRequest = Map.of(
                "emprendimientoId", business,
                "modalidad", "ENTREGA_DIRECTA",
                "items", List.of(Map.of("productoId", productId, "cantidad", BigDecimal.ONE)),
                "ubicacionEntrega", Map.of(
                        "latitud", 4.72, "longitud", -74.06, "direccion", "Calle de prueba"),
                "medioPago", "EFECTIVO",
                "totalEsperado", 1);
        long ordersBefore = jdbc.queryForObject("SELECT count(*) FROM pedido", Long.class);
        long eventsBefore = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'pedidos.pedido-creado'", Long.class);

        var response = request("POST", "/pedidos", customer, orderRequest, key());

        assertEquals(409, response.statusCode(), response.body());
        assertEquals("CONFLICT", body(response).get("code"));
        assertEquals(ordersBefore, jdbc.queryForObject("SELECT count(*) FROM pedido", Long.class));
        assertEquals(eventsBefore, jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'pedidos.pedido-creado'", Long.class));
    }

    @Test
    void authorizationRestrictsManagementToTheOwnerAndCatalogToClients() throws Exception {
        String seller = account("VENDEDOR_AMBULANTE");
        String business = business(seller);
        String other = account("VENDEDOR_PUNTO_FIJO");
        business(other);
        String customer = account("CLIENTE");
        String id = body(request("POST", "/productos", seller, product("Papaya", "1"), key())).get("id").toString();
        assertEquals(403, request("POST", "/productos/" + id + "/inventario", other,
                Map.of("cantidadDisponible", 0), key()).statusCode());
        assertTrue(items(ok("GET", "/productos", other, null)).isEmpty());
        assertEquals(403, request("GET", "/productos", customer, null, null).statusCode());
        assertEquals(403, request("POST", "/productos", customer, product("X", "1"), key()).statusCode());
        assertEquals(403, request("GET", "/emprendimientos/" + business + "/productos", seller, null, null).statusCode());
        assertEquals(401, request("GET", "/productos", null, null, null).statusCode());
        assertEquals(1, items(ok("GET", "/productos", seller, null)).size());
    }

    @Test
    void validatesPriceQuantityNamesUnitsAndListQueriesWithoutRounding() throws Exception {
        String seller = account("VENDEDOR_AMBULANTE");
        business(seller);
        List<Map<String, Object>> invalid = new ArrayList<>();
        for (String quantity : List.of("0", "-1", "1.234", "100000000")) invalid.add(product("Mango", quantity));
        for (Object price : List.of(0, -1, new BigDecimal("3500.50"), new BigDecimal("9007199254740992"))) {
            var value = product("Mango", "1"); value.put("precio", price); invalid.add(value);
        }
        var wrongUnit = product("Mango", "1"); wrongUnit.put("unidad", "INVALIDA"); invalid.add(wrongUnit);
        invalid.add(product("  ", "1"));
        for (Map<String, Object> value : invalid) {
            var response = request("POST", "/productos", seller, value, key());
            assertEquals(400, response.statusCode(), response.body());
            assertEquals("VALIDATION_ERROR", body(response).get("code"));
        }
        assertEquals(400, request("POST", "/productos", seller, product("Mango", "1"), null).statusCode());
        for (String query : List.of("?size=101", "?page=-1", "?sort=invalid", "?estado=ELIMINADO", "?extra=x", "?page=1&page=2")) {
            assertEquals(400, request("GET", "/productos" + query, seller, null, null).statusCode(), query);
        }
        assertTrue(items(ok("GET", "/productos", seller, null)).isEmpty());
        String id = body(request("POST", "/productos", seller, product("Mango", "1"), key())).get("id").toString();
        assertEquals(400, request("POST", "/productos/" + id + "/inventario", seller,
                Map.of("cantidadDisponible", new BigDecimal("0.001")), key()).statusCode());
        assertEquals(400, request("POST", "/productos/" + id + "/inventario", seller,
                Map.of("cantidadDisponible", -1), key()).statusCode());
    }

    @Test
    void sellerWithoutBusinessGetsAnEmptyListAndCannotPublish() throws Exception {
        String seller = account("VENDEDOR_AMBULANTE");
        assertTrue(items(ok("GET", "/productos", seller, null)).isEmpty());
        assertNull(body(ok("GET", "/productos", seller, null)).get("emprendimiento"));
        var response = request("POST", "/productos", seller, product("Mango", "1"), key());
        assertEquals(422, response.statusCode(), response.body());
        assertEquals("BUSINESS_RULE", body(response).get("code"));
    }

    @Test
    void concurrentNamesAreUniquePerBusinessIgnoringCaseAndOuterWhitespace() throws Exception {
        String seller = account("VENDEDOR_AMBULANTE");
        String business = business(seller);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> {
                ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                return request("POST", "/productos", seller, product("  Mango ", "1"), key());
            });
            var b = workers.submit(() -> {
                ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                return request("POST", "/productos", seller, product("mango", "2"), key());
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            var responses = List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
            assertEquals(List.of(201, 409), responses.stream().map(HttpResponse::statusCode).sorted().toList());
            assertEquals("CONFLICT", body(responses.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow()).get("code"));
        } finally { start.countDown(); }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM producto WHERE emprendimiento_id = ?", Integer.class, UUID.fromString(business)));
        String other = account("VENDEDOR_PUNTO_FIJO"); business(other);
        assertEquals(201, request("POST", "/productos", other, product("Mango", "1"), key()).statusCode());
    }

    @Test
    void replaysCreateAndInventoryWithoutDuplicatingOrReapplyingAnOldQuantity() throws Exception {
        String seller = account("VENDEDOR_AMBULANTE"); business(seller);
        String createKey = key();
        var created = request("POST", "/productos", seller, product("Mango", "1"), createKey);
        var replay = request("POST", "/productos", seller, product("Mango", "1"), createKey);
        assertEquals(201, created.statusCode(), created.body());
        assertEquals(created.body(), replay.body());
        assertEquals(422, request("POST", "/productos", seller, product("Otro", "1"), createKey).statusCode());
        String id = body(created).get("id").toString();
        String inventoryKey = key();
        var first = request("POST", "/productos/" + id + "/inventario", seller, Map.of("cantidadDisponible", 5), inventoryKey);
        assertEquals(200, first.statusCode(), first.body());
        ok("POST", "/productos/" + id + "/inventario", seller, Map.of("cantidadDisponible", 2));
        assertEquals(first.body(), request("POST", "/productos/" + id + "/inventario", seller,
                Map.of("cantidadDisponible", 5), inventoryKey).body());
        assertEquals(2, ((Number) items(ok("GET", "/productos", seller, null)).getFirst().get("cantidadDisponible")).intValue());
    }

    @Test
    void paginatesWithAvailableFirstFiltersAndHidesDeletedProductsAndInactiveBusinesses() throws Exception {
        String seller = account("VENDEDOR_AMBULANTE"); String business = business(seller);
        String customer = account("CLIENTE");
        String exhausted = body(request("POST", "/productos", seller, product("A agotado", "1"), key())).get("id").toString();
        ok("POST", "/productos/" + exhausted + "/inventario", seller, Map.of("cantidadDisponible", 0));
        request("POST", "/productos", seller, product("Z disponible", "2"), key());
        String deleted = body(request("POST", "/productos", seller, product("B eliminado", "1"), key())).get("id").toString();
        jdbc.update("UPDATE producto SET estado = 'ELIMINADO' WHERE id = ?", UUID.fromString(deleted));
        var first = ok("GET", "/emprendimientos/" + business + "/productos?size=1", customer, null);
        assertEquals(2, ((Number) body(first).get("totalElements")).intValue());
        assertEquals("Z disponible", items(first).getFirst().get("nombre"));
        assertEquals(true, items(ok("GET", "/emprendimientos/" + business + "/productos?size=1&page=1", customer, null))
                .getFirst().get("soloReserva"));
        assertEquals(1, items(ok("GET", "/productos?estado=AGOTADO", seller, null)).size());
        assertEquals(409, request("POST", "/productos/" + deleted + "/inventario", seller, Map.of("cantidadDisponible", 1), key()).statusCode());
        jdbc.update("UPDATE emprendimiento SET estado = 'INACTIVO' WHERE id = ?", UUID.fromString(business));
        assertEquals(404, request("GET", "/emprendimientos/" + business + "/productos", customer, null, null).statusCode());
        assertTrue(items(ok("GET", "/productos", seller, null)).isEmpty());
        assertEquals(404, request("GET", "/emprendimientos/" + key() + "/productos", customer, null, null).statusCode());
    }

    private String account(String role) throws Exception {
        String phone = "+573" + ThreadLocalRandom.current().nextLong(100_000_000, 1_000_000_000);
        var registered = request("POST", "/auth/register", null, Map.of("nombre", "Producto Test", "celular", phone,
                "rol", role, "password", "test-password", "confirmPassword", "test-password"), key());
        assertEquals(201, registered.statusCode(), registered.body());
        var verified = request("POST", "/auth/verify-phone/confirm", null, Map.of("celular", phone, "codigo", "654321"), null);
        assertEquals(200, verified.statusCode(), verified.body());
        return body(verified).get("accessToken").toString();
    }

    private String business(String seller) throws Exception {
        var response = request("POST", "/emprendimientos", seller, Map.of("nombre", "Frutas Test", "categoria", "FRUTAS_VERDURAS",
                "descripcion", "Frutas frescas", "horaApertura", "06:00", "horaCierre", "18:00",
                "ubicacion", Map.of("latitud", 4.71, "longitud", -74.07, "direccion", "Calle de pruebas")), key());
        assertEquals(201, response.statusCode(), response.body());
        return body(response).get("id").toString();
    }

    private Map<String, Object> product(String name, String quantity) {
        return new HashMap<>(Map.of("nombre", name, "precio", 3500, "unidad", "KG",
                "descripcion", "Fruta fresca", "cantidadDisponible", new BigDecimal(quantity)));
    }

    private HttpResponse<String> ok(String method, String path, String token, Object value) throws Exception {
        var response = request(method, path, token, value, method.equals("GET") ? null : key());
        assertEquals(200, response.statusCode(), response.body()); return response;
    }

    private HttpResponse<String> request(String method, String path, String token, Object value, String idempotency) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .timeout(Duration.ofSeconds(15)).header("Origin", "http://localhost:8081")
                .header("Content-Type", "application/json").header("X-Device-Id", "product-test-device");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (idempotency != null) builder.header("Idempotency-Key", idempotency);
        builder.method(method, value == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(value)));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, Object> body(HttpResponse<String> response) {
        return json.readValue(response.body(), new TypeReference<>() {});
    }
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(HttpResponse<String> response) {
        return (List<Map<String, Object>>) body(response).get("content");
    }
    private String key() { return UUID.randomUUID().toString(); }
}
