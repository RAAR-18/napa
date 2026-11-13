package co.edu.napa.tarifas.domain.model;

import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TarifaMinimaTest {
    @Test
    void acceptsPositiveValueAndNullableDateForConfiguredDefault() {
        TarifaMinima tarifa = new TarifaMinima(3050, null);

        assertEquals(3050, tarifa.valor());
        assertNull(tarifa.createdAt());
        assertEquals(5000, new TarifaMinima(5000, Instant.now()).valor());
    }

    @Test
    void rejectsZeroAndNegativeValues() {
        for (long value : new long[]{0, -1}) {
            DomainException exception = assertThrows(
                    DomainException.class,
                    () -> new TarifaMinima(value, null)
            );
            assertEquals("VALIDATION_ERROR", exception.code());
            assertEquals("La tarifa mínima debe ser mayor a cero.", exception.getMessage());
        }
    }
}
