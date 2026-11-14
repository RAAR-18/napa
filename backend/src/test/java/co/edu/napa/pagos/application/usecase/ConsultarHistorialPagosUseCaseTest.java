package co.edu.napa.pagos.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarNombresUsuarioPort;
import co.edu.napa.pagos.application.ports.in.ConsultarHistorialPagosPort.Filtros;
import co.edu.napa.pagos.application.ports.in.ConsultarHistorialPagosPort.Pagina;
import co.edu.napa.pagos.application.ports.in.ConsultarHistorialPagosPort.PagoGeneral;
import co.edu.napa.pagos.application.ports.in.ConsultarHistorialPagosPort.PagoPropio;
import co.edu.napa.pagos.application.ports.in.ConsultarHistorialPagosPort.RolHistorial;
import co.edu.napa.pagos.application.ports.in.ConsultarHistorialPagosPort.Usuario;
import co.edu.napa.pagos.application.ports.out.HistorialPagosQuery;
import co.edu.napa.pagos.application.ports.out.PagoRepositoryPort;
import co.edu.napa.pagos.application.ports.out.PagoRepositoryPort.FilaHistorial;
import co.edu.napa.pagos.domain.model.MedioPago;
import co.edu.napa.pagos.domain.model.Pago;
import co.edu.napa.pagos.domain.model.TipoPago;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsultarHistorialPagosUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-13T18:00:00Z");
    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID VENDEDOR = UUID.randomUUID();
    private static final UUID DOMICILIARIO = UUID.randomUUID();
    private static final Filtros TODOS = new Filtros(null, null, null, null, null, false, false, 0, 20);

    private PagoRepositoryPort pagos;
    private ConsultarNombresUsuarioPort usuarios;
    private ConsultarHistorialPagosUseCase useCase;
    private Pago pagoPedido;
    private Pago tarifa;

    @BeforeEach
    void setUp() {
        pagos = mock(PagoRepositoryPort.class);
        usuarios = mock(ConsultarNombresUsuarioPort.class);
        useCase = new ConsultarHistorialPagosUseCase(pagos, usuarios);
        pagoPedido = Pago.pedidoPendiente(UUID.randomUUID(), CLIENTE, VENDEDOR, 14_500, MedioPago.CUENTA_BANCARIA, NOW);
        tarifa = Pago.tarifaRegistrada(UUID.randomUUID(), CLIENTE, VENDEDOR, DOMICILIARIO, 5_000, NOW);
        when(pagos.listarHistorial(any())).thenReturn(List.of(
                new FilaHistorial(pagoPedido, "DOMICILIO"), new FilaHistorial(tarifa, null)));
        when(pagos.contarHistorial(any())).thenReturn(2L);
    }

    @Test
    void elClienteSoloPideSusPagosDePedido() {
        useCase.consultarPropio(CLIENTE, RolHistorial.CLIENTE, TODOS);

        HistorialPagosQuery query = query();
        assertEquals(CLIENTE, query.compradorId());
        assertNull(query.vendedorId());
        assertNull(query.domiciliarioId());
        assertEquals(TipoPago.PEDIDO, query.tipoFijo());
    }

    @Test
    void elVendedorVeSusCobrosConModalidadYLasTarifasQuePago() {
        Pagina<PagoPropio> pagina = useCase.consultarPropio(VENDEDOR, RolHistorial.VENDEDOR, TODOS);

        assertEquals(VENDEDOR, query().vendedorId());
        assertNull(query().tipoFijo());
        assertEquals("DOMICILIO", pagina.content().get(0).modalidad());
        assertNull(pagina.content().get(1).modalidad());
        assertEquals(tarifa.domicilioId(), pagina.content().get(1).domicilioId());
        assertEquals(2, pagina.totalElements());
    }

    @Test
    void elDomiciliarioSoloPideSusTarifasYNoVeModalidad() {
        Pagina<PagoPropio> pagina = useCase.consultarPropio(DOMICILIARIO, RolHistorial.DOMICILIARIO, TODOS);

        assertEquals(DOMICILIARIO, query().domiciliarioId());
        assertEquals(TipoPago.TARIFA_DOMICILIO, query().tipoFijo());
        assertNull(pagina.content().get(0).modalidad());
    }

    @Test
    void lasFechasSonDiasCompletosEnHoraDeColombia() {
        useCase.consultarPropio(CLIENTE, RolHistorial.CLIENTE, new Filtros(MedioPago.EFECTIVO, null, null,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 13), true, true, 2, 10));

        HistorialPagosQuery query = query();
        assertEquals(Instant.parse("2026-11-01T05:00:00Z"), query.desde());
        assertEquals(Instant.parse("2026-11-14T05:00:00Z"), query.hastaExclusivo());
        assertEquals(MedioPago.EFECTIVO, query.medio());
        assertEquals(20, query.offset());
        assertEquals(10, query.limit());
    }

    @Test
    void rechazaFiltrosInvalidos() {
        DomainException rango = assertThrows(DomainException.class, () -> useCase.consultarGeneral(new Filtros(
                null, null, null, LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 1), false, false, 0, 20)));
        assertEquals(Map.of("desde", "La fecha inicial no puede ser posterior a la fecha final."),
                rango.details().get("fields"));
        assertThrows(DomainException.class, () -> useCase.consultarGeneral(
                new Filtros(null, null, null, null, null, false, false, 0, 101)));
    }

    @Test
    void elAdministradorVeTodosConLosNombresDeLosInvolucrados() {
        when(usuarios.consultar(any())).thenReturn(Map.of(
                CLIENTE, "Ana Pérez", VENDEDOR, "Rosa Martínez", DOMICILIARIO, "Carlos Ruiz"));

        Pagina<PagoGeneral> pagina = useCase.consultarGeneral(TODOS);

        HistorialPagosQuery query = query();
        assertNull(query.compradorId());
        assertNull(query.vendedorId());
        assertNull(query.domiciliarioId());
        PagoGeneral pedido = pagina.content().get(0);
        assertEquals(new Usuario(CLIENTE, "Ana Pérez"), pedido.comprador());
        assertEquals(new Usuario(VENDEDOR, "Rosa Martínez"), pedido.vendedor());
        assertNull(pedido.domiciliario());
        PagoGeneral cobro = pagina.content().get(1);
        assertNull(cobro.comprador());
        assertEquals(new Usuario(DOMICILIARIO, "Carlos Ruiz"), cobro.domiciliario());
    }

    private HistorialPagosQuery query() {
        ArgumentCaptor<HistorialPagosQuery> captor = ArgumentCaptor.forClass(HistorialPagosQuery.class);
        verify(pagos).listarHistorial(captor.capture());
        return captor.getValue();
    }
}
