package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.cuentas.domain.model.Usuario;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuscarCuentaPorCorreoUseCaseTest {
    private final UsuarioRepositoryPort usuarios = mock(UsuarioRepositoryPort.class);
    private final BuscarCuentaPorCorreoUseCase useCase = new BuscarCuentaPorCorreoUseCase(usuarios);

    @Test
    void findsThePhoneOfTheAccountWithThatEmail() {
        Instant now = Instant.parse("2026-11-22T12:00:00Z");
        when(usuarios.findByCorreo("ana@gmail.com")).thenReturn(Optional.of(new Usuario(
                UUID.randomUUID(), "Ana", "+573001234567", "ana@gmail.com", "hash",
                RolUsuario.CLIENTE, EstadoUsuario.PENDIENTE_VERIFICACION, null, now, now
        )));

        assertEquals("+573001234567", useCase.celularDe(" Ana@Gmail.com "));
    }

    @Test
    void rejectsAnUnknownEmail() {
        when(usuarios.findByCorreo("nadie@gmail.com")).thenReturn(Optional.empty());

        DomainException error = assertThrows(DomainException.class, () -> useCase.celularDe("nadie@gmail.com"));

        assertEquals("VALIDATION_ERROR", error.code());
    }
}
