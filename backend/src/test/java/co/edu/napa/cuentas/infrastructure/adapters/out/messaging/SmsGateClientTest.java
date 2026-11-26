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

class SmsGateClientTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void queuesTheMessageWithBasicAuthentication() throws IOException {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        String baseUrl = start(202, "{\"state\":\"Pending\"}", body, key);

        new SmsGateClient(HttpClient.newHttpClient(), baseUrl, "test", "secret")
                .enviar("+573007080825", "Napa: tu codigo es \"123456\"");

        assertEquals("Basic dGVzdDpzZWNyZXQ=", key.get());
        assertEquals("{\"textMessage\":{\"text\":\"Napa: tu codigo es \\\"123456\\\"\"},"
                + "\"phoneNumbers\":[\"+573007080825\"]}", body.get());
    }

    @Test
    void failsWhenTheGatewayRejectsTheCredentials() throws IOException {
        String baseUrl = start(401, "{\"error\":\"Unauthorized\"}", new AtomicReference<>(),
                new AtomicReference<>());
        SmsGateClient client = new SmsGateClient(HttpClient.newHttpClient(), baseUrl, "test", "secret");

        EnvioFallidoException exception = assertThrows(
                EnvioFallidoException.class,
                () -> client.enviar("+573007080825", "hola")
        );
        assertTrue(exception.getMessage().contains("401"));
    }

    @Test
    void failsWithoutCallingTheApiWhenCredentialsAreMissing() {
        SmsGateClient client = new SmsGateClient(HttpClient.newHttpClient(), "http://localhost:1", "test", "");

        assertThrows(EnvioFallidoException.class, () -> client.enviar("+573007080825", "hola"));
    }

    private String start(int status, String response, AtomicReference<String> body, AtomicReference<String> key)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/3rdparty/v1/messages", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            key.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }
}
