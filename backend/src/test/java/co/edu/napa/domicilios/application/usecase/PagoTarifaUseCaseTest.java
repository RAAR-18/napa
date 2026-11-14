package co.edu.napa.domicilios.application.usecase;

import co.edu.napa.domicilios.application.ports.in.PagoTarifaPort.CuentaDomiciliario;
import co.edu.napa.domicilios.application.ports.in.PagoTarifaPort.EstadoPagoTarifa;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.pagos.application.ports.in.PagoTarifaDomicilioPort;
import co.edu.napa.pagos.application.ports.in.PagoTarifaDomicilioPort.CuentaCobro;
import co.edu.napa.pagos.application.ports.in.PagoTarifaDomicilioPort.PagoTarifa;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PagoTarifaUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-13T18:00:00Z");
    private static final UUID DOMICILIARIO = UUID.randomUUID();

    private DomicilioRepositoryPort domicilios;
    private PagoTarifaDomicilioPort pagos;
    private PagoTarifaUseCase useCase;
    private Domicilio entregado;

    @BeforeEach
    void setUp() {
        domicilios = mock(DomicilioRepositoryPort.class);
        pagos = mock(PagoTarifaDomicilioPort.class);
        useCase = new PagoTarifaUseCase(domicilios, pagos);
        entregado = Domicilio.publicar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 5_000,
                        UUID.randomUUID(), UUID.randomUUID(), NOW.minusSeconds(3_600))
                .asignar(DOMICILIARIO, "048213", NOW.minusSeconds(3_000))
                .confirmarRecogida(NOW.minusSeconds(2_400))
                .confirmarSalida(NOW.minusSeconds(2_000))
                .entregar(NOW.minusSeconds(600));
    }

    @Test
    void elVendedorRegistraElPagoPorLaTarifaPublicada() {
        when(domicilios.bloquear(entregado.id())).thenReturn(Optional.of(entregado));
        when(pagos.registrar(entregado.id(), entregado.compradorId(), entregado.vendedorId(), DOMICILIARIO, 5_000))
                .thenReturn(new PagoTarifa(UUID.randomUUID(), "REGISTRADO", 5_000, "CUENTA_BANCARIA", NOW, null));

        EstadoPagoTarifa estado = useCase.registrar(entregado.vendedorId(), entregado.id());

        assertEquals("REGISTRADO", estado.estado());
        assertEquals(5_000, estado.monto());
    }

    @Test
    void noSeRegistraAntesDeLaEntrega() {
        Domicilio enCamino = Domicilio.publicar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 5_000,
                        UUID.randomUUID(), UUID.randomUUID(), NOW)
                .asignar(DOMICILIARIO, "048213", NOW).confirmarRecogida(NOW).confirmarSalida(NOW);
        when(domicilios.bloquear(enCamino.id())).thenReturn(Optional.of(enCamino));

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.registrar(enCamino.vendedorId(), enCamino.id()));

        assertEquals("INVALID_STATE", error.code());
        assertEquals(Map.of("estadoActual", "EN_CAMINO"), error.details());
        verify(pagos, never()).registrar(any(), any(), any(), any(), anyLong());
    }

    @Test
    void otroVendedorNoEncuentraElDomicilio() {
        when(domicilios.bloquear(entregado.id())).thenReturn(Optional.of(entregado));

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.registrar(UUID.randomUUID(), entregado.id()));

        assertEquals("NOT_FOUND", error.code());
    }

    @Test
    void soloElDomiciliarioAsignadoConfirma() {
        when(domicilios.bloquear(entregado.id())).thenReturn(Optional.of(entregado));
        when(pagos.confirmar(entregado.id()))
                .thenReturn(new PagoTarifa(UUID.randomUUID(), "CONFIRMADO", 5_000, "CUENTA_BANCARIA", NOW, NOW));

        assertEquals("CONFIRMADO", useCase.confirmar(DOMICILIARIO, entregado.id()).estado());
        DomainException error = assertThrows(DomainException.class,
                () -> useCase.confirmar(UUID.randomUUID(), entregado.id()));
        assertEquals("NOT_FOUND", error.code());
    }

    @Test
    void elVendedorVeLaCuentaDelDomiciliarioConElDomicilioEntregado() {
        when(domicilios.buscar(entregado.id())).thenReturn(Optional.of(entregado));
        when(pagos.consultar(entregado.id())).thenReturn(Optional.empty());
        when(pagos.cuentaDelDomiciliario(DOMICILIARIO))
                .thenReturn(Optional.of(new CuentaCobro("Nequi", "AHORROS", "3001112233")));

        EstadoPagoTarifa estado = useCase.consultarComoVendedor(entregado.vendedorId(), entregado.id());

        assertEquals("SIN_REGISTRAR", estado.estado());
        assertEquals(5_000, estado.monto());
        assertEquals(new CuentaDomiciliario("Nequi", "AHORROS", "3001112233"), estado.cuentaDomiciliario());
    }

    @Test
    void elDomiciliarioNuncaRecibeLaCuenta() {
        when(domicilios.buscar(entregado.id())).thenReturn(Optional.of(entregado));
        when(pagos.consultar(entregado.id())).thenReturn(Optional.empty());

        EstadoPagoTarifa estado = useCase.consultarComoDomiciliario(DOMICILIARIO, entregado.id());

        assertNull(estado.cuentaDomiciliario());
        verify(pagos, never()).cuentaDelDomiciliario(any());
    }
}
