package co.edu.napa.productos;

import co.edu.napa.productos.application.ports.in.ConsultarDisponibilidadPort;
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

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
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

    @BeforeEach
    void clearRedisState() throws Exception {
        assertEquals(0, redis.execInContainer("redis-cli", "FLUSHDB").getExitCode());
    }

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
                        "latitud", 4.72, "longitud", -74.06, "direccion", "Calle de prueba", "barrio", "Centro"));
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
                        "latitud", 4.72, "longitud", -74.06, "direccion", "Calle de prueba", "barrio", "Centro"),
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
                            "latitud", 4.72, "longitud", -74.06, "direccion", "Calle de prueba", "barrio", "Centro"),
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
                            "latitud", 4.72, "longitud", -74.06, "direccion", "Calle de prueba", "barrio", "Centro"),
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
    @SuppressWarnings("unchecked")
    void clientCanCreatePickupOrderWithoutDeliveryLocation() throws Exception {
        String seller = account("VENDEDOR_PUNTO_FIJO");
        String business = business(seller);
        ok("PUT", "/vendedores/me/configuracion-pago", seller, Map.of("aceptaEfectivo", true));
        String customer = account("CLIENTE");
        String productId = body(request("POST", "/productos", seller, product("Mango para recoger", "4"), key()))
                .get("id").toString();
        String secondProductId = body(request("POST", "/productos", seller,
                product("Banano para recoger", "4"), key())).get("id").toString();
        Map<String, Object> quoteRequest = Map.of(
                "emprendimientoId", business,
                "modalidad", "RECOGIDA",
                "items", List.of(
                        Map.of("productoId", productId, "cantidad", BigDecimal.ONE),
                        Map.of("productoId", secondProductId, "cantidad", BigDecimal.ONE)));

        var quote = request("POST", "/pedidos/cotizacion", customer, quoteRequest, null);

        assertEquals(200, quote.statusCode(), quote.body());
        assertEquals(List.of("RECOGIDA"), body(quote).get("modalidadesDisponibles"));
        assertEquals(List.of("EFECTIVO"), body(quote).get("mediosPagoDisponibles"));
        assertEquals(2, ((List<?>) body(quote).get("items")).size());
        assertEquals(7000, ((Number) body(quote).get("total")).longValue());
        assertNull(body(quote).get("tarifaDomicilio"));
        assertEquals(400, request("POST", "/pedidos/cotizacion", customer, Map.of(
                "emprendimientoId", business,
                "modalidad", "RECOGIDA",
                "items", List.of(Map.of("productoId", productId, "cantidad", BigDecimal.ONE)),
                "ubicacionEntrega", Map.of(
                        "latitud", 4.72, "longitud", -74.06, "direccion", "Calle de prueba", "barrio", "Centro")
        ), null).statusCode());

        Map<String, Object> orderRequest = new HashMap<>(quoteRequest);
        orderRequest.put("medioPago", "EFECTIVO");
        orderRequest.put("totalEsperado", 7000);
        var created = request("POST", "/pedidos", customer, orderRequest, key());

        assertEquals(201, created.statusCode(), created.body());
        UUID orderId = UUID.fromString(body(created).get("id").toString());
        assertEquals("PENDIENTE", body(created).get("estado"));
        assertNull(body(created).get("ubicacionEntrega"));
        assertNull(body(created).get("tarifaDomicilio"));
        assertEquals(7000, ((Number) body(created).get("total")).longValue());
        assertEquals(1L, jdbc.queryForObject(
                "SELECT count(*) FROM pedido WHERE id = ? AND modalidad = 'RECOGIDA' "
                        + "AND ubicacion_entrega_id IS NULL AND tarifa_domicilio IS NULL",
                Long.class, orderId));
        assertEquals(2L, jdbc.queryForObject(
                "SELECT count(*) FROM item_pedido WHERE pedido_id = ?", Long.class, orderId));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE aggregate_id = ? "
                        + "AND event_type = 'pedidos.pedido-creado'", Long.class, orderId));
    }

    @Test
    @SuppressWarnings("unchecked")
    void homeDeliveryRequiresBankTransferAndPersistsTheQuotedFeeAndLocation() throws Exception {
        String seller = account("VENDEDOR_PUNTO_FIJO");
        String business = business(seller);
        ok("PUT", "/vendedores/me/configuracion-pago", seller, Map.of(
                "aceptaEfectivo", true,
                "cuentaBancaria", Map.of(
                        "banco", "Banco de prueba",
                        "tipoCuenta", "AHORROS",
                        "numeroCuenta", "0000123456")));
        String customer = account("CLIENTE");
        String productId = body(request("POST", "/productos", seller, product("Mango a domicilio", "4"), key()))
                .get("id").toString();
        Map<String, Object> location = Map.of(
                "latitud", 4.72, "longitud", -74.06, "direccion", "Calle de prueba", "barrio", "Centro");
        Map<String, Object> quoteRequest = Map.of(
                "emprendimientoId", business,
                "modalidad", "DOMICILIO",
                "items", List.of(Map.of("productoId", productId, "cantidad", BigDecimal.ONE)),
                "ubicacionEntrega", location);

        var quote = request("POST", "/pedidos/cotizacion", customer, quoteRequest, null);

        assertEquals(200, quote.statusCode(), quote.body());
        Map<String, Object> quoted = body(quote);
        assertEquals(List.of("RECOGIDA", "DOMICILIO"), quoted.get("modalidadesDisponibles"));
        assertEquals(List.of("CUENTA_BANCARIA"), quoted.get("mediosPagoDisponibles"));
        long deliveryFee = ((Number) quoted.get("tarifaDomicilio")).longValue();
        assertTrue(deliveryFee >= 3050);
        assertEquals(3500 + deliveryFee, ((Number) quoted.get("total")).longValue());
        Map<String, Object> deliveryPayment = (Map<String, Object>) quoted.get("pagoDomicilio");
        assertEquals("CUENTA_BANCARIA", deliveryPayment.get("medio"));
        assertEquals("AL_SER_ACEPTADO", deliveryPayment.get("momento"));
        assertEquals(quoted.get("total"), deliveryPayment.get("total"));

        Map<String, Object> orderRequest = new HashMap<>(quoteRequest);
        orderRequest.put("medioPago", "EFECTIVO");
        orderRequest.put("totalEsperado", quoted.get("total"));
        long ordersBefore = jdbc.queryForObject("SELECT count(*) FROM pedido", Long.class);
        var cashAttempt = request("POST", "/pedidos", customer, orderRequest, key());
        assertEquals(422, cashAttempt.statusCode(), cashAttempt.body());
        assertEquals("BUSINESS_RULE", body(cashAttempt).get("code"));
        assertEquals(ordersBefore, jdbc.queryForObject("SELECT count(*) FROM pedido", Long.class));

        orderRequest.put("medioPago", "CUENTA_BANCARIA");
        var created = request("POST", "/pedidos", customer, orderRequest, key());

        assertEquals(201, created.statusCode(), created.body());
        UUID orderId = UUID.fromString(body(created).get("id").toString());
        UUID locationId = jdbc.queryForObject(
                "SELECT ubicacion_entrega_id FROM pedido WHERE id = ?", UUID.class, orderId);
        assertEquals(deliveryFee, jdbc.queryForObject(
                "SELECT tarifa_domicilio FROM pedido WHERE id = ?", Long.class, orderId));
        assertEquals(3500 + deliveryFee, jdbc.queryForObject(
                "SELECT total FROM pedido WHERE id = ?", Long.class, orderId));
        assertEquals("CUENTA_BANCARIA", jdbc.queryForObject(
                "SELECT medio_pago FROM pedido WHERE id = ?", String.class, orderId));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM ubicacion WHERE id = ?", Long.class, locationId));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE aggregate_id = ? "
                        + "AND event_type = 'pedidos.pedido-creado'", Long.class, orderId));
    }

    @Test
    void reservationCanIncludeAnExhaustedProductWithoutChangingItsInventory() throws Exception {
        String seller = account("VENDEDOR_PUNTO_FIJO");
        String business = business(seller);
        ok("PUT", "/vendedores/me/configuracion-pago", seller, Map.of("aceptaEfectivo", true));
        String customer = account("CLIENTE");
        String productId = body(request("POST", "/productos", seller, product("Mango por reservar", "3"), key()))
                .get("id").toString();
        ok("POST", "/productos/" + productId + "/inventario", seller, Map.of("cantidadDisponible", 0));
        String reservation = java.time.Instant.now().plusSeconds(3600)
                .truncatedTo(ChronoUnit.MICROS).toString();
        Map<String, Object> orderRequest = Map.of(
                "emprendimientoId", business,
                "modalidad", "RECOGIDA",
                "items", List.of(Map.of("productoId", productId, "cantidad", new BigDecimal("2"))),
                "reserva", reservation,
                "medioPago", "EFECTIVO",
                "totalEsperado", 7000);

        var created = request("POST", "/pedidos", customer, orderRequest, key());

        assertEquals(201, created.statusCode(), created.body());
        UUID orderId = UUID.fromString(body(created).get("id").toString());
        assertEquals("PENDIENTE", body(created).get("estado"));
        assertNotNull(body(created).get("reserva"));
        assertEquals(7000, ((Number) body(created).get("total")).longValue());
        assertEquals(0, BigDecimal.ZERO.compareTo(jdbc.queryForObject(
                "SELECT cantidad_disponible FROM producto WHERE id = ?",
                BigDecimal.class, UUID.fromString(productId))));
        assertEquals(reservation, jdbc.queryForObject(
                "SELECT reserva_at FROM pedido WHERE id = ?", (row, index) ->
                        row.getTimestamp(1).toInstant().toString(), orderId));
    }

    @Test
    void outboxFailureRollsBackOrderItemsAndDeliveryLocation() throws Exception {
        String seller = account("VENDEDOR_AMBULANTE");
        String business = business(seller);
        ok("PUT", "/vendedores/me/configuracion-pago", seller, Map.of("aceptaEfectivo", true));
        String customer = account("CLIENTE");
        String productId = body(request("POST", "/productos", seller, product("Mango rollback", "2"), key()))
                .get("id").toString();
        Map<String, Object> orderRequest = Map.of(
                "emprendimientoId", business,
                "modalidad", "ENTREGA_DIRECTA",
                "items", List.of(Map.of("productoId", productId, "cantidad", BigDecimal.ONE)),
                "ubicacionEntrega", Map.of(
                        "latitud", 4.72, "longitud", -74.06, "direccion", "Calle de prueba", "barrio", "Centro"),
                "medioPago", "EFECTIVO");
        long ordersBefore = jdbc.queryForObject("SELECT count(*) FROM pedido", Long.class);
        long itemsBefore = jdbc.queryForObject("SELECT count(*) FROM item_pedido", Long.class);
        long locationsBefore = jdbc.queryForObject("SELECT count(*) FROM ubicacion", Long.class);
        long eventsBefore = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'pedidos.pedido-creado'", Long.class);
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION test_reject_order_event() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.event_type = 'pedidos.pedido-creado' THEN
                        RAISE EXCEPTION 'test rejects order event';
                    END IF;
                    RETURN NEW;
                END
                $$
                """);
        jdbc.execute("""
                CREATE TRIGGER test_reject_order_event
                BEFORE INSERT ON outbox_event
                FOR EACH ROW EXECUTE FUNCTION test_reject_order_event()
                """);

        HttpResponse<String> response;
        try {
            response = request("POST", "/pedidos", customer, orderRequest, key());
        } finally {
            jdbc.execute("DROP TRIGGER test_reject_order_event ON outbox_event");
            jdbc.execute("DROP FUNCTION test_reject_order_event()");
        }

        assertEquals(500, response.statusCode(), response.body());
        assertEquals(ordersBefore, jdbc.queryForObject("SELECT count(*) FROM pedido", Long.class));
        assertEquals(itemsBefore, jdbc.queryForObject("SELECT count(*) FROM item_pedido", Long.class));
        assertEquals(locationsBefore, jdbc.queryForObject("SELECT count(*) FROM ubicacion", Long.class));
        assertEquals(eventsBefore, jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'pedidos.pedido-creado'", Long.class));
    }

    @Test
    void concurrentOrderCreationAndProductDeletionCannotLeaveAnOpenOrderForDeletedProduct() throws Exception {
        String seller = account("VENDEDOR_AMBULANTE");
        String business = business(seller);
        ok("PUT", "/vendedores/me/configuracion-pago", seller, Map.of("aceptaEfectivo", true));
        String customer = account("CLIENTE");
        String productId = body(request("POST", "/productos", seller, product("Mango concurrente", "2"), key()))
                .get("id").toString();
        Map<String, Object> orderRequest = Map.of(
                "emprendimientoId", business,
                "modalidad", "ENTREGA_DIRECTA",
                "items", List.of(Map.of("productoId", productId, "cantidad", BigDecimal.ONE)),
                "ubicacionEntrega", Map.of(
                        "latitud", 4.72, "longitud", -74.06, "direccion", "Calle de prueba", "barrio", "Centro"),
                "medioPago", "EFECTIVO");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        HttpResponse<String> deletion;
        HttpResponse<String> order;
        try (var workers = Executors.newFixedThreadPool(2)) {
            var deleteTask = workers.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return request("POST", "/productos/" + productId + "/eliminar", seller,
                        Map.of("confirmar", true), key());
            });
            var createTask = workers.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return request("POST", "/pedidos", customer, orderRequest, key());
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            deletion = deleteTask.get(30, TimeUnit.SECONDS);
            order = createTask.get(30, TimeUnit.SECONDS);
        } finally {
            start.countDown();
        }

        boolean deletionWon = deletion.statusCode() == 204 && order.statusCode() == 404;
        boolean orderWon = deletion.statusCode() == 409 && order.statusCode() == 201;
        assertTrue(deletionWon || orderWon,
                "Unexpected race result: delete=" + deletion.statusCode() + " " + deletion.body()
                        + ", create=" + order.statusCode() + " " + order.body());
        if (deletionWon) {
            assertEquals("ELIMINADO", jdbc.queryForObject(
                    "SELECT estado FROM producto WHERE id = ?", String.class, UUID.fromString(productId)));
            assertEquals(0L, jdbc.queryForObject("""
                    SELECT count(*) FROM item_pedido item
                    JOIN pedido ON pedido.id = item.pedido_id
                    WHERE item.producto_id = ? AND pedido.estado IN
                        ('PENDIENTE', 'ACEPTADO', 'LISTO_PARA_RECOGER', 'EN_CAMINO')
                    """, Long.class, UUID.fromString(productId)));
        } else {
            assertEquals("DISPONIBLE", jdbc.queryForObject(
                    "SELECT estado FROM producto WHERE id = ?", String.class, UUID.fromString(productId)));
            assertEquals(1L, jdbc.queryForObject("""
                    SELECT count(*) FROM item_pedido item
                    JOIN pedido ON pedido.id = item.pedido_id
                    WHERE item.producto_id = ? AND pedido.estado = 'PENDIENTE'
                    """, Long.class, UUID.fromString(productId)));
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
                        "latitud", 4.72, "longitud", -74.06, "direccion", "Calle de prueba", "barrio", "Centro"),
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
    @SuppressWarnings("unchecked")
    void sellerAcceptsDirectOrderDeductsStockCreatesPendingPaymentAndPublishesE02() throws Exception {
        String seller = account("VENDEDOR_AMBULANTE");
        String business = business(seller);
        configurePayment(seller, true, false);
        String customer = account("CLIENTE");
        String productId = body(request("POST", "/productos", seller, product("Mango aceptar", "5"), key()))
                .get("id").toString();
        String orderId = createOrder(customer, business, productId, "ENTREGA_DIRECTA",
                BigDecimal.valueOf(2), "EFECTIVO", null, 7000L);

        var response = request("POST", "/pedidos/" + orderId + "/aceptar", seller, null, key());

        assertEquals(200, response.statusCode(), response.body());
        Map<String, Object> accepted = body(response);
        assertEquals("ACEPTADO", accepted.get("estado"));
        assertEquals("ENTREGA_DIRECTA", accepted.get("modalidad"));
        Map<String, Object> payment = (Map<String, Object>) accepted.get("pago");
        assertEquals("PENDIENTE_CONFIRMACION", payment.get("estado"));
        assertEquals("EFECTIVO", payment.get("medio"));
        assertEquals(7000, ((Number) payment.get("monto")).longValue());
        assertEquals(7000L, jdbc.queryForObject(
                "SELECT monto FROM pago WHERE pedido_id = ?", Long.class, UUID.fromString(orderId)));
        assertEquals("PEDIDO", jdbc.queryForObject(
                "SELECT tipo FROM pago WHERE pedido_id = ?", String.class, UUID.fromString(orderId)));
        assertEquals(0, new BigDecimal("3").compareTo(jdbc.queryForObject(
                "SELECT cantidad_disponible FROM producto WHERE id = ?", BigDecimal.class, UUID.fromString(productId))));
        assertEquals(1L, eventCount("pedidos.pedido-aceptado", orderId));
        assertEquals("false", jdbc.queryForObject(
                "SELECT payload ->> 'requierePagoDigital' FROM outbox_event "
                        + "WHERE event_type = 'pedidos.pedido-aceptado' AND payload ->> 'pedidoId' = ?",
                String.class, orderId));
    }

    @Test
    void sellerAcceptsReservationWithoutValidatingOrDeductingExhaustedStock() throws Exception {
        String seller = account("VENDEDOR_PUNTO_FIJO");
        String business = business(seller);
        configurePayment(seller, true, false);
        String customer = account("CLIENTE");
        String productId = body(request("POST", "/productos", seller, product("Papaya reserva", "1"), key()))
                .get("id").toString();
        ok("POST", "/productos/" + productId + "/inventario", seller, Map.of("cantidadDisponible", 0));
        String reservation = java.time.Instant.now().plusSeconds(3600).toString();
        String orderId = createOrder(customer, business, productId, "RECOGIDA",
                BigDecimal.ONE, "EFECTIVO", reservation, 3500L);

        var response = request("POST", "/pedidos/" + orderId + "/aceptar", seller, null, key());

        assertEquals(200, response.statusCode(), response.body());
        assertEquals("ACEPTADO", body(response).get("estado"));
        assertEquals(0, BigDecimal.ZERO.compareTo(jdbc.queryForObject(
                "SELECT cantidad_disponible FROM producto WHERE id = ?", BigDecimal.class, UUID.fromString(productId))));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT count(*) FROM pago WHERE pedido_id = ?", Long.class, UUID.fromString(orderId)));
    }

    @Test
    void sellerAcceptsHomeOrderCreatesDigitalPaymentAndPublishesDigitalPaymentRequirement() throws Exception {
        String seller = account("VENDEDOR_PUNTO_FIJO");
        String business = business(seller);
        configurePayment(seller, true, true);
        String customer = account("CLIENTE");
        String productId = body(request("POST", "/productos", seller, product("Mango domicilio aceptado", "2"), key()))
                .get("id").toString();
        String orderId = createOrder(customer, business, productId, "DOMICILIO",
                BigDecimal.ONE, "CUENTA_BANCARIA", null, null);
        long orderTotal = jdbc.queryForObject(
                "SELECT total FROM pedido WHERE id = ?", Long.class, UUID.fromString(orderId));

        var response = request("POST", "/pedidos/" + orderId + "/aceptar", seller, null, key());

        assertEquals(200, response.statusCode(), response.body());
        assertEquals("ACEPTADO", body(response).get("estado"));
        assertEquals(orderTotal, jdbc.queryForObject(
                "SELECT monto FROM pago WHERE pedido_id = ?", Long.class, UUID.fromString(orderId)));
        assertEquals("CUENTA_BANCARIA", jdbc.queryForObject(
                "SELECT medio FROM pago WHERE pedido_id = ?", String.class, UUID.fromString(orderId)));
        assertEquals("true", jdbc.queryForObject(
                "SELECT payload ->> 'requierePagoDigital' FROM outbox_event "
                        + "WHERE event_type = 'pedidos.pedido-aceptado' AND aggregate_id = ?",
                String.class, UUID.fromString(orderId)));
    }

    @Test
    void sellerRejectionLeavesInventoryAndPaymentUntouchedAndPublishesE03() throws Exception {
        String seller = account("VENDEDOR_PUNTO_FIJO");
        String business = business(seller);
        configurePayment(seller, true, false);
        String customer = account("CLIENTE");
        String productId = body(request("POST", "/productos", seller, product("Banano rechazar", "4"), key()))
                .get("id").toString();
        String orderId = createOrder(customer, business, productId, "RECOGIDA",
                BigDecimal.ONE, "EFECTIVO", null, 3500L);

        var response = request("POST", "/pedidos/" + orderId + "/rechazar", seller, null, key());

        assertEquals(200, response.statusCode(), response.body());
        assertEquals("RECHAZADO", body(response).get("estado"));
        assertEquals(0, BigDecimal.valueOf(4).compareTo(jdbc.queryForObject(
                "SELECT cantidad_disponible FROM producto WHERE id = ?", BigDecimal.class, UUID.fromString(productId))));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM pago WHERE pedido_id = ?", Long.class, UUID.fromString(orderId)));
        assertEquals(1L, eventCount("pedidos.pedido-rechazado", orderId));
    }

    @Test
    void sellerCanRejectReservationButCannotAnswerAnotherSellersOrAlreadyAcceptedOrder() throws Exception {
        String seller = account("VENDEDOR_PUNTO_FIJO");
        String business = business(seller);
        configurePayment(seller, true, false);
        String otherSeller = account("VENDEDOR_AMBULANTE");
        business(otherSeller);
        configurePayment(otherSeller, true, false);
        String customer = account("CLIENTE");
        String productId = body(request("POST", "/productos", seller, product("Banano de reserva", "3"), key()))
                .get("id").toString();
        String reservation = java.time.Instant.now().plusSeconds(3600)
                .truncatedTo(ChronoUnit.MICROS).toString();
        String reservationOrder = createOrder(customer, business, productId, "RECOGIDA",
                BigDecimal.ONE, "EFECTIVO", reservation, 3500L);

        var rejectedReservation = request(
                "POST", "/pedidos/" + reservationOrder + "/rechazar", seller, null, key());

        assertEquals(200, rejectedReservation.statusCode(), rejectedReservation.body());
        assertEquals("RECHAZADO", body(rejectedReservation).get("estado"));
        assertEquals(1L, eventCount("pedidos.pedido-rechazado", reservationOrder));

        String immediateOrder = createOrder(customer, business, productId, "RECOGIDA",
                BigDecimal.ONE, "EFECTIVO", null, 3500L);
        assertEquals(403, request(
                "POST", "/pedidos/" + immediateOrder + "/aceptar", customer, null, key()).statusCode());
        var foreignResponse = request(
                "POST", "/pedidos/" + immediateOrder + "/rechazar", otherSeller, null, key());
        assertEquals(404, foreignResponse.statusCode(), foreignResponse.body());
        assertEquals("NOT_FOUND", body(foreignResponse).get("code"));

        var accepted = request("POST", "/pedidos/" + immediateOrder + "/aceptar", seller, null, key());
        assertEquals(200, accepted.statusCode(), accepted.body());
        var rejectAccepted = request(
                "POST", "/pedidos/" + immediateOrder + "/rechazar", seller, null, key());
        assertEquals(409, rejectAccepted.statusCode(), rejectAccepted.body());
        assertEquals("INVALID_STATE", body(rejectAccepted).get("code"));
        assertEquals(0L, eventCount("pedidos.pedido-rechazado", immediateOrder));
    }

    @Test
    void insufficientStockAtAcceptanceKeepsOrderPendingAndDoesNotCreatePaymentOrEvent() throws Exception {
        String seller = account("VENDEDOR_AMBULANTE");
        String business = business(seller);
        configurePayment(seller, true, false);
        String customer = account("CLIENTE");
        String productId = body(request("POST", "/productos", seller, product("Mango escaso", "3"), key()))
                .get("id").toString();
        String orderId = createOrder(customer, business, productId, "ENTREGA_DIRECTA",
                BigDecimal.valueOf(2), "EFECTIVO", null, 7000L);
        jdbc.update("UPDATE producto SET cantidad_disponible = 1 WHERE id = ?", UUID.fromString(productId));

        var response = request("POST", "/pedidos/" + orderId + "/aceptar", seller, null, key());

        assertEquals(409, response.statusCode(), response.body());
        assertEquals("OUT_OF_STOCK", body(response).get("code"));
        assertEquals("PENDIENTE", jdbc.queryForObject(
                "SELECT estado FROM pedido WHERE id = ?", String.class, UUID.fromString(orderId)));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM pago WHERE pedido_id = ?", Long.class, UUID.fromString(orderId)));
        assertEquals(0L, eventCount("pedidos.pedido-aceptado", orderId));
        assertEquals(0, BigDecimal.ONE.compareTo(jdbc.queryForObject(
                "SELECT cantidad_disponible FROM producto WHERE id = ?", BigDecimal.class, UUID.fromString(productId))));
    }

    @Test
    void homeOrderCannotBeAcceptedAfterSellerRemovesBankAccount() throws Exception {
        String seller = account("VENDEDOR_PUNTO_FIJO");
        String business = business(seller);
        configurePayment(seller, true, true);
        String customer = account("CLIENTE");
        String productId = body(request("POST", "/productos", seller, product("Mango domicilio", "3"), key()))
                .get("id").toString();
        String orderId = createOrder(customer, business, productId, "DOMICILIO",
                BigDecimal.ONE, "CUENTA_BANCARIA", null, null);
        configurePayment(seller, true, false);

        var response = request("POST", "/pedidos/" + orderId + "/aceptar", seller, null, key());

        assertEquals(409, response.statusCode(), response.body());
        assertEquals("BANK_ACCOUNT_REQUIRED", body(response).get("code"));
        assertEquals("PENDIENTE", jdbc.queryForObject(
                "SELECT estado FROM pedido WHERE id = ?", String.class, UUID.fromString(orderId)));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM pago WHERE pedido_id = ?", Long.class, UUID.fromString(orderId)));
    }

    @Test
    void concurrentAcceptancesForTheLastUnitAcceptOnlyOneOrder() throws Exception {
        String seller = account("VENDEDOR_AMBULANTE");
        String business = business(seller);
        configurePayment(seller, true, false);
        String customer = account("CLIENTE");
        String productId = body(request("POST", "/productos", seller, product("Última unidad", "1"), key()))
                .get("id").toString();
        String firstOrder = createOrder(customer, business, productId, "ENTREGA_DIRECTA",
                BigDecimal.ONE, "EFECTIVO", null, 3500L);
        String secondOrder = createOrder(customer, business, productId, "ENTREGA_DIRECTA",
                BigDecimal.ONE, "EFECTIVO", null, 3500L);
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var first = pool.submit(() -> {
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS));
            return request("POST", "/pedidos/" + firstOrder + "/aceptar", seller, null, key());
        });
        var second = pool.submit(() -> {
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS));
            return request("POST", "/pedidos/" + secondOrder + "/aceptar", seller, null, key());
        });
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        var firstResponse = first.get(15, TimeUnit.SECONDS);
        var secondResponse = second.get(15, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertEquals(1, List.of(firstResponse.statusCode(), secondResponse.statusCode()).stream()
                .filter(status -> status == 200).count());
        assertEquals(1, List.of(firstResponse.statusCode(), secondResponse.statusCode()).stream()
                .filter(status -> status == 409).count());
        assertEquals(1L, jdbc.queryForObject(
                "SELECT count(*) FROM pago WHERE pedido_id IN (?, ?)", Long.class,
                UUID.fromString(firstOrder), UUID.fromString(secondOrder)));
        assertEquals(0, BigDecimal.ZERO.compareTo(jdbc.queryForObject(
                "SELECT cantidad_disponible FROM producto WHERE id = ?", BigDecimal.class, UUID.fromString(productId))));
    }

    @Test
    void concurrentResponsesToTheSameOrderAcceptAndChargeOnlyOnce() throws Exception {
        String seller = account("VENDEDOR_AMBULANTE");
        String business = business(seller);
        configurePayment(seller, true, false);
        String customer = account("CLIENTE");
        String productId = body(request("POST", "/productos", seller, product("Mango misma orden", "2"), key()))
                .get("id").toString();
        String orderId = createOrder(customer, business, productId, "ENTREGA_DIRECTA",
                BigDecimal.ONE, "EFECTIVO", null, 3500L);
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return request("POST", "/pedidos/" + orderId + "/aceptar", seller, null, key());
            });
            var second = pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return request("POST", "/pedidos/" + orderId + "/aceptar", seller, null, key());
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            var responses = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertEquals(1, responses.stream().filter(response -> response.statusCode() == 200).count());
            assertEquals(1, responses.stream().filter(response -> response.statusCode() == 409).count());
        } finally {
            start.countDown();
            pool.shutdownNow();
        }

        assertEquals(1L, jdbc.queryForObject(
                "SELECT count(*) FROM pago WHERE pedido_id = ?", Long.class, UUID.fromString(orderId)));
        assertEquals(0, BigDecimal.ONE.compareTo(jdbc.queryForObject(
                "SELECT cantidad_disponible FROM producto WHERE id = ?", BigDecimal.class, UUID.fromString(productId))));
    }

    @Test
    void paymentFailureRollsBackOrderAcceptanceInventoryAndEvent() throws Exception {
        String seller = account("VENDEDOR_AMBULANTE");
        String business = business(seller);
        configurePayment(seller, true, false);
        String customer = account("CLIENTE");
        String productId = body(request("POST", "/productos", seller, product("Mango pago", "3"), key()))
                .get("id").toString();
        String orderId = createOrder(customer, business, productId, "ENTREGA_DIRECTA",
                BigDecimal.ONE, "EFECTIVO", null, 3500L);
        jdbc.execute("""
                CREATE FUNCTION test_reject_pedido_payment() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'payment persistence unavailable'; END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER test_reject_pedido_payment
                BEFORE INSERT ON pago FOR EACH ROW EXECUTE FUNCTION test_reject_pedido_payment()
                """);
        try {
            var response = request("POST", "/pedidos/" + orderId + "/aceptar", seller, null, key());

            assertEquals(500, response.statusCode(), response.body());
            assertEquals("PENDIENTE", jdbc.queryForObject(
                    "SELECT estado FROM pedido WHERE id = ?", String.class, UUID.fromString(orderId)));
            assertEquals(0L, eventCount("pedidos.pedido-aceptado", orderId));
            assertEquals(0, BigDecimal.valueOf(3).compareTo(jdbc.queryForObject(
                    "SELECT cantidad_disponible FROM producto WHERE id = ?", BigDecimal.class,
                    UUID.fromString(productId))));
        } finally {
            jdbc.execute("DROP TRIGGER test_reject_pedido_payment ON pago");
            jdbc.execute("DROP FUNCTION test_reject_pedido_payment()");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void sellerListsOnlyOwnOrdersWithReservationAndDateFiltersAndPagination() throws Exception {
        String seller = account("VENDEDOR_PUNTO_FIJO");
        String business = business(seller);
        configurePayment(seller, true, false);
        String otherSeller = account("VENDEDOR_AMBULANTE");
        String otherBusiness = business(otherSeller);
        configurePayment(otherSeller, true, false);
        String customer = account("CLIENTE");
        String productId = body(request("POST", "/productos", seller, product("Mango listado", "4"), key()))
                .get("id").toString();
        String firstOrder = createOrder(customer, business, productId, "RECOGIDA",
                BigDecimal.ONE, "EFECTIVO", null, 3500L);
        String reservation = java.time.Instant.now().plusSeconds(3600)
                .truncatedTo(ChronoUnit.MICROS).toString();
        String secondOrder = createOrder(customer, business, productId, "RECOGIDA",
                BigDecimal.ONE, "EFECTIVO", reservation, 3500L);
        String foreignProductId = body(request("POST", "/productos", otherSeller,
                product("Mango ajeno", "1"), key())).get("id").toString();
        createOrder(customer, otherBusiness, foreignProductId, "ENTREGA_DIRECTA",
                BigDecimal.ONE, "EFECTIVO", null, 3500L);

        var response = ok("GET", "/pedidos", seller, null);
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(null));
        List<Map<String, Object>> content = (List<Map<String, Object>>) body(response).get("content");

        assertEquals(2, content.size());
        assertEquals(2L, ((Number) body(response).get("totalElements")).longValue());
        assertTrue(content.stream().allMatch(item -> item.containsKey("cliente")
                && item.containsKey("createdAt") && item.containsKey("estado")
                && item.containsKey("modalidad") && item.containsKey("reserva")
                && item.containsKey("total")));
        assertTrue(content.stream().allMatch(item ->
                "Producto Test".equals(((Map<String, Object>) item.get("cliente")).get("nombre"))));
        List<Map<String, Object>> reservationItems = (List<Map<String, Object>>) body(ok(
                "GET", "/pedidos?modalidad=RECOGIDA&reserva=true&estado=PENDIENTE", seller, null)).get("content");
        assertEquals(1, reservationItems.size());
        assertEquals(secondOrder, reservationItems.getFirst().get("id"));
        assertEquals(java.time.Instant.parse(reservation),
                java.time.Instant.parse(reservationItems.getFirst().get("reserva").toString()));
        List<Map<String, Object>> immediateItems = (List<Map<String, Object>>) body(ok(
                "GET", "/pedidos?reserva=false", seller, null)).get("content");
        assertEquals(1, immediateItems.size());
        assertEquals(firstOrder, immediateItems.getFirst().get("id"));

        String today = java.time.LocalDate.now(java.time.ZoneId.of("America/Bogota")).toString();
        assertEquals(2L, ((Number) body(ok(
                "GET", "/pedidos?desde=" + today + "&hasta=" + today, seller, null)).get("totalElements")).longValue());
        jdbc.update("UPDATE pedido SET created_at = ? WHERE id IN (?, ?)",
                java.sql.Timestamp.from(java.time.Instant.now().truncatedTo(ChronoUnit.SECONDS)),
                UUID.fromString(firstOrder), UUID.fromString(secondOrder));
        List<String> tiedIds = ((List<Map<String, Object>>) body(ok("GET", "/pedidos", seller, null))
                .get("content")).stream().map(item -> item.get("id").toString()).toList();
        assertEquals(List.of(firstOrder, secondOrder).stream().sorted().toList(), tiedIds);
        Map<String, Object> page = body(ok("GET", "/pedidos?page=0&size=1", seller, null));
        assertEquals(1, ((List<?>) page.get("content")).size());
        assertEquals(2, ((Number) page.get("totalElements")).longValue());
        assertEquals(403, request("GET", "/pedidos", customer, null, null).statusCode());
        for (String invalidQuery : List.of(
                "?unknown=value", "?estado=NO_EXISTE", "?modalidad=OTRA", "?reserva=yes",
                "?page=bad", "?size=101", "?desde=2026-02-02&hasta=2026-02-01",
                "?sort=total,desc", "?estado=PENDIENTE&estado=ACEPTADO")) {
            assertEquals(400, request("GET", "/pedidos" + invalidQuery, seller, null, null).statusCode(),
                    invalidQuery);
        }

        String emptySeller = account("VENDEDOR_AMBULANTE");
        business(emptySeller);
        assertTrue(((List<?>) body(ok("GET", "/pedidos", emptySeller, null)).get("content")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sellerReadsConsistentOrderDetailsWithPaymentPrivacyAndOwnership() throws Exception {
        String seller = account("VENDEDOR_AMBULANTE");
        String business = business(seller);
        configurePayment(seller, true, false);
        String customer = account("CLIENTE");
        String productId = body(request("POST", "/productos", seller, product("Mango detalle", "4"), key()))
                .get("id").toString();
        String acceptedOrder = createOrder(customer, business, productId, "ENTREGA_DIRECTA",
                BigDecimal.ONE, "EFECTIVO", null, 3500L);
        assertEquals(200, request("POST", "/pedidos/" + acceptedOrder + "/aceptar", seller, null, key()).statusCode());
        jdbc.update("UPDATE producto SET precio = 9999, estado = 'ELIMINADO' WHERE id = ?",
                UUID.fromString(productId));

        var acceptedResponse = ok("GET", "/pedidos/" + acceptedOrder, seller, null);
        assertEquals("no-store", acceptedResponse.headers().firstValue("Cache-Control").orElse(null));
        Map<String, Object> accepted = body(acceptedResponse);
        assertEquals("ACEPTADO", accepted.get("estado"));
        assertEquals("ENTREGA_DIRECTA", accepted.get("modalidad"));
        assertEquals("PENDIENTE_CONFIRMACION", ((Map<String, Object>) accepted.get("pago")).get("estado"));
        assertEquals("EFECTIVO", accepted.get("medioPago"));
        assertEquals(1, ((Map<String, Object>) accepted.get("cliente")).size());
        assertEquals("Producto Test", ((Map<String, Object>) accepted.get("cliente")).get("nombre"));
        assertEquals(3500L, ((Number) accepted.get("subtotal")).longValue());
        assertEquals(3500L, ((Number) accepted.get("total")).longValue());
        assertNotNull(accepted.get("ubicacionEntrega"));
        Map<String, Object> item = ((List<Map<String, Object>>) accepted.get("items")).getFirst();
        assertEquals("Mango detalle", item.get("nombre"));
        assertEquals(3500L, ((Number) item.get("precioUnitario")).longValue());
        assertEquals(true, item.get("productoEliminado"));

        String pickupSeller = account("VENDEDOR_PUNTO_FIJO");
        String pickupBusiness = business(pickupSeller);
        configurePayment(pickupSeller, true, false);
        String pickupProduct = body(request(
                "POST", "/productos", pickupSeller, product("Papaya detalle", "2"), key()))
                .get("id").toString();
        String pendingPickup = createOrder(customer, pickupBusiness, pickupProduct, "RECOGIDA",
                BigDecimal.ONE, "EFECTIVO", null, 3500L);
        Map<String, Object> pickup = body(ok("GET", "/pedidos/" + pendingPickup, pickupSeller, null));
        assertNull(pickup.get("pago"));
        assertNull(pickup.get("ubicacionEntrega"));
        assertEquals("RECOGIDA", pickup.get("modalidad"));

        String homeSeller = account("VENDEDOR_PUNTO_FIJO");
        String homeBusiness = business(homeSeller);
        configurePayment(homeSeller, true, true);
        String homeProduct = body(request(
                "POST", "/productos", homeSeller, product("Pera domicilio", "2"), key()))
                .get("id").toString();
        String homeOrder = createOrder(customer, homeBusiness, homeProduct, "DOMICILIO",
                BigDecimal.ONE, "CUENTA_BANCARIA", null, null);
        Map<String, Object> home = body(ok("GET", "/pedidos/" + homeOrder, homeSeller, null));
        assertEquals("DOMICILIO", home.get("modalidad"));
        assertEquals("CUENTA_BANCARIA", home.get("medioPago"));
        assertNotNull(home.get("ubicacionEntrega"));
        assertNull(home.get("pago"));

        String rejectedProduct = body(request("POST", "/productos", seller, product("Guayaba detalle", "1"), key()))
                .get("id").toString();
        String rejectedOrder = createOrder(customer, business, rejectedProduct, "ENTREGA_DIRECTA",
                BigDecimal.ONE, "EFECTIVO", null, 3500L);
        assertEquals(200, request("POST", "/pedidos/" + rejectedOrder + "/rechazar", seller, null, key()).statusCode());
        Map<String, Object> rejected = body(ok("GET", "/pedidos/" + rejectedOrder, seller, null));
        assertEquals("RECHAZADO", rejected.get("estado"));
        assertNull(rejected.get("ubicacionEntrega"));

        jdbc.update("UPDATE pedido SET estado = 'CANCELADO' WHERE id = ?", UUID.fromString(acceptedOrder));
        Map<String, Object> cancelled = body(ok("GET", "/pedidos/" + acceptedOrder, seller, null));
        assertEquals("CANCELADO", cancelled.get("estado"));
        assertNull(cancelled.get("ubicacionEntrega"));

        String otherSeller = account("VENDEDOR_PUNTO_FIJO");
        business(otherSeller);
        assertEquals(404, request("GET", "/pedidos/" + acceptedOrder, otherSeller, null, null).statusCode());
        assertEquals(404, request("GET", "/pedidos/" + UUID.randomUUID(), seller, null, null).statusCode());
        assertEquals(403, request("GET", "/pedidos/" + acceptedOrder, customer, null, null).statusCode());
        assertEquals(400, request("GET", "/pedidos/" + acceptedOrder + "?extra=x", seller, null, null).statusCode());
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

    private void configurePayment(String seller, boolean acceptsCash, boolean hasBankAccount) throws Exception {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("aceptaEfectivo", acceptsCash);
        configuration.put("cuentaBancaria", hasBankAccount ? Map.of(
                "banco", "Banco de prueba",
                "tipoCuenta", "AHORROS",
                "numeroCuenta", "0000123456") : null);
        ok("PUT", "/vendedores/me/configuracion-pago", seller, configuration);
    }

    private String createOrder(
            String customer,
            String business,
            String productId,
            String modality,
            BigDecimal quantity,
            String paymentMethod,
            String reservation,
            Long expectedTotal
    ) throws Exception {
        Map<String, Object> orderRequest = new HashMap<>(Map.of(
                "emprendimientoId", business,
                "modalidad", modality,
                "items", List.of(Map.of("productoId", productId, "cantidad", quantity)),
                "medioPago", paymentMethod));
        if (modality.equals("ENTREGA_DIRECTA") || modality.equals("DOMICILIO")) {
            orderRequest.put("ubicacionEntrega", Map.of(
                    "latitud", 4.72, "longitud", -74.06, "direccion", "Calle de prueba", "barrio", "Centro"));
        }
        if (reservation != null) {
            orderRequest.put("reserva", reservation);
        }
        if (expectedTotal != null) {
            orderRequest.put("totalEsperado", expectedTotal);
        }
        var response = request("POST", "/pedidos", customer, orderRequest, key());
        assertEquals(201, response.statusCode(), response.body());
        return body(response).get("id").toString();
    }

    private long eventCount(String eventType, String orderId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = ? AND aggregate_id = ?",
                Long.class, eventType, UUID.fromString(orderId));
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
