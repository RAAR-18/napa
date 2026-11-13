package co.edu.napa.emprendimientos.application.usecase;

import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.emprendimientos.application.ports.in.CrearEmprendimientoCommand;
import co.edu.napa.emprendimientos.application.ports.in.CrearEmprendimientoResult;
import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoRepositoryPort;
import co.edu.napa.emprendimientos.domain.model.CategoriaEmprendimiento;
import co.edu.napa.emprendimientos.domain.model.EstadoEmprendimiento;
import co.edu.napa.shared.application.ports.out.UbicacionRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.Ubicacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrearEmprendimientoUseCaseTest {
    private static final UUID OWNER_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

    @Mock private EmprendimientoRepositoryPort emprendimientos;
    @Mock private UbicacionRepositoryPort ubicaciones;

    private CrearEmprendimientoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CrearEmprendimientoUseCase(
                emprendimientos,
                ubicaciones,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createsActiveBusinessAndAllowsAnAmbulantZoneWithoutStreetAddress() {
        when(emprendimientos.existsActiveByUsuarioId(OWNER_ID)).thenReturn(false);
        when(ubicaciones.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(emprendimientos.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CrearEmprendimientoResult result = useCase.crear(command(RolUsuario.VENDEDOR_AMBULANTE, null));

        assertEquals(EstadoEmprendimiento.ACTIVO, result.emprendimiento().estado());
        assertEquals(RolUsuario.VENDEDOR_AMBULANTE, result.tipoVendedor());
        assertNull(result.ubicacion().direccion());
        verify(ubicaciones).save(any(Ubicacion.class));
        verify(emprendimientos).save(result.emprendimiento());
    }

    @Test
    void requiresAnAddressForFixedLocationSellers() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.crear(command(RolUsuario.VENDEDOR_PUNTO_FIJO, null))
        );

        assertEquals("VALIDATION_ERROR", exception.code());
        verify(ubicaciones, never()).save(any());
        verify(emprendimientos, never()).save(any());
    }

    @Test
    void rejectsASecondActiveBusinessBeforeSavingItsLocation() {
        when(emprendimientos.existsActiveByUsuarioId(OWNER_ID)).thenReturn(true);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.crear(command(RolUsuario.VENDEDOR_AMBULANTE, null))
        );

        assertEquals("CONFLICT", exception.code());
        verify(ubicaciones, never()).save(any());
        verify(emprendimientos, never()).save(any());
    }

    @Test
    void rejectsNonSellerRoles() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.crear(command(RolUsuario.CLIENTE, null))
        );

        assertEquals("FORBIDDEN", exception.code());
        verify(emprendimientos, never()).existsActiveByUsuarioId(any());
    }

    @Test
    void rejectsCoordinatesOutsideGeographicRanges() {
        CrearEmprendimientoCommand invalid = new CrearEmprendimientoCommand(
                OWNER_ID,
                RolUsuario.VENDEDOR_AMBULANTE,
                "Frutas Doña Rosa",
                CategoriaEmprendimiento.FRUTAS_VERDURAS,
                "Frutas frescas del día",
                LocalTime.of(6, 0),
                LocalTime.of(18, 0),
                91.0,
                -74.211,
                null,
                null
        );

        DomainException exception = assertThrows(DomainException.class, () -> useCase.crear(invalid));

        assertEquals("VALIDATION_ERROR", exception.code());
        verify(emprendimientos, never()).existsActiveByUsuarioId(any());
    }

    @Test
    void trimsOptionalAddressAndReturnsThePersistedLocation() {
        when(emprendimientos.existsActiveByUsuarioId(OWNER_ID)).thenReturn(false);
        when(ubicaciones.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(emprendimientos.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<Ubicacion> location = ArgumentCaptor.forClass(Ubicacion.class);
        useCase.crear(command(RolUsuario.VENDEDOR_PUNTO_FIJO, "  Calle 1  "));

        verify(ubicaciones).save(location.capture());
        assertEquals("Calle 1", location.getValue().direccion());
    }

    private CrearEmprendimientoCommand command(RolUsuario rol, String direccion) {
        return new CrearEmprendimientoCommand(
                OWNER_ID,
                rol,
                "Frutas Doña Rosa",
                CategoriaEmprendimiento.FRUTAS_VERDURAS,
                "Frutas frescas del día",
                LocalTime.of(6, 0),
                LocalTime.of(18, 0),
                11.2408,
                -74.211,
                direccion,
                "Frente al parque"
        );
    }
}
