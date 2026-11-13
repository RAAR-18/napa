package co.edu.napa.emprendimientos.domain.model;

import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class EmprendimientoTest {
    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

    @Test
    void acceptsOpeningHoursThatCrossMidnight() {
        assertDoesNotThrow(() -> new Emprendimiento(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Panadería",
                CategoriaEmprendimiento.ALIMENTOS_PREPARADOS,
                "Pan recién horneado",
                LocalTime.of(18, 0),
                LocalTime.of(2, 0),
                EstadoEmprendimiento.ACTIVO,
                NOW,
                NOW
        ));
    }

    @Test
    void rejectsEqualOpeningAndClosingTimes() {
        DomainException exception = assertThrows(DomainException.class, () -> new Emprendimiento(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Panadería",
                CategoriaEmprendimiento.ALIMENTOS_PREPARADOS,
                "Pan recién horneado",
                LocalTime.NOON,
                LocalTime.NOON,
                EstadoEmprendimiento.ACTIVO,
                NOW,
                NOW
        ));

        assertEquals("VALIDATION_ERROR", exception.code());
    }

    @Test
    void rejectsBlankOrOverlongNames() {
        DomainException blankName = assertThrows(DomainException.class, () -> create(" ", "Descripción"));
        DomainException longName = assertThrows(
                DomainException.class,
                () -> create("x".repeat(101), "Descripción")
        );

        assertEquals("VALIDATION_ERROR", blankName.code());
        assertEquals("VALIDATION_ERROR", longName.code());
    }

    private Emprendimiento create(String nombre, String descripcion) {
        return new Emprendimiento(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                nombre,
                CategoriaEmprendimiento.OTROS,
                descripcion,
                LocalTime.of(8, 0),
                LocalTime.of(17, 0),
                EstadoEmprendimiento.ACTIVO,
                NOW,
                NOW
        );
    }
}
