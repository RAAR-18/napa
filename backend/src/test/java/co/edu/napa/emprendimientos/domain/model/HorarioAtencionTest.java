package co.edu.napa.emprendimientos.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorarioAtencionTest {
    @Test
    void includesOpeningTimeAndExcludesClosingTime() {
        assertTrue(HorarioAtencion.estaAbierto(
                LocalTime.of(8, 0),
                LocalTime.of(18, 0),
                LocalTime.of(8, 0)
        ));
        assertFalse(HorarioAtencion.estaAbierto(
                LocalTime.of(8, 0),
                LocalTime.of(18, 0),
                LocalTime.of(18, 0)
        ));
    }

    @Test
    void handlesHoursThatCrossMidnight() {
        assertTrue(HorarioAtencion.estaAbierto(
                LocalTime.of(20, 0),
                LocalTime.of(2, 0),
                LocalTime.of(23, 0)
        ));
        assertTrue(HorarioAtencion.estaAbierto(
                LocalTime.of(20, 0),
                LocalTime.of(2, 0),
                LocalTime.of(1, 59)
        ));
        assertFalse(HorarioAtencion.estaAbierto(
                LocalTime.of(20, 0),
                LocalTime.of(2, 0),
                LocalTime.of(2, 0)
        ));
        assertFalse(HorarioAtencion.estaAbierto(
                LocalTime.of(20, 0),
                LocalTime.of(2, 0),
                LocalTime.of(12, 0)
        ));
    }
}
