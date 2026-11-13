package co.edu.napa.emprendimientos.infrastructure.adapters.in.web;

import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MiEmprendimientoControllerTest {
    @Test
    void readsOnlyTheFieldsThatCame() {
        var edition = MiEmprendimientoController.edicion(Map.of(
                "horaCierre", "19:30",
                "ubicacion", Map.of("latitud", 11.2, "longitud", -74, "referencia", "Playa")));

        assertNull(edition.descripcion());
        assertNull(edition.horaApertura());
        assertEquals(LocalTime.of(19, 30), edition.horaCierre());
        assertEquals(11.2, edition.ubicacion().latitud());
        assertEquals(-74.0, edition.ubicacion().longitud());
        assertNull(edition.ubicacion().direccion());
    }

    @Test
    void nameCategoryAndStateCannotBeEdited() {
        for (String field : new String[] {"nombre", "categoria", "estado"}) {
            DomainException error = assertThrows(DomainException.class,
                    () -> MiEmprendimientoController.edicion(Map.of(field, "x")));
            assertEquals("VALIDATION_ERROR", error.code());
        }
    }

    @Test
    void rejectsAnEmptyBodyAndBadTimes() {
        assertEquals("VALIDATION_ERROR", assertThrows(DomainException.class,
                () -> MiEmprendimientoController.edicion(Map.of())).code());
        assertEquals("VALIDATION_ERROR", assertThrows(DomainException.class,
                () -> MiEmprendimientoController.edicion(Map.of("horaApertura", "7am"))).code());
    }
}
