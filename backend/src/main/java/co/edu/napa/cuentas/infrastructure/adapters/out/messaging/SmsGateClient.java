package co.edu.napa.cuentas.infrastructure.adapters.out.messaging;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/** Encola un SMS que el Android vinculado a SMS Gate envía con su SIM. */
@Component
@ConditionalOnProperty(name = "napa.verification.provider", havingValue = "sms-correo")
public class SmsGateClient {
    private final HttpClient http;
    private final URI endpoint;
    private final String authorization;

    @Autowired
    public SmsGateClient(
            @Value("${napa.verification.sms-gate.base-url:https://api.sms-gate.app}") String baseUrl,
            @Value("${napa.verification.sms-gate.username:}") String username,
            @Value("${napa.verification.sms-gate.password:}") String password
    ) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), baseUrl, username, password);
    }

    SmsGateClient(HttpClient http, String baseUrl, String username, String password) {
        this.http = http;
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/3rdparty/v1/messages");
        this.authorization = username.isBlank() || password.isBlank() ? "" : "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    public void enviar(String celularE164, String mensaje) {
        if (authorization.isEmpty()) {
            throw new EnvioFallidoException("SMS Gate no está configurado (NAPA_SMS_GATE_USERNAME y NAPA_SMS_GATE_PASSWORD)");
        }
        String body = "{\"textMessage\":{\"text\":" + JsonTexto.de(mensaje)
                + "},\"phoneNumbers\":[" + JsonTexto.de(celularE164) + "]}";
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<Void> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException exception) {
            throw new EnvioFallidoException("SMS Gate no respondió");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EnvioFallidoException("Envío interrumpido");
        }
        if (response.statusCode() != 202) {
            throw new EnvioFallidoException("SMS Gate rechazó el envío (HTTP " + response.statusCode() + ")");
        }
    }
}
