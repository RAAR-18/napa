package co.edu.napa.shared.domain.geo;

import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeoPointTest {
    @Test
    void rejectsCoordinatesOutsideEarthBounds() {
        DomainException latitudeError = assertThrows(
                DomainException.class,
                () -> new GeoPoint(91, 0)
        );
        DomainException longitudeError = assertThrows(
                DomainException.class,
                () -> new GeoPoint(0, 181)
        );

        assertEquals("VALIDATION_ERROR", latitudeError.code());
        assertEquals("VALIDATION_ERROR", longitudeError.code());
    }
}
