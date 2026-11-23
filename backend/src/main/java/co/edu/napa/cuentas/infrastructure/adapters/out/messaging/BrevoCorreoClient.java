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

/**
 * Envía el código por correo con la API transaccional de Brevo (POST /v3/smtp/email).
 * El remitente debe estar verificado en la cuenta de Brevo.
 */
@Component
@ConditionalOnProperty(name = "napa.verification.provider", havingValue = "sms-correo")
public class BrevoCorreoClient {
    private final HttpClient http;
    private final URI endpoint;
    private final String apiKey;
    private final String remitenteCorreo;
    private final String remitenteNombre;

    @Autowired
    public BrevoCorreoClient(
            @Value("${napa.verification.brevo.base-url:https://api.brevo.com}") String baseUrl,
            @Value("${napa.verification.brevo.api-key:}") String apiKey,
            @Value("${napa.verification.brevo.remitente-correo:}") String remitenteCorreo,
            @Value("${napa.verification.brevo.remitente-nombre:Ñapa}") String remitenteNombre
    ) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                baseUrl, apiKey, remitenteCorreo, remitenteNombre);
    }

    BrevoCorreoClient(HttpClient http, String baseUrl, String apiKey, String remitenteCorreo, String remitenteNombre) {
        this.http = http;
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/v3/smtp/email");
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.remitenteCorreo = remitenteCorreo == null ? "" : remitenteCorreo.trim();
        this.remitenteNombre = remitenteNombre == null || remitenteNombre.isBlank() ? "Ñapa" : remitenteNombre.trim();
    }

    public void enviarCodigo(String destino, String codigo) {
        if (apiKey.isEmpty() || remitenteCorreo.isEmpty()) {
            throw new EnvioFallidoException("Brevo no está configurado (NAPA_BREVO_API_KEY y NAPA_BREVO_SENDER_EMAIL)");
        }
        String texto = "Tu código de verificación de Ñapa es " + codigo
                + ". Vence en 10 minutos. Si no lo pediste, ignora este correo.";
        // Documento completo con charset y letras especiales como entidades: así ningún servidor
        // las convierte en "Ã‘apa" (pasaba con el fragmento suelto) y el correo no parece spam.
        String html = "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"utf-8\">"
                + "<title>C&oacute;digo de &Ntilde;apa</title></head>"
                + "<body style=\"margin:0;padding:24px;background:#FAF6EF\">"
                + "<div style=\"font-family:Arial,sans-serif;max-width:420px;margin:0 auto;background:#FFFFFF;"
                + "border-radius:12px;padding:24px;color:#342036\">"
                + "<h2 style=\"color:#E95F3D;margin-top:0\">&Ntilde;apa</h2>"
                + "<p>Tu c&oacute;digo de verificaci&oacute;n es:</p>"
                + "<p style=\"font-size:32px;font-weight:bold;letter-spacing:8px\">" + codigo + "</p>"
                + "<p>Vence en 10 minutos. Si no lo pediste, ignora este correo.</p>"
                + "</div></body></html>";
        String body = "{\"sender\":{\"name\":" + JsonTexto.de(remitenteNombre) + ",\"email\":"
                + JsonTexto.de(remitenteCorreo) + "},\"to\":[{\"email\":" + JsonTexto.de(destino) + "}],"
                + "\"subject\":" + JsonTexto.de("Tu código de Ñapa: " + codigo) + ","
                + "\"textContent\":" + JsonTexto.de(texto) + ",\"htmlContent\":" + JsonTexto.de(html) + "}";
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new EnvioFallidoException("Brevo no respondió: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EnvioFallidoException("Envío interrumpido");
        }
        if (response.statusCode() / 100 != 2) {
            String detalle = response.body() == null ? "" : response.body();
            throw new EnvioFallidoException("HTTP " + response.statusCode() + " "
                    + detalle.substring(0, Math.min(200, detalle.length())));
        }
    }
}
