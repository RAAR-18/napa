package co.edu.napa.cuentas.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispositivoTokenTest {
    @Test
    void requiresTokenAndPlatform() {
        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        UUID id = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> new DispositivoToken(
                id, usuarioId, " ", PlataformaDispositivo.ANDROID, now, now, now
        ));
        assertThrows(NullPointerException.class, () -> new DispositivoToken(
                id, usuarioId, "fcm-token", null, now, now, now
        ));
    }

    @Test
    void doesNotExposeTheFcmTokenInItsStringRepresentation() {
        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        String secretToken = "fcm-device-token";
        DispositivoToken token = new DispositivoToken(
                UUID.randomUUID(), UUID.randomUUID(), secretToken,
                PlataformaDispositivo.IOS, now, now, now
        );

        assertFalse(token.toString().contains(secretToken));
        assertTrue(token.toString().contains("[REDACTED]"));
    }
}
