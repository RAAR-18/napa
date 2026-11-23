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
import java.util.regex.Pattern;

/**
 * Cliente de POST /smsapi/create-task de SMSReactor (https://smsreactor.com/apidocs/).
 * El SMS sale por el celular Android conectado a la cuenta, con la app de SMSReactor abierta.
 */
@Component
@ConditionalOnProperty(name = "napa.verification.provider", havingValue = "smsreactor")
public class SmsReactorClient {
    private static final Pattern OK = Pattern.compile("\"ok\"\\s*:\\s*true");
    private final HttpClient http;
    private final URI endpoint;
    private final String apiKey;
    private final Integer phoneId;

    @Autowired
    public SmsReactorClient(
            @Value("${napa.verification.smsreactor.base-url:https://smsreactor.com}") String baseUrl,
            @Value("${napa.verification.smsreactor.api-key:}") String apiKey,
            @Value("${napa.verification.smsreactor.phone-id:}") String phoneId
    ) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), baseUrl, apiKey, phoneId);
    }

    SmsReactorClient(HttpClient http, String baseUrl, String apiKey, String phoneId) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("NAPA_SMSREACTOR_API_KEY is required for the smsreactor provider");
        }
        this.http = http;
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/smsapi/create-task");
        this.apiKey = apiKey.trim();
        this.phoneId = phoneId == null || phoneId.isBlank() ? null : Integer.valueOf(phoneId.trim());
    }

    /** Pide el envío; lanza {@link EnvioFallidoException} si SMSReactor no lo acepta. */
    public void enviar(String celularE164, String mensaje) {
        String body = "{\"recipient\":" + json(celularE164) + ",\"message\":" + json(mensaje)
                + (phoneId == null ? "" : ",\"phone_id\":" + phoneId) + "}";
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new EnvioFallidoException("SMSReactor no respondió: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EnvioFallidoException("Envío interrumpido");
        }
        if (response.statusCode() / 100 != 2 || !OK.matcher(response.body()).find()) {
            String detalle = response.body() == null ? "" : response.body();
            throw new EnvioFallidoException("HTTP " + response.statusCode() + " "
                    + detalle.substring(0, Math.min(200, detalle.length())));
        }
    }

    static String json(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u%04x".formatted((int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    public static class EnvioFallidoException extends RuntimeException {
        public EnvioFallidoException(String message) {
            super(message);
        }
    }
}
