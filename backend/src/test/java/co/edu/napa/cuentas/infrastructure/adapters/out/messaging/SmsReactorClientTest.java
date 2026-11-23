package co.edu.napa.cuentas.infrastructure.adapters.out.messaging;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsReactorClientTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsTheTaskWithTheApiKeyHeader() throws IOException {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        String baseUrl = start(201, "{\"ok\": true, \"id\": 7, \"status\": \"pending\"}", body, key);

        new SmsReactorClient(HttpClient.newHttpClient(), baseUrl, "skr_test", "3")
                .enviar("+573007080825", "Napa: tu codigo es \"123456\"");

        assertEquals("skr_test", key.get());
        assertEquals("{\"recipient\":\"+573007080825\",\"message\":\"Napa: tu codigo es \\\"123456\\\"\","
                + "\"phone_id\":3}", body.get());
    }

    @Test
    void failsWhenTheDailyCreditsRunOut() throws IOException {
        String baseUrl = start(402, "{\"error\":\"Daily send limit reached.\"}", new AtomicReference<>(),
                new AtomicReference<>());
        SmsReactorClient client = new SmsReactorClient(HttpClient.newHttpClient(), baseUrl, "skr_test", "");

        SmsReactorClient.EnvioFallidoException exception = assertThrows(
                SmsReactorClient.EnvioFallidoException.class,
                () -> client.enviar("+573007080825", "hola")
        );
        assertTrue(exception.getMessage().contains("402"));
    }

    @Test
    void requiresAnApiKey() {
        assertThrows(IllegalArgumentException.class,
                () -> new SmsReactorClient(HttpClient.newHttpClient(), "http://localhost", " ", ""));
    }

    private String start(int status, String response, AtomicReference<String> body, AtomicReference<String> key)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/smsapi/create-task", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            key.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }
}
