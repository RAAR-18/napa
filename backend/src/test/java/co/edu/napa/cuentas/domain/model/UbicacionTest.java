package co.edu.napa.cuentas.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UbicacionTest {
    private final Instant now = Instant.parse("2026-10-20T12:00:00Z");

    @Test
    void acceptsCoordinatesOnTheGeographicBoundaries() {
        assertDoesNotThrow(() -> new Ubicacion(
                UUID.randomUUID(), 90, -180, "Dirección", null, now, now
        ));
    }

    @Test
    void rejectsCoordinatesOutsideTheirValidRanges() {
        assertThrows(IllegalArgumentException.class, () -> new Ubicacion(
                UUID.randomUUID(), 90.01, 0, "Dirección", null, now, now
        ));
        assertThrows(IllegalArgumentException.class, () -> new Ubicacion(
                UUID.randomUUID(), 0, Double.NaN, "Dirección", null, now, now
        ));
    }

    @Test
    void rejectsInvalidAddressData() {
        assertThrows(IllegalArgumentException.class, () -> new Ubicacion(
                UUID.randomUUID(), 0, 0, " ", null, now, now
        ));
        assertThrows(IllegalArgumentException.class, () -> new Ubicacion(
                UUID.randomUUID(), 0, 0, "x".repeat(301), null, now, now
        ));
    }
}
