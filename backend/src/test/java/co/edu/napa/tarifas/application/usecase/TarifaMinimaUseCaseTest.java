package co.edu.napa.tarifas.application.usecase;

import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.tarifas.application.ports.out.TarifaMinimaDefectoPort;
import co.edu.napa.tarifas.application.ports.out.TarifaMinimaRepositoryPort;
import co.edu.napa.tarifas.domain.model.TarifaMinima;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TarifaMinimaUseCaseTest {
    private static final long DEFAULT_VALUE = 3050;
    private static final UUID ADMIN_ID = UUID.fromString("55000000-0000-0000-0000-000000000001");

    private TarifaMinimaRepositoryPort repository;
    private TarifaMinimaDefectoPort defaultPort;

    @BeforeEach
    void setUp() {
        repository = mock(TarifaMinimaRepositoryPort.class);
        defaultPort = () -> DEFAULT_VALUE;
    }

    @Test
    void consultReturnsConfiguredValueBeforeAnyChange() {
        when(repository.vigente()).thenReturn(Optional.empty());

        TarifaMinima result = new ConsultarTarifaMinimaUseCase(repository, defaultPort).consultar();

        assertEquals(DEFAULT_VALUE, result.valor());
        assertNull(result.createdAt());
    }

    @Test
    void consultReturnsLatestPersistedValueWhenPresent() {
        Instant changedAt = Instant.parse("2026-11-11T12:00:00Z");
        when(repository.vigente()).thenReturn(Optional.of(new TarifaMinima(5000, changedAt)));

        TarifaMinima result = new ConsultarTarifaMinimaUseCase(repository, defaultPort).consultar();

        assertEquals(5000, result.valor());
        assertEquals(changedAt, result.createdAt());
    }

    @Test
    void updateDelegatesTheValueAndConfiguredFallbackToTheRepository() {
        TarifaMinima expected = new TarifaMinima(4500, Instant.parse("2026-11-11T12:00:00Z"));
        when(repository.registrarSiCambio(4500, ADMIN_ID, DEFAULT_VALUE)).thenReturn(expected);

        TarifaMinima result = new ActualizarTarifaMinimaUseCase(repository, defaultPort)
                .actualizar(ADMIN_ID, 4500);

        assertEquals(expected, result);
        verify(repository).registrarSiCambio(4500, ADMIN_ID, DEFAULT_VALUE);
    }

    @Test
    void updateRejectsInvalidValueBeforeWriting() {
        ActualizarTarifaMinimaUseCase useCase = new ActualizarTarifaMinimaUseCase(repository, defaultPort);

        assertThrows(DomainException.class, () -> useCase.actualizar(ADMIN_ID, 0));
    }
}
