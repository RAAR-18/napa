package co.edu.napa.pagos.application.usecase;

import co.edu.napa.pagos.application.ports.in.PagoTarifaDomicilioPort.CuentaCobro;
import co.edu.napa.pagos.application.ports.in.PagoTarifaDomicilioPort.PagoTarifa;
import co.edu.napa.pagos.application.ports.out.ConfiguracionPagoRepositoryPort;
import co.edu.napa.pagos.application.ports.out.PagoRepositoryPort;
import co.edu.napa.pagos.domain.event.PagoTarifaRegistradoEvent;
import co.edu.napa.pagos.domain.model.ConfiguracionPago;
import co.edu.napa.pagos.domain.model.CuentaBancaria;
import co.edu.napa.pagos.domain.model.EstadoPago;
import co.edu.napa.pagos.domain.model.MedioPago;
import co.edu.napa.pagos.domain.model.Pago;
import co.edu.napa.pagos.domain.model.TipoCuentaBancaria;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PagoTarifaDomicilioUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-13T18:00:00Z");
    private static final UUID DOMICILIO = UUID.randomUUID();
    private static final UUID COMPRADOR = UUID.randomUUID();
    private static final UUID VENDEDOR = UUID.randomUUID();
    private static final UUID DOMICILIARIO = UUID.randomUUID();

    private PagoRepositoryPort pagos;
    private ConfiguracionPagoRepositoryPort configuraciones;
    private EventPublisherPort eventos;
    private PagoTarifaDomicilioUseCase useCase;

    @BeforeEach
    void setUp() {
        pagos = mock(PagoRepositoryPort.class);
        configuraciones = mock(ConfiguracionPagoRepositoryPort.class);
        eventos = mock(EventPublisherPort.class);
        useCase = new PagoTarifaDomicilioUseCase(pagos, configuraciones, eventos, Clock.fixed(NOW, ZoneOffset.UTC));
        when(pagos.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void registraLaTarifaPorCuentaBancariaYAvisaAlDomiciliario() {
        when(pagos.bloquearTarifaPorDomicilio(DOMICILIO)).thenReturn(Optional.empty());

        PagoTarifa pago = useCase.registrar(DOMICILIO, COMPRADOR, VENDEDOR, DOMICILIARIO, 5_000);

        assertEquals("REGISTRADO", pago.estado());
        assertEquals("CUENTA_BANCARIA", pago.medio());
        assertEquals(5_000, pago.monto());
        assertEquals(NOW, pago.registradoAt());
        ArgumentCaptor<PagoTarifaRegistradoEvent> evento = ArgumentCaptor.forClass(PagoTarifaRegistradoEvent.class);
        verify(eventos).publish(evento.capture());
        assertEquals("pagos.pago-tarifa-registrado", evento.getValue().type());
        assertEquals(Map.of("pagoId", pago.pagoId(), "domicilioId", DOMICILIO, "domiciliarioId", DOMICILIARIO,
                "monto", 5_000L), evento.getValue().payload());
    }

    @Test
    void noSeRegistraDosVeces() {
        when(pagos.bloquearTarifaPorDomicilio(DOMICILIO)).thenReturn(Optional.of(registrado()));

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.registrar(DOMICILIO, COMPRADOR, VENDEDOR, DOMICILIARIO, 5_000));

        assertEquals("CONFLICT", error.code());
        verify(pagos, never()).guardar(any());
        verify(eventos, never()).publish(any());
    }

    @Test
    void elDomiciliarioConfirmaQueRecibioLaTarifa() {
        when(pagos.bloquearTarifaPorDomicilio(DOMICILIO)).thenReturn(Optional.of(registrado()));

        PagoTarifa pago = useCase.confirmar(DOMICILIO);

        assertEquals("CONFIRMADO", pago.estado());
        assertEquals(NOW, pago.confirmadoAt());
        verify(pagos).actualizar(any());
    }

    @Test
    void noSeConfirmaSinRegistro() {
        when(pagos.bloquearTarifaPorDomicilio(DOMICILIO)).thenReturn(Optional.empty());

        DomainException error = assertThrows(DomainException.class, () -> useCase.confirmar(DOMICILIO));

        assertEquals("FEE_NOT_REGISTERED", error.code());
    }

    @Test
    void noSeConfirmaDosVeces() {
        when(pagos.bloquearTarifaPorDomicilio(DOMICILIO))
                .thenReturn(Optional.of(registrado().confirmarTarifa(NOW.minusSeconds(60))));

        DomainException error = assertThrows(DomainException.class, () -> useCase.confirmar(DOMICILIO));

        assertEquals("ALREADY_CONFIRMED", error.code());
        verify(pagos, never()).actualizar(any());
    }

    @Test
    void entregaLaCuentaDelDomiciliarioSinEnmascarar() {
        when(configuraciones.findByVendedorId(DOMICILIARIO)).thenReturn(Optional.of(new ConfiguracionPago(
                DOMICILIARIO, false, new CuentaBancaria("Nequi", TipoCuentaBancaria.AHORROS, "3001112233"),
                NOW, NOW)));

        assertEquals(Optional.of(new CuentaCobro("Nequi", "AHORROS", "3001112233")),
                useCase.cuentaDelDomiciliario(DOMICILIARIO));
        assertEquals(Optional.empty(), useCase.cuentaDelDomiciliario(UUID.randomUUID()));
    }

    @Test
    void elDominioSoloAdmiteCuentaBancariaParaLaTarifa() {
        assertThrows(DomainException.class, () -> new Pago(UUID.randomUUID(),
                co.edu.napa.pagos.domain.model.TipoPago.TARIFA_DOMICILIO, 5_000, MedioPago.EFECTIVO,
                EstadoPago.REGISTRADO, null, null, DOMICILIO, COMPRADOR, VENDEDOR, DOMICILIARIO, NOW, NOW));
    }

    private static Pago registrado() {
        return Pago.tarifaRegistrada(DOMICILIO, COMPRADOR, VENDEDOR, DOMICILIARIO, 5_000, NOW.minusSeconds(600));
    }
}
