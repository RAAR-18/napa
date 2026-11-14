package co.edu.napa.domicilios.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarNombresUsuarioPort;
import co.edu.napa.domicilios.application.ports.in.ListarDomiciliosVendedorPort.Filtro;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.domicilios.domain.model.EstadoDomicilio;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListarDomiciliosVendedorUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    private static final UUID VENDEDOR = UUID.randomUUID();

    private final DomicilioRepositoryPort domicilios = mock(DomicilioRepositoryPort.class);
    private final ConsultarNombresUsuarioPort usuarios = mock(ConsultarNombresUsuarioPort.class);
    private final ListarDomiciliosVendedorUseCase useCase = new ListarDomiciliosVendedorUseCase(domicilios, usuarios);

    @Test
    void listaLosDomiciliosDelVendedorConSuEstado() {
        Domicilio disponible = Domicilio.publicar(UUID.randomUUID(), VENDEDOR, UUID.randomUUID(), 4_500,
                UUID.randomUUID(), UUID.randomUUID(), NOW);
        Filtro filtro = new Filtro(EstadoDomicilio.DISPONIBLE, null, null, false);
        when(domicilios.listarVendedor(VENDEDOR, filtro, 20, 10)).thenReturn(List.of(disponible));
        when(domicilios.contarVendedor(VENDEDOR, filtro)).thenReturn(21L);

        var pagina = useCase.listar(VENDEDOR, filtro, 2, 10);

        assertEquals(21, pagina.totalElements());
        assertEquals(1, pagina.content().size());
        var item = pagina.content().getFirst();
        assertEquals(EstadoDomicilio.DISPONIBLE, item.estado());
        assertEquals(4_500, item.tarifa());
        assertNull(item.domiciliario());
        verify(usuarios, never()).consultar(any());
    }

    @Test
    void sinDomiciliosDevuelvePaginaVacia() {
        when(domicilios.listarVendedor(VENDEDOR, Filtro.SIN_FILTROS, 0, 20)).thenReturn(List.of());
        when(domicilios.contarVendedor(VENDEDOR, Filtro.SIN_FILTROS)).thenReturn(0L);
        when(usuarios.consultar(any())).thenReturn(Map.of());

        var pagina = useCase.listar(VENDEDOR, null, 0, 20);

        assertTrue(pagina.content().isEmpty());
        assertEquals(0, pagina.totalElements());
    }

    @Test
    void validaPaginacionYRangoDeFechas() {
        assertEquals("VALIDATION_ERROR",
                assertThrows(DomainException.class, () -> useCase.listar(VENDEDOR, null, -1, 20)).code());
        assertEquals("VALIDATION_ERROR",
                assertThrows(DomainException.class, () -> useCase.listar(VENDEDOR, null, 0, 101)).code());
        Filtro invertido = new Filtro(null, NOW, NOW.minusSeconds(1), false);
        assertEquals("VALIDATION_ERROR",
                assertThrows(DomainException.class, () -> useCase.listar(VENDEDOR, invertido, 0, 20)).code());
    }
}
