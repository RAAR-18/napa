package co.edu.napa.pedidos.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TarifaDomicilioTest {
    @Test
    void routePriceUsesDistanceAndDurationAndRoundsHalfUpToHundreds() {
        long amount = TarifaDomicilio.calcularRuta(
                3_050, 500, 200, 2_000, Duration.ofMinutes(10), 3_050
        );

        assertEquals(6_100, amount);
    }

    @Test
    void routePriceNeverFallsBelowConfiguredMinimum() {
        long amount = TarifaDomicilio.calcularRuta(
                0, 0, 0, 0, Duration.ZERO, 3_050
        );

        assertEquals(3_050, amount);
    }

    @Test
    void fallbackAdjustsStraightLineDistanceAndHasNoTimeComponent() {
        long amount = TarifaDomicilio.calcularRespaldo(
                3_050, 500, BigDecimal.valueOf(2_000), new BigDecimal("1.3"), 3_050
        );

        assertEquals(4_400, amount);
    }

    @Test
    void rejectsNegativeDistance() {
        assertThrows(IllegalArgumentException.class, () -> TarifaDomicilio.calcularRuta(
                3_050, 500, 200, -1, Duration.ZERO, 3_050
        ));
    }

    @Test
    void rejectsNegativeRate() {
        assertThrows(IllegalArgumentException.class, () -> TarifaDomicilio.calcularRuta(
                3_050, 500, -1, 0, Duration.ZERO, 3_050
        ));
    }

    @Test
    void rejectsInvalidFallbackFactor() {
        assertThrows(IllegalArgumentException.class, () -> TarifaDomicilio.calcularRespaldo(
                3_050, 500, BigDecimal.ONE, BigDecimal.ZERO, 3_050
        ));
    }
}
