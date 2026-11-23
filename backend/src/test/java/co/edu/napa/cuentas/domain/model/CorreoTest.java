package co.edu.napa.cuentas.domain.model;

import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorreoTest {
    @ParameterizedTest
    @ValueSource(strings = {"ana@gmail,com", "ana@gmail", "ana@@gmail.com", "ana gmail.com", "ana@.com",
            "ana@gmail..com", ".ana@gmail.com", "ana@gmail.c"})
    void rejectsMalformedAddresses(String correo) {
        DomainException exception = assertThrows(DomainException.class, () -> Correo.desde(correo));
        assertEquals("VALIDATION_ERROR", exception.code());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana@gmial.com", "ana@hotmal.com", "ana@gmail.con", "ana@outlok.com", "ana@yaho.com"})
    void suggestsTheIntendedCommonDomain(String correo) {
        DomainException exception = assertThrows(DomainException.class, () -> Correo.desde(correo));
        assertTrue(exception.getMessage().contains("¿Quisiste decir"), exception.getMessage());
    }

    @Test
    void acceptsCommonAndInstitutionalAddresses() {
        assertEquals("ana.perez@gmail.com", Correo.desde("  Ana.Perez@Gmail.com ").valor());
        assertEquals("estudiante@unimagdalena.edu.co", Correo.desde("estudiante@unimagdalena.edu.co").valor());
        assertEquals("unimagdalena.edu.co", Correo.desde("estudiante@unimagdalena.edu.co").dominio());
    }
}
