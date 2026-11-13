package co.edu.napa.emprendimientos.application.usecase;

import co.edu.napa.emprendimientos.application.ports.in.EmprendimientosPagina;
import co.edu.napa.emprendimientos.application.ports.in.ListarEmprendimientosQuery;
import co.edu.napa.emprendimientos.application.ports.out.CampoOrdenEmprendimientos;
import co.edu.napa.emprendimientos.application.ports.out.DireccionOrdenEmprendimientos;
import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoListadoProjection;
import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoListadoRepositoryPort;
import co.edu.napa.emprendimientos.application.ports.out.FiltroEmprendimientos;
import co.edu.napa.emprendimientos.domain.model.CategoriaEmprendimiento;
import co.edu.napa.emprendimientos.domain.model.TipoVendedor;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListarEmprendimientosUseCaseTest {
    @Mock
    private EmprendimientoListadoRepositoryPort repository;

    private ListarEmprendimientosUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListarEmprendimientosUseCase(
                repository,
                Clock.fixed(Instant.parse("2026-11-06T15:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void calculatesOpenStatusUsingColombianLocalTimeAndPreservesPagination() {
        FiltroEmprendimientos filter = filter(null, null, null);
        when(repository.buscar(filter, 20, 10)).thenReturn(List.of(new EmprendimientoListadoProjection(
                UUID.randomUUID(),
                "Frutas de Ana",
                CategoriaEmprendimiento.FRUTAS_VERDURAS,
                TipoVendedor.AMBULANTE,
                LocalTime.of(8, 0),
                LocalTime.of(18, 0),
                4.7,
                -74.0,
                null,
                null
        )));
        when(repository.contar(filter)).thenReturn(31L);

        EmprendimientosPagina page = useCase.listar(new ListarEmprendimientosQuery(filter, 2, 10));

        assertEquals(2, page.page());
        assertEquals(10, page.size());
        assertEquals(31, page.totalElements());
        assertEquals(1, page.content().size());
        assertEquals(true, page.content().getFirst().abierto());
        verify(repository).buscar(filter, 20, 10);
    }

    @Test
    void requiresCoordinatesWhenFilteringOrSortingByDistance() {
        FiltroEmprendimientos filter = filter(null, null, 2_000);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.listar(new ListarEmprendimientosQuery(filter, 0, 20))
        );

        assertEquals("VALIDATION_ERROR", exception.code());
    }

    @Test
    void rejectsIncompleteAndOutOfRangeCoordinatePairs() {
        assertValidationError(filter(4.7, null, null));
        assertValidationError(filter(91.0, -74.0, null));
        assertValidationError(filter(4.7, -181.0, null));
        assertValidationError(filter(Double.NaN, -74.0, null));
        assertValidationError(filter(4.7, Double.POSITIVE_INFINITY, null));
    }

    @Test
    void requiresCoordinatesWhenOrderingByDistance() {
        FiltroEmprendimientos filter = new FiltroEmprendimientos(
                null,
                null,
                null,
                null,
                true,
                CampoOrdenEmprendimientos.CREATED_AT,
                DireccionOrdenEmprendimientos.DESC
        );

        assertValidationError(filter);
    }

    @Test
    void rejectsPageSizesAboveTheMaximum() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.listar(new ListarEmprendimientosQuery(filter(null, null, null), 0, 101))
        );

        assertEquals("VALIDATION_ERROR", exception.code());
    }

    private void assertValidationError(FiltroEmprendimientos filter) {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> useCase.listar(new ListarEmprendimientosQuery(filter, 0, 20))
        );
        assertEquals("VALIDATION_ERROR", exception.code());
    }

    private FiltroEmprendimientos filter(Double latitude, Double longitude, Integer radius) {
        return new FiltroEmprendimientos(
                null,
                latitude,
                longitude,
                radius,
                false,
                CampoOrdenEmprendimientos.CREATED_AT,
                DireccionOrdenEmprendimientos.DESC
        );
    }
}
