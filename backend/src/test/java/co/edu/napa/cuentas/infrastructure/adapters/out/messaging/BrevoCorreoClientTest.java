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

class BrevoCorreoClientTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsTheCodeFromTheVerifiedSender() throws IOException {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        String baseUrl = start(201, "{\"messageId\":\"<abc@brevo>\"}", body, key);

        new BrevoCorreoClient(HttpClient.newHttpClient(), baseUrl, "xkeysib-test", "napa@example.com", "Ñapa")
                .enviarCodigo("ana@example.com", "481516");

        assertEquals("xkeysib-test", key.get());
        assertTrue(body.get().startsWith(
                "{\"sender\":{\"name\":\"Ñapa\",\"email\":\"napa@example.com\"},\"to\":[{\"email\":\"ana@example.com\"}]"));
        assertTrue(body.get().contains("\"subject\":\"Tu código de Ñapa: 481516\""));
        // El HTML lleva charset y entidades para que "Ñapa" no llegue como "Ã‘apa".
        assertTrue(body.get().contains("meta charset="));
        assertTrue(body.get().contains("&Ntilde;apa"));
    }

    @Test
    void failsWhenBrevoRejectsTheEmail() throws IOException {
        String baseUrl = start(400, "{\"code\":\"invalid_parameter\"}", new AtomicReference<>(), new AtomicReference<>());
        BrevoCorreoClient client = new BrevoCorreoClient(
                HttpClient.newHttpClient(), baseUrl, "xkeysib-test", "napa@example.com", "Ñapa");

        EnvioFallidoException exception = assertThrows(EnvioFallidoException.class,
                () -> client.enviarCodigo("ana@example.com", "481516"));
        assertTrue(exception.getMessage().contains("400"));
    }

    @Test
    void failsWithoutCallingBrevoWhenTheSenderIsMissing() {
        BrevoCorreoClient client = new BrevoCorreoClient(
                HttpClient.newHttpClient(), "http://localhost:1", "xkeysib-test", "", "Ñapa");

        assertThrows(EnvioFallidoException.class, () -> client.enviarCodigo("ana@example.com", "481516"));
    }

    private String start(int status, String response, AtomicReference<String> body, AtomicReference<String> key)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            key.set(exchange.getRequestHeaders().getFirst("api-key"));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
