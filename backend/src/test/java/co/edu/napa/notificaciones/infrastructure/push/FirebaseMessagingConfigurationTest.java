package co.edu.napa.notificaciones.infrastructure.push;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FirebaseMessagingConfigurationTest {
    private static final String JSON = "{\"type\":\"service_account\",\"project_id\":\"napa\"}";

    @Test
    void acceptsThePlainServiceAccountJson() {
        assertEquals(JSON, text(FirebaseMessagingConfiguration.jsonBytes("  " + JSON + "\n")));
    }

    @Test
    void acceptsTheJsonEncodedInBase64EvenWithLineBreaks() {
        String encoded = Base64.getEncoder().encodeToString(JSON.getBytes(StandardCharsets.UTF_8));
        String wrapped = encoded.substring(0, 10) + "\n" + encoded.substring(10);
        assertEquals(JSON, text(FirebaseMessagingConfiguration.jsonBytes(wrapped)));
    }

    @Test
    void rejectsAValueThatIsNeitherJsonNorBase64() {
        assertThrows(IllegalStateException.class, () -> FirebaseMessagingConfiguration.jsonBytes("no-es-json%"));
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
